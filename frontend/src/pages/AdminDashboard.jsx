import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { letterRequestAPI, timesheetAPI, vacationAPI } from '../api';
import { format } from 'date-fns';
import { LoadingIndicator } from '../components/Hourglass';
import '../styles.css';

function formatDate(value) {
  return value ? format(new Date(value), 'MMM dd, yyyy') : '-';
}

function formatLetterType(value) {
  return {
    EMPLOYMENT_VERIFICATION: 'Employment Verification Letter',
    TRAVEL: 'Travel Letter',
    VACATION: 'Vacation Letter',
  }[value] || value;
}

export default function AdminDashboard() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [pendingTimesheets, setPendingTimesheets] = useState([]);
  const [pendingVacations, setPendingVacations] = useState([]);
  const [pendingLetters, setPendingLetters] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [rejectingTask, setRejectingTask] = useState(null);
  const [rejectReason, setRejectReason] = useState('');
  const isReviewer = ['PROJECT_MANAGER', 'ADMIN', 'SUPER_ADMIN'].includes(user?.role);

  useEffect(() => {
    if (!isReviewer) return;
    loadPendingItems();
  }, [isReviewer]);

  const loadPendingItems = async () => {
    try {
      const [timesheetRes, vacationRes, letterRes] = await Promise.allSettled([
        timesheetAPI.getPendingProjectSubmissions(),
        vacationAPI.getPendingRequests(),
        letterRequestAPI.getPendingRequests(),
      ]);
      setPendingTimesheets(timesheetRes.status === 'fulfilled' ? timesheetRes.value.data || [] : []);
      setPendingVacations(vacationRes.status === 'fulfilled' ? vacationRes.value.data || [] : []);
      setPendingLetters(letterRes.status === 'fulfilled' ? letterRes.value.data || [] : []);
      setError(letterRes.status === 'rejected' ? 'Letter requests could not be loaded. Other pending tasks are still shown.' : '');
    } catch (err) {
      setError('Failed to load pending tasks');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const pendingTasks = useMemo(() => [
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
    })),
    ...pendingVacations.map((task) => ({
      id: `vacation-${task.id}`,
      entityId: task.id,
      type: 'Vacation Request',
      employee: task.userName,
      period: `${formatDate(task.startDate)} to ${formatDate(task.endDate)}`,
      detail: `${Number(task.hours || 0) / 8} days, ${task.vacationType}`,
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
  ].sort((a, b) => new Date(b.submittedAt || 0) - new Date(a.submittedAt || 0)), [pendingTimesheets, pendingVacations, pendingLetters]);

  const approveTask = async (task) => {
    try {
      if (task.kind === 'timesheet') {
        await timesheetAPI.approveProjectSubmission(task.entityId);
      } else if (task.kind === 'vacation') {
        await vacationAPI.approveVacation(task.entityId);
      } else {
        await letterRequestAPI.approveRequest(task.entityId);
      }
      await loadPendingItems();
    } catch (err) {
      setError(`Failed to approve ${task.type.toLowerCase()}`);
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
      setError(`Failed to reject ${rejectingTask.type.toLowerCase()}`);
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
          <h1>Admin Dashboard</h1>
          <p className="page-subtitle">Approvals, changes, and operational work that needs attention.</p>
        </div>
        <div className="admin-stats">
          <div>
            <span>Pending Tasks</span>
            <strong>{pendingTasks.length}</strong>
          </div>
          <div>
            <span>Timesheets</span>
            <strong>{pendingTimesheets.length}</strong>
          </div>
          <div>
            <span>Vacation</span>
            <strong>{pendingVacations.length}</strong>
          </div>
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}

      <section className="admin-panel">
        <div className="panel-heading">
          <div>
            <h2>Pending Tasks</h2>
            <p>{pendingTasks.length === 0 ? 'Everything is clear.' : 'Review each item and take action.'}</p>
          </div>
        </div>

        {pendingTasks.length === 0 ? (
          <div className="empty-state">
            <p>No pending tasks.</p>
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
                    {task.kind === 'timesheet' && <span>{task.project}</span>}
                    <span>{task.period}</span>
                    <span>{task.detail}</span>
                    <span>Submitted {formatDate(task.submittedAt)}</span>
                    <span className={`status-badge status-${task.status.toLowerCase()}`}>{task.status.replace('_', ' ')}</span>
                  </div>
                  {task.timeDetail && <p className="task-letter-preview">{task.timeDetail}</p>}
                </div>
                <div className="task-actions">
                  {task.kind === 'timesheet' ? (
                    <button className="button button-small button-secondary" onClick={() => navigate(`/timesheet/${task.timesheetId}?projectId=${task.projectId}`)}>
                      Review
                    </button>
                  ) : task.kind === 'letter' ? (
                    <button className="button button-small button-secondary" onClick={() => navigate(`/admin/letter-request/${task.entityId}`)} type="button">
                      Review
                    </button>
                  ) : null}
                  {task.kind !== 'letter' && (
                    <>
                      <button className="button button-small button-success" onClick={() => approveTask(task)}>
                        Approve
                      </button>
                      <button className="button button-small button-danger" onClick={() => rejectTask(task)}>
                        Reject
                      </button>
                    </>
                  )}
                </div>
              </article>
            ))}
          </div>
        )}
      </section>

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
