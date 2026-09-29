import ScreenTitle from '../components/ScreenTitle';
import { formatDate } from '../utils/dates';
import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { expenseAPI, letterRequestAPI, timesheetAPI, vacationAPI } from '../api';
import { LoadingIndicator } from '../components/Hourglass';
import { downloadReceipt } from './Expenses';
import './Expenses.css';
import './AdminDashboard.css';
import '../styles.css';


function formatLetterType(value) {
  return {
    EMPLOYMENT_VERIFICATION: 'Employment Verification Letter',
    TRAVEL: 'Travel Letter',
    VACATION: 'Vacation Letter',
  }[value] || value;
}

function apiErrorMessage(error, fallback) {
  return error?.userMessage || error?.response?.data?.message || error?.message || fallback;
}

const money = value => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(Number(value || 0));
const expenseLabel = value => String(value || '').replaceAll('_', ' ').toLowerCase().replace(/^./, c => c.toUpperCase());
const needsLeaveClassification = type => !['VACATION', 'SICK', 'BEREAVEMENT', 'UNPAID_LEAVE'].includes(type);
const accountingOptions = [['PAID_NO_QUOTA', 'Paid, no annual balance'], ['UNPAID', 'Unpaid, no annual balance'], ['VACATION', 'Use vacation balance'], ['SICK', 'Use sick balance'], ['BEREAVEMENT', 'Use bereavement balance']];

export default function AdminDashboard() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [pendingTimesheets, setPendingTimesheets] = useState([]);
  const [pendingVacations, setPendingVacations] = useState([]);
  const [pendingLetters, setPendingLetters] = useState([]);
  const [pendingExpenses, setPendingExpenses] = useState([]);
  const [pendingOpeningRequests, setPendingOpeningRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [rejectingTask, setRejectingTask] = useState(null);
  const [rejectReason, setRejectReason] = useState('');
  const [reviewingExpense, setReviewingExpense] = useState(null);
  const [expenseComment, setExpenseComment] = useState('');
  const [expenseBusy, setExpenseBusy] = useState(false);
  const [reviewingOpening, setReviewingOpening] = useState(null);
  const [openingComment, setOpeningComment] = useState('');
  const [openingBusy, setOpeningBusy] = useState(false);
  const [reviewingLeave, setReviewingLeave] = useState(null);
  const [specialAccounting, setSpecialAccounting] = useState('');
  const isReviewer = user?.canReviewProjects || ['PROJECT_ADMIN', 'ADMIN'].includes(user?.role);
  const canReviewLetters = user?.role === 'ADMIN';

  useEffect(() => {
    if (!isReviewer) return;
    loadPendingItems();
  }, [isReviewer, canReviewLetters]);

  const loadPendingItems = async () => {
    try {
      const requests = [
        user?.role !== 'ADMIN' ? timesheetAPI.getPendingProjectSubmissions() : Promise.resolve({ data: [] }),
        user?.role === 'ADMIN' ? vacationAPI.getPendingRequests() : Promise.resolve({ data: [] }),
        canReviewLetters ? letterRequestAPI.getPendingRequests() : Promise.resolve({ data: [] }),
        expenseAPI.pending(),
        user?.role === 'PROJECT_ADMIN' ? timesheetAPI.getPendingOpeningRequests() : Promise.resolve({ data: [] }),
      ];
      const [timesheetRes, vacationRes, letterRes, expenseRes, openingRes] = await Promise.allSettled(requests);
      if (timesheetRes.status === 'fulfilled') setPendingTimesheets(timesheetRes.value.data || []);
      if (vacationRes.status === 'fulfilled') setPendingVacations(vacationRes.value.data || []);
      if (letterRes.status === 'fulfilled') setPendingLetters(letterRes.value.data || []);
      if (expenseRes.status === 'fulfilled') setPendingExpenses(expenseRes.value.data || []);
      if (openingRes.status === 'fulfilled') setPendingOpeningRequests(openingRes.value.data || []);
      const failed = [[timesheetRes, 'timesheets'], [vacationRes, 'vacation requests'], [letterRes, 'letters'], [expenseRes, 'expenses'], [openingRes, 'timesheet openings']]
        .filter(([result]) => result?.status === 'rejected').map(([, label]) => label);
      setError(failed.length ? 'Could not refresh ' + failed.join(', ') + '. Previously loaded items may be out of date. Please retry.' : '');
    } catch (err) {
      setError('Failed to load pending tasks');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const pendingTasks = useMemo(() => [
    ...pendingOpeningRequests.map((task) => ({
      id: `opening-${task.id}`,
      entityId: task.id,
      timesheetId: task.timesheetId,
      projectId: task.projectId,
      type: 'Timesheet Opening',
      employee: task.userName,
      project: task.projectCode,
      period: `${task.month}/${task.year}`,
      detail: 'Opening requested',
      timeDetail: task.employeeComment,
      submittedAt: task.createdAt,
      status: task.status,
      kind: 'opening',
      opening: task,
    })),
    ...pendingTimesheets.map((task) => ({
      id: `timesheet-${task.id}`,
      entityId: task.id,
      timesheetId: task.timesheetId,
      projectId: task.projectId,
      type: task.status === 'CHANGE_REQUESTED' ? 'Timesheet Change' : 'Timesheet Approval',
      employee: task.userName,
      designation: task.userJobTitle || '-',
      project: `${task.projectCode}${task.projectName ? ` - ${task.projectName}` : ''}`,
      period: `${task.month}/${task.year}`,
      detail: `${Number(task.totalHours || 0).toFixed(2)} hours`,
      timeDetail: (task.timeEntries || [])
        .flatMap((entry) => (entry.sessions || []).map((session) => `${entry.entryDate}: ${session.loginTime}-${session.logoutTime}`))
        .join(', '),
      submittedAt: task.submittedAt,
      status: task.status,
      kind: 'timesheet',
      isOwn: task.userId === user?.id,
    })),
    ...pendingVacations.map((task) => ({
      id: `vacation-${task.id}`,
      entityId: task.id,
      type: 'Vacation Request',
      employee: task.userName,
      period: `${formatDate(task.startDate)} to ${formatDate(task.endDate)}`,
      detail: `${Number(task.hours || 0) / 8} days, ${task.vacationType}`,
      vacationType: task.vacationType,
      specialReason: task.specialReason,
      notes: task.notes,
      submittedAt: task.submittedAt,
      status: task.status,
      kind: 'vacation',
    })),
    ...pendingLetters.map((task) => ({
      id: `letter-${task.id}`,
      entityId: task.id,
      type: formatLetterType(task.requestType),
      employee: task.userName,
      period: task.requestedJobTitle || 'Letter request',
      detail: task.employmentStartDate ? `Started ${formatDate(task.employmentStartDate)}` : 'Pending review',
      submittedAt: task.submittedAt,
      status: task.status,
      kind: 'letter',
    })),
    ...pendingExpenses.map((task) => ({
      id: `expense-${task.id}`,
      entityId: task.id,
      type: 'Expense Approval',
      employee: task.employee_name,
      designation: task.project_code,
      period: task.expense_date ? formatDate(task.expense_date) : 'Expense date unavailable',
      detail: `${expenseLabel(task.category)} · ${money(task.amount)}`,
      submittedAt: task.submitted_at,
      status: task.status || 'PENDING_APPROVAL',
      kind: 'expense',
      expense: task,
    })),
  ].sort((a, b) => new Date(b.submittedAt || 0) - new Date(a.submittedAt || 0)), [pendingOpeningRequests, pendingTimesheets, pendingVacations, pendingLetters, pendingExpenses, user?.id]);

  const decideOpening = async approve => {
    if (!reviewingOpening || openingBusy) return;
    if (!approve && !openingComment.trim()) { setError('Explain why the opening request is declined.'); return; }
    setOpeningBusy(true);
    setError('');
    try {
      await timesheetAPI.decideOpeningRequest(reviewingOpening.id, approve, openingComment.trim());
      setReviewingOpening(null);
      setOpeningComment('');
      await loadPendingItems();
    } catch (err) { setError(apiErrorMessage(err, 'Failed to decide opening request')); }
    finally { setOpeningBusy(false); }
  };

  const decideExpense = async status => {
    if (!reviewingExpense || expenseBusy) return;
    setExpenseBusy(true);
    setError('');
    try {
      await expenseAPI.decide(reviewingExpense.id, status, expenseComment.trim());
      setReviewingExpense(null);
      setExpenseComment('');
      await loadPendingItems();
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to review expense'));
    } finally {
      setExpenseBusy(false);
    }
  };

  const approveTask = async (task, accountingType = null) => {
    try {
      if (task.kind === 'timesheet') {
        await timesheetAPI.approveProjectSubmission(task.entityId);
      } else if (task.kind === 'vacation') {
        await vacationAPI.approveVacation(task.entityId, accountingType);
      } else {
        await letterRequestAPI.approveRequest(task.entityId);
      }
      await loadPendingItems();
      setReviewingLeave(null);
      setSpecialAccounting('');
    } catch (err) {
      setError(apiErrorMessage(err, `Failed to approve ${task.type.toLowerCase()}`));
    }
  };

  const rejectTask = async (task) => {
    setRejectingTask(task);
    setRejectReason('');
  };

  const confirmRejectTask = async () => {
    const reason = rejectReason.trim();
    if (!reason || !rejectingTask) {
      setError('Rejection reason is required');
      return;
    }

    try {
      if (rejectingTask.kind === 'timesheet') {
        await timesheetAPI.rejectProjectSubmission(rejectingTask.entityId, reason);
      } else if (rejectingTask.kind === 'vacation') {
        await vacationAPI.rejectVacation(rejectingTask.entityId, reason);
      } else {
        await letterRequestAPI.rejectRequest(rejectingTask.entityId, reason);
      }
      setRejectingTask(null);
      setRejectReason('');
      await loadPendingItems();
    } catch (err) {
      setError(apiErrorMessage(err, `Failed to reject ${rejectingTask.type.toLowerCase()}`));
    }
  };

  if (!isReviewer) {
    return (
      <div className="page-container">
        <div className="error-message">You do not have permission to access this page.</div>
      </div>
    );
  }

  if (loading) {
    return <div className="page-container"><div className="loading-panel"><LoadingIndicator label="Loading pending tasks..." /></div></div>;
  }

  return (
    <div className="page-container admin-page">
      <div className="admin-hero">
        <div>
          <ScreenTitle title="Approvals" icon="check" eyebrow="REVIEW & APPROVE" />
          <p className="page-subtitle">Approvals, changes, and operational work that needs attention.</p>
        </div>
        <div className="admin-stats">
          <div>
            <span>Pending Tasks</span>
            <strong>{pendingTasks.length}</strong>
          </div>
          <div>
            <span>Timesheets</span>
            <strong>{pendingTimesheets.length + pendingOpeningRequests.length}</strong>
          </div>
          <div>
            <span>Vacation</span>
            <strong>{pendingVacations.length}</strong>
          </div>
          <div>
            <span>Expenses</span>
            <strong>{pendingExpenses.length}</strong>
          </div>
        </div>
      </div>

      {error && <div className="error-message">{error} <button type="button" onClick={loadPendingItems}>Retry</button></div>}


      <section className="admin-panel admin-approval-section admin-pending-section">
        <div className="panel-heading">
          <div>
            <h2>Pending Tasks</h2>
            <p>{error ? 'Some queues could not be refreshed.' : pendingTasks.length === 0 ? 'Everything is clear.' : 'Review each item and take action.'}</p>
          </div>
        </div>

        {pendingTasks.length === 0 ? (
          <div className="empty-state">
            <p>{error ? 'Pending tasks could not be confirmed.' : 'No pending tasks.'}</p>
          </div>
        ) : (
          <div className="task-list">
            {pendingTasks.map((task) => (
              <article className="task-row" key={task.id}>
                <div className="task-main">
                  <span className="task-type">{task.type}</span>
                  <h3>{task.employee}</h3>
                  <p>{task.designation}</p>
                  <div className="request-meta">
                    {['timesheet', 'opening'].includes(task.kind) && <span>{task.project}</span>}
                    <span>{task.period}</span>
                    <span>{task.detail}</span>
                    <span>Submitted {formatDate(task.submittedAt)}</span>
                    <span className={`status-badge status-${task.status.toLowerCase()}`}>{task.status.replace('_', ' ')}</span>
                  </div>
                  {task.timeDetail && <p className="task-letter-preview">{task.timeDetail}</p>}
                </div>
                <div className="task-actions">
                  {task.kind === 'opening' ? (
                    <button className="button button-small button-secondary" onClick={() => { setReviewingOpening(task.opening); setOpeningComment(''); setError(''); }} type="button">Review</button>
                  ) : task.kind === 'timesheet' ? (
                    <button className="button button-small button-secondary" onClick={() => navigate(`/timesheet/${task.timesheetId}?projectId=${task.projectId}`)}>
                      Review
                    </button>
                  ) : task.kind === 'letter' ? (
                    <button className="button button-small button-secondary" onClick={() => navigate(`/admin/letter-request/${task.entityId}`)} type="button">
                      Review
                    </button>
                  ) : task.kind === 'expense' ? (
                    <button className="button button-small button-secondary" onClick={() => { setReviewingExpense(task.expense); setExpenseComment(''); }} type="button">
                      Review
                    </button>
                  ) : null}
                  {task.kind !== 'letter' && task.kind !== 'expense' && task.kind !== 'opening' && (
                    <>
                      {task.kind === 'vacation' && needsLeaveClassification(task.vacationType)
                        ? <button className="button button-small button-secondary" onClick={() => { setReviewingLeave(task); setSpecialAccounting(''); }}>Review</button>
                        : <button className="button button-small button-success" onClick={() => approveTask(task)}>Approve</button>}
                      {!task.isOwn && <button className="button button-small button-danger" onClick={() => rejectTask(task)}>
                        Reject
                      </button>}
                    </>
                  )}
                </div>
              </article>
            ))}
          </div>
        )}
      </section>

      {reviewingOpening && <div className="modal-backdrop" role="presentation">
        <div className="modal-card expense-review-modal opening-review-modal" role="dialog" aria-modal="true" aria-labelledby="opening-review-title">
          <div className="modal-header"><h2 id="opening-review-title">Review timesheet opening</h2><button type="button" className="modal-close" aria-label="Close" onClick={() => setReviewingOpening(null)}>&times;</button></div>
          <p><strong>{reviewingOpening.userName}</strong> / {reviewingOpening.projectCode} / {reviewingOpening.month}/{reviewingOpening.year}</p>
          <p className="opening-review-reason"><span>Employee comment</span>{reviewingOpening.employeeComment}</p>
          <button type="button" className="button button-secondary button-small" onClick={() => navigate(`/timesheet/${reviewingOpening.timesheetId}?projectId=${reviewingOpening.projectId}`)}>View timesheet</button>
          {error && <p role="alert" className="error-message">{error}</p>}
          <label className="expense-review-comment">Project Admin comment<textarea value={openingComment} maxLength={500} rows={3} onChange={event => setOpeningComment(event.target.value)} placeholder="Optional for approval, required to decline" /></label>
          <div className="expense-actions"><button type="button" disabled={openingBusy} className="button button-primary" onClick={() => decideOpening(true)}>Approve for 7 days</button><button type="button" disabled={openingBusy || !openingComment.trim()} className="button button-secondary" onClick={() => decideOpening(false)}>Decline</button></div>
        </div>
      </div>}

      {reviewingExpense && <div className="modal-backdrop" role="presentation">
        <div className="modal-card expense-review-modal" role="dialog" aria-modal="true" aria-labelledby="expense-review-title">
          <div className="modal-header"><h2 id="expense-review-title">Review expense</h2><button type="button" className="modal-close" aria-label="Close" onClick={() => setReviewingExpense(null)}>×</button></div>
          <p><strong>{reviewingExpense.employee_name}</strong> · {reviewingExpense.project_code} · {expenseLabel(reviewingExpense.category)} · {money(reviewingExpense.amount)}</p>
          <p>{reviewingExpense.description}</p>
          {error && <p role="alert" className="error-message">{error}</p>}
          {reviewingExpense.receipt_name && <button type="button" className="button button-secondary button-small" onClick={() => downloadReceipt(reviewingExpense.id, reviewingExpense.receipt_name).catch(err => setError(apiErrorMessage(err, 'Unable to download receipt')))}>Download receipt</button>}
          <label className="expense-review-comment">Reviewer comments<textarea value={expenseComment} maxLength="2000" rows="3" onChange={event => setExpenseComment(event.target.value)} placeholder="Required when requesting changes or rejecting" /></label>
          <div className="expense-actions"><button type="button" disabled={expenseBusy} className="button button-primary" onClick={() => decideExpense('APPROVED')}>Approve</button><button type="button" disabled={expenseBusy || !expenseComment.trim()} className="button button-secondary" onClick={() => decideExpense('CHANGES_REQUESTED')}>Request changes</button><button type="button" disabled={expenseBusy || !expenseComment.trim()} className="button button-secondary" onClick={() => decideExpense('REJECTED')}>Reject</button></div>
        </div>
      </div>}

      {reviewingLeave && <div className="modal-backdrop" role="presentation">
        <div className="modal-card leave-review-modal" role="dialog" aria-modal="true" aria-labelledby="leave-review-title">
          <div className="modal-header"><h2 id="leave-review-title">Classify special leave</h2><button type="button" className="modal-close" aria-label="Close" onClick={() => setReviewingLeave(null)}>×</button></div>
          <p><strong>{reviewingLeave.employee}</strong> · {reviewingLeave.period}</p>
          <p>{reviewingLeave.specialReason || expenseLabel(reviewingLeave.vacationType)}</p>
          {reviewingLeave.notes && <p>{reviewingLeave.notes}</p>}
          {error && <p role="alert" className="error-message">{error}</p>}
          <label>How should this leave be counted?<select value={specialAccounting} onChange={event => setSpecialAccounting(event.target.value)}>
            <option value="">Choose a classification</option>{accountingOptions.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select></label>
          <p className="leave-review-note">Balance choices are checked again before approval. The chosen classification is saved with this request.</p>
          <div className="action-bar compact-actions"><button type="button" className="button button-primary" disabled={!specialAccounting} onClick={() => approveTask(reviewingLeave, specialAccounting)}>Approve leave</button><button type="button" className="button button-secondary" onClick={() => setReviewingLeave(null)}>Cancel</button></div>
        </div>
      </div>}

      {rejectingTask && (
        <div className="modal-backdrop" role="presentation">
          <div className="modal-card" role="dialog" aria-modal="true" aria-labelledby="reject-task-title">
            <div className="modal-header">
              <h2 id="reject-task-title">Reject {rejectingTask.type}</h2>
              <button className="modal-close" onClick={() => setRejectingTask(null)} type="button" aria-label="Close">
                x
              </button>
            </div>
            <p className="modal-subtitle">Add a clear reason for {rejectingTask.employee} before rejecting.</p>
            <textarea
              value={rejectReason}
              onChange={(event) => setRejectReason(event.target.value)}
              rows="4"
              placeholder="Reason for rejection"
              autoFocus
            />
            <div className="action-bar compact-actions">
              <button className="button button-secondary" onClick={() => setRejectingTask(null)} type="button">
                Cancel
              </button>
              <button className="button button-danger" onClick={confirmRejectTask} type="button">
                Reject
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
