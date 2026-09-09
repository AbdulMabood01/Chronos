import csv
import os

import requests


BASE_URL = os.getenv("OPENPROJECT_BASE_URL", "https://openproject.maxwellnetwork.org").rstrip("/")
TOKEN = os.getenv("OPENPROJECT_TOKEN")
PROJECT_ID = os.getenv("OPENPROJECT_PROJECT_ID", "maxwell-timekeeping")
CSV_PATH = os.getenv("OPENPROJECT_CSV_PATH", "OPENPROJECT_TASKS_IMPORT.csv")

if not TOKEN:
    raise SystemExit("OPENPROJECT_TOKEN environment variable is required.")

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


def print_error(response):
    try:
        print(response.json())
    except ValueError:
        print(response.text)


with open(CSV_PATH, mode="r", encoding="utf-8") as f:
    reader = csv.DictReader(f)
    for row in reader:
        if not row.get("subject"):
            continue

        payload = build_payload(row)
        response = requests.post(
            f"{BASE_URL}/api/v3/projects/{PROJECT_ID}/work_packages",
            headers=headers,
            auth=("apikey", TOKEN),
            json=payload,
        )

        if response.status_code == 201:
            print(f"Created task: {row['subject']}")
        else:
            print(f"Failed ({response.status_code}): {row['subject']}")
            print_error(response)
