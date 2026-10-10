# Timesheet approval periods

The monthly calendar is the workspace for entering hours. Approval is tracked separately for each project and employee, using the project's daily, weekly or monthly frequency.

- Past draft days remain editable within an active assignment's date range. Passing the submission deadline does not close a draft.
- Daily periods cover one day. Weekly periods run Monday through Sunday; a scheduled frequency change can split a period. Monthly periods cover the calendar month.
- Click an editable hours cell to select its day, week or month and enter hours directly. Other eligible draft cells stay editable without first choosing a period. Edits save when the cell loses focus, with saving, saved and error feedback.
- Daily approval uses the calendar alone for selection. Weekly approval keeps bold week cards directly above the calendar; the selected week also has a strong border and background throughout its calendar cells.
- The project selector and readable monthly hours, remaining hours, gross pay, approver and hourly rate sit above the calendar. Month navigation is beside the calendar. Export, refresh and approval history are grouped under **More actions**.
- The submission panel is below the calendar with the selected dates, status, hours and a dated submit button. A confirmation preview lists each selected period and its hours before submission. Submitted hours cannot be changed until review.
- For daily and weekly approval, **Select multiple days/weeks** reveals batch checkboxes on calendar days or week cards. Users choose ready periods within the displayed calendar month and preview them together. Unselected drafts remain drafts. Each period still receives its own approval.
- After submission the screen stays open and shows **Awaiting Approval**. **Go to Next Week/Day** selects the next period, including across a month boundary. Users can also click older draft cells to enter missed hours; weekly cards remain available.
- Submitted periods are read-only while awaiting review. Submitting one period does not freeze other drafts in the calendar. Repeated or concurrent submissions cannot create duplicate submissions.
- Rejected periods reopen for corrections without an editing deadline. Approved periods require another Project Admin to approve an opening. Requests are available after the approval period ends and within 30 days of its end. An approved opening has no seven-day expiry and remains active until resubmission. The first correction changes the period back to Draft; previous approval and opening events remain in its history. Resubmission locks the period again.
- The legacy monthly workflow also has no seven-day cutoff after month-end. Future months and periods remain read-only. Assignment dates, project permissions, approved vacation and planned-hour limits still apply.
- A period is late when submitted after the end of its final day in the employee's timezone. Every positive-hour entry in that late submission displays a red **Late** badge during review. The late flag survives rejection, resubmission and approval.
- Weekly approvals include entries on both sides of a month boundary. Owners can open the other month's days while keeping the same week selected. For reviewers, entries outside the displayed month appear beneath the calendar with hours, notes, sessions and their late mark.
- Batch submission is atomic: if any selected period cannot be submitted, none of the batch is submitted.

Browser regression coverage: `frontend/e2e/timesheet-calendar-editing.spec.cjs`, `frontend/e2e/timesheet-period-navigation.spec.cjs`, `frontend/e2e/timesheet-period-submission.spec.cjs`, `frontend/e2e/timesheet-period-matrix.spec.cjs`, and `frontend/e2e/timesheet-workflows.spec.cjs`.
