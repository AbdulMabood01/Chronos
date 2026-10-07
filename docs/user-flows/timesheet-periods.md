# Timesheet approval periods

The monthly calendar is the workspace for entering hours. Approval is tracked separately for each project and employee, using the project's daily, weekly or monthly frequency.

- Past draft days remain editable within an active assignment's date range. Passing the submission deadline does not close a draft.
- Daily periods cover one day. Weekly periods run Monday through Sunday; a scheduled frequency change can split a period. Monthly periods cover the calendar month.
- Weekly and daily approval use visible period cards showing dates, hours and status. Clicking a card or calendar date selects the period; draft entries in that period can be edited.
- Users submit the selected day, week or month. For daily and weekly approval, **Include in batch** checkboxes let users choose ready periods to submit together. Unselected drafts remain drafts. Each period still receives its own approval.
- After submission the screen stays open, shows **Awaiting Approval**, and retains every period card. **Go to Next Week/Day** selects the next period, including across a month boundary. Users can also select older drafts to enter missed hours.
- Submitted periods are read-only while awaiting review. Submitting one period does not freeze other drafts in the calendar. Repeated or concurrent submissions cannot create duplicate submissions.
- Rejected periods reopen for corrections. Approved periods require the existing Project Admin opening process before changes are permitted.
- A period is late when submitted after the end of its final day in the employee's timezone. Every positive-hour entry in that late submission displays a red **Late** badge during review. The late flag survives rejection, resubmission and approval.
- Weekly approvals include entries on both sides of a month boundary. Owners can open the other month's days while keeping the same week selected. For reviewers, entries outside the displayed month appear beneath the calendar with hours, notes, sessions and their late mark.
- Batch submission is atomic: if any selected period cannot be submitted, none of the batch is submitted.

Browser regression coverage: `frontend/e2e/timesheet-period-navigation.spec.cjs`, `frontend/e2e/timesheet-period-submission.spec.cjs`, and `frontend/e2e/timesheet-workflows.spec.cjs`.
