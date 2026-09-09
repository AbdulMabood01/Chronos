import csv
import os
from datetime import date

import requests


BASE_URL = os.getenv("OPENPROJECT_BASE_URL", "https://openproject.maxwellnetwork.org").rstrip("/")
TOKEN = os.getenv("OPENPROJECT_TOKEN")
PROJECT_ID = os.getenv("OPENPROJECT_PROJECT_ID", "maxwell-timekeeping")
PROJECT_HREF = "/api/v3/projects/8"
CSV_PATH = os.getenv("OPENPROJECT_CSV_PATH", "OPENPROJECT_TASKS_IMPORT.csv")

if not TOKEN:
    raise SystemExit("OPENPROJECT_TOKEN environment variable is required.")

AUTH = ("apikey", TOKEN)
HEADERS = {"Content-Type": "application/json"}

STATUS = {
    "new": "/api/v3/statuses/15",
    "in_progress": "/api/v3/statuses/21",
    "closed": "/api/v3/statuses/26",
}

PRIORITY = {
    "2": "/api/v3/priorities/20",
    "3": "/api/v3/priorities/21",
    "4": "/api/v3/priorities/22",
}

TYPE = {
    "task": "/api/v3/types/8",
    "summary": "/api/v3/types/10",
}

PHASES = {
    "initiation": {
        "name": "01 - Initiation & Planning",
        "description": "Foundation, role model, project setup, early UI structure, and completed August work.",
        "start": "2026-08-01",
        "end": "2026-08-31",
    },
    "development": {
        "name": "02 - Development & Stabilization",
        "description": "September implementation, timesheet behavior, planned hours, routing, and active stabilization.",
        "start": "2026-09-01",
        "end": "2026-09-30",
    },
    "testing": {
        "name": "03 - Testing, QA & Hardening",
        "description": "October testing, reporting, reminders, migration hardening, documentation, and release preparation.",
        "start": "2026-10-01",
        "end": "2026-10-31",
    },
    "deployment": {
        "name": "04 - Deployment & Handover",
        "description": "November final E2E, UAT, production readiness, and handover work.",
        "start": "2026-11-01",
        "end": "2026-11-15",
    },
}


def api(method, path, **kwargs):
    url = path if path.startswith("http") else f"{BASE_URL}{path}"
    response = requests.request(method, url, auth=AUTH, headers=HEADERS, **kwargs)
    if response.status_code >= 400:
        raise RuntimeError(f"{method} {url} failed: {response.status_code} {response.text}")
    return response.json() if response.text else None


def collection(path):
    data = api("GET", path)
    return data.get("_embedded", {}).get("elements", [])


def load_work_packages():
    path = f"/api/v3/projects/{PROJECT_ID}/work_packages?pageSize=200&filters=%5B%7B%22status%22%3A%7B%22operator%22%3A%22*%22%2C%22values%22%3A%5B%5D%7D%7D%5D"
    return collection(path)


def load_versions():
    return collection(f"/api/v3/projects/{PROJECT_ID}/versions")


def ensure_version(phase):
    versions = {item["name"]: item for item in load_versions()}
    if phase["name"] in versions:
        return versions[phase["name"]]

    payload = {
        "name": phase["name"],
        "description": {"format": "markdown", "raw": phase["description"]},
        "startDate": phase["start"],
        "_links": {
            "definingProject": {"href": PROJECT_HREF},
        },
    }
    return api("POST", "/api/v3/versions", json=payload)


def get_phase_key(row):
    due = date.fromisoformat(row["due_date"])
    if due.month <= 8:
        return "initiation"
    if due.month == 9:
        return "development"
    if due.month == 10:
        return "testing"
    return "deployment"


def get_status_href(row):
    if row["status_id"] == "3":
        return STATUS["closed"]
    if row["status_id"] == "2":
        return STATUS["in_progress"]
    return STATUS["new"]


def get_remaining_time(row):
    estimated_minutes = int(float(row["estimated_hours"]) * 60)
    if row["status_id"] == "3":
        return "PT0H"
    if row["status_id"] == "2":
        return f"PT{max(1, estimated_minutes // 2)}M"
    return f"PT{estimated_minutes}M"


def get_percentage_done(row):
    if row["status_id"] == "3":
        return 100
    if row["status_id"] == "2":
        return 50
    return 0


def ensure_summary_task(phase_key, version, phase_hours):
    phase = PHASES[phase_key]
    subject = f"Phase: {phase['name']}"
    work_packages = load_work_packages()
    existing = next((item for item in work_packages if item["subject"] == subject), None)
    if existing:
        return existing

    status_href = STATUS["closed"] if phase_key == "initiation" else STATUS["in_progress"] if phase_key == "development" else STATUS["new"]
    estimated_hours = phase_hours.get(phase_key, 1)
    remaining_minutes = 0 if phase_key == "initiation" else max(1, (estimated_hours * 60) // 2) if phase_key == "development" else estimated_hours * 60
    payload = {
        "subject": subject,
        "description": {"format": "markdown", "raw": phase["description"]},
        "startDate": phase["start"],
        "dueDate": phase["end"],
        "estimatedTime": f"PT{estimated_hours}H",
        "remainingTime": f"PT{remaining_minutes}M",
        "percentageDone": 100 if phase_key == "initiation" else 50 if phase_key == "development" else 0,
        "_links": {
            "type": {"href": TYPE["summary"]},
            "status": {"href": status_href},
            "priority": {"href": PRIORITY["3"]},
            "version": {"href": version["_links"]["self"]["href"]},
        },
    }
    return api("POST", f"/api/v3/projects/{PROJECT_ID}/work_packages", json=payload)


def patch_work_package(work_package, payload):
    payload["lockVersion"] = work_package["lockVersion"]
    return api("PATCH", work_package["_links"]["self"]["href"], json=payload)


def update_task(row, work_package, version, parent):
    payload = {
        "startDate": row["start_date"],
        "dueDate": row["due_date"],
        "estimatedTime": f"PT{row['estimated_hours']}H",
        "remainingTime": get_remaining_time(row),
        "percentageDone": get_percentage_done(row),
        "_links": {
            "status": {"href": get_status_href(row)},
            "priority": {"href": PRIORITY.get(row["priority_id"], PRIORITY["3"])},
            "version": {"href": version["_links"]["self"]["href"]},
            "parent": {"href": parent["_links"]["self"]["href"]},
        },
    }
    patch_work_package(work_package, payload)


def main():
    with open(CSV_PATH, mode="r", encoding="utf-8") as f:
        rows = [row for row in csv.DictReader(f) if row.get("subject")]

    phase_hours = {}
    for row in rows:
        phase_key = get_phase_key(row)
        phase_hours[phase_key] = phase_hours.get(phase_key, 0) + int(float(row["estimated_hours"]))

    phase_versions = {key: ensure_version(value) for key, value in PHASES.items()}
    phase_parents = {key: ensure_summary_task(key, phase_versions[key], phase_hours) for key in PHASES}

    work_packages = {item["subject"]: item for item in load_work_packages()}
    updated = 0
    missing = []

    for row in rows:
        work_package = work_packages.get(row["subject"])
        if not work_package:
            missing.append(row["subject"])
            continue

        phase_key = get_phase_key(row)
        update_task(row, work_package, phase_versions[phase_key], phase_parents[phase_key])
        updated += 1

    technical_review = work_packages.get("Technical Architecture Review")
    if technical_review:
        patch_work_package(
            technical_review,
            {
                "estimatedTime": "PT4H",
                "remainingTime": "PT0H",
                "percentageDone": 100,
                "_links": {
                    "status": {"href": STATUS["closed"]},
                    "priority": {"href": PRIORITY["3"]},
                    "version": {"href": phase_versions["initiation"]["_links"]["self"]["href"]},
                    "parent": {"href": phase_parents["initiation"]["_links"]["self"]["href"]},
                },
            },
        )
        updated += 1

    print(f"Created/verified phase versions: {len(phase_versions)}")
    print(f"Created/verified summary phase tasks: {len(phase_parents)}")
    print(f"Updated work packages: {updated}")
    if missing:
        print("Missing CSV tasks:")
        for subject in missing:
            print(f"- {subject}")


if __name__ == "__main__":
    main()
