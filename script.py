import csv
import os
import argparse
import hashlib
import json
from pathlib import Path
from datetime import date
from decimal import Decimal

import requests


BASE_URL = os.getenv("OPENPROJECT_BASE_URL", "https://openproject.maxwellnetwork.org").rstrip("/")
TOKEN = os.getenv("OPENPROJECT_TOKEN")
PROJECT_ID = os.getenv("OPENPROJECT_PROJECT_ID", "maxwell-timekeeping")
CSV_PATH = os.getenv("OPENPROJECT_CSV_PATH", "OPENPROJECT_TASKS_IMPORT.csv")

headers = {
    "Content-Type": "application/json",
}


def build_payload(row):
    payload = {
        "subject": row["subject"],
    }

    if row.get("description"):
        payload["description"] = {
            "format": "markdown",
            "raw": row["description"],
        }

    if row.get("start_date"):
        payload["startDate"] = row["start_date"]
    if row.get("due_date"):
        payload["dueDate"] = row["due_date"]

    if row.get("estimated_hours"):
        payload["estimatedTime"] = f"PT{row['estimated_hours']}H"

    links = {}
    if row.get("type_id"):
        links["type"] = {"href": f"/api/v3/types/{row['type_id']}"}
    if row.get("status_id"):
        links["status"] = {"href": f"/api/v3/statuses/{row['status_id']}"}
    if row.get("priority_id"):
        links["priority"] = {"href": f"/api/v3/priorities/{row['priority_id']}"}
    if row.get("assignee_id"):
        links["assignee"] = {"href": f"/api/v3/users/{row['assignee_id']}"}

    if links:
        payload["_links"] = links

    return payload


def read_rows(path):
    with open(path, encoding="utf-8-sig", newline="") as source:
        rows = [row for row in csv.DictReader(source) if row.get("subject", "").strip()]
    for row in rows:
        row["subject"] = row["subject"].strip()
        for field in ("start_date", "due_date"):
            if row.get(field):
                date.fromisoformat(row[field])
        if row.get("start_date") and row.get("due_date") and row["start_date"] > row["due_date"]:
            raise ValueError("Start date is after due date: " + row["subject"])
        if row.get("estimated_hours"):
            hours = Decimal(row["estimated_hours"])
            if not hours.is_finite() or hours < 0:
                raise ValueError("Invalid estimated hours: " + row["subject"])
    return rows


def save_state(path, state):
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(state, indent=2), encoding="utf-8")
    temporary.replace(path)


def run_import(rows, state_path, dry_run=False, post=None):
    post = post or requests.post
    state_path = Path(state_path)
    state = json.loads(state_path.read_text(encoding="utf-8")) if state_path.exists() else {}
    created = skipped = failed = 0
    for row in rows:
        payload = build_payload(row)
        key = hashlib.sha256(json.dumps([BASE_URL, PROJECT_ID, row["subject"]]).encode()).hexdigest()
        previous = state.get(key, {})
        if previous.get("status") == "created":
            skipped += 1
            print("Already imported: " + row["subject"])
            continue
        if dry_run:
            print(json.dumps(payload))
            continue
        if previous.get("status") == "pending":
            failed += 1
            print("Uncertain previous result; reconcile this checkpoint with OpenProject before retrying: " + row["subject"])
            continue
        # Persist intent BEFORE the request. A timeout or crash must not cause a duplicate on rerun.
        state[key] = {"subject": row["subject"], "status": "pending"}
        save_state(state_path, state)
        try:
            response = post(f"{BASE_URL}/api/v3/projects/{PROJECT_ID}/work_packages",
                            headers=headers, auth=("apikey", TOKEN), json=payload, timeout=(10, 60))
            if response.status_code == 201:
                state[key] = {"subject": row["subject"], "status": "created", "id": response.json().get("id")}
                created += 1
                print("Created task: " + row["subject"])
            else:
                failed += 1
                # Server errors can occur after a commit, so leave them pending.
                if 400 <= response.status_code < 500:
                    state[key]["status"] = "failed"
                print(f"Failed ({response.status_code}): {row['subject']}")
        except (requests.RequestException, ValueError) as error:
            failed += 1
            print(f"Uncertain result for {row['subject']}: {type(error).__name__}")
        save_state(state_path, state)
    print(f"Created: {created}; skipped: {skipped}; failed or uncertain: {failed}")
    return 1 if failed else 0


def main(argv=None):
    parser = argparse.ArgumentParser(description="Import tasks with resumable checkpoints.")
    parser.add_argument("--csv", default=CSV_PATH)
    parser.add_argument("--state", help="Checkpoint file; keep it for safe reruns")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args(argv)
    rows = read_rows(args.csv)
    state_path = Path(args.state or str(Path(args.csv).with_suffix(".import-state.json")))
    if args.dry_run:
        return run_import(rows, state_path, dry_run=True)
    if not TOKEN:
        raise ValueError("OPENPROJECT_TOKEN environment variable is required.")
    lock_path = state_path.with_suffix(state_path.suffix + ".lock")
    try:
        lock = lock_path.open("x")
    except FileExistsError:
        raise ValueError("An import lock exists; ensure no import is running before removing it")
    try:
        with lock:
            return run_import(rows, state_path)
    finally:
        lock_path.unlink()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError) as error:
        print(f"Import failed: {error}")
        raise SystemExit(1)
