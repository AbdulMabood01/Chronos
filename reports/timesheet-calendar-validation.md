# Timesheet editing and submission update

Validated October 9, 2026.

## Behavior

- Past Draft and Rejected periods remain editable within eligible assignment dates. Clicking an hours input selects its approval period immediately; editing no longer depends on a separate period-selection button.
- Daily approval selects through the calendar. Weekly cards sit next to the calendar, with a strong selected border, background and label.
- The project and readable monthly totals, approver and rate are grouped above the calendar. Month controls are alongside the calendar. Refresh, PDF export and approval history are under More actions.
- A panel below the calendar contains the selected dates, status, hours and dated Submit/Resubmit action. A modal preview confirms the periods and hours before submission.
- Multiple submission reveals day/week checkboxes on demand. Batches cover ready periods in one displayed calendar month; each period keeps its own approval. Unselected drafts remain editable.
- Submitted periods lock while awaiting review. Approved periods require another Project Admin to approve reopening. The 30-day request window remains; the seven-day reopening expiry is removed.
- Reopened periods remain open until resubmission. The first edit returns the period to Draft, and resubmission locks it again. Approval and correction events remain in history.
- The legacy seven-day month-end editing cutoff is removed. Migration V69 preserves legacy correction openings using an explicit boolean instead of an expiry timestamp, and clears obsolete expiry timestamps.
- PDF export and project closure now check an active opening rather than an expiry timestamp. Future periods, assignment boundaries, permissions, approved leave and planned-hour limits still apply.

## Verification

| Check | Result |
| --- | --- |
| Production frontend build | Passed; existing bundle-size advisory remains |
| TimesheetDetail frontend tests | 29 passed |
| Backend workflow, correction request, boundary, domain, report and project permission tests | 71 passed |
| Real PostgreSQL approval-period tests, rollback-only isolated E2E database | 9 passed |
| Approval matrix API scenarios | 27 passed |
| Final browser submission, navigation, cross-month, batch, desktop/mobile/dark and manager review scenarios | 15 passed |

Two older browser assertions in the initial matrix run still looked for Submit Day/Submit Week. Their selectors and confirmation steps were updated; both passed in the final browser run.

Screenshots: [weekly desktop](timesheet-weekly-desktop.png), [weekly mobile](timesheet-weekly-mobile.png), [daily desktop](timesheet-daily-desktop.png), [daily mobile](timesheet-daily-mobile.png).

The application backend must restart to load the service changes and apply migration V69 through the normal migration process. Validation used the isolated E2E database, not production data.
