import ScreenTitle from '../components/ScreenTitle';
import { ProjectHealthOverview, useProjectHealth } from '../components/ProjectHealth';
import { formatDate } from '../utils/dates';
import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { letterRequestAPI, timesheetAPI, vacationAPI } from '../api';
import { LoadingIndicator } from '../components/Hourglass';
import '../styles.css';


function formatLetterType(value) {
  return {
    EMPLOYMENT_VERIFICATION: 'Employment Verification Letter',
    TRAVEL: 'Travel Letter',
    VACATION: 'Vacation Letter',
  }[value] || value;
}

function apiErrorMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || fallback;
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
  const isReviewer = user?.canReviewProjects || ['ADMIN', 'SUPER_ADMIN'].includes(user?.role);
  const canReviewLetters = user?.role === 'SUPER_ADMIN';
  const healthState = useProjectHealth(['ADMIN', 'SUPER_ADMIN'].includes(user?.role), pendingTimesheets);

  useEffect(() => {
    if (!isReviewer) return;
    loadPendingItems();
  }, [isReviewer, canReviewLetters]);

  const loadPendingItems = async () => {
    try {
      const requests = [
        user?.role !== 'SUPER_ADMIN' ? timesheetAPI.getPendingProjectSubmissions() : Promise.resolve({ data: [] }),
        user?.role === 'SUPER_ADMIN' ? vacationAPI.getPendingRequests() : Promise.resolve({ data: [] }),
      ];
      if (canReviewLetters) {
        requests.push(letterRequestAPI.getPendingRequests());
      }
      const [timesheetRes, vacationRes, letterRes] = await Promise.allSettled(requests);
      if (timesheetRes.status === 'fulfilled') setPendingTimesheets(timesheetRes.value.data || []);
      if (vacationRes.status === 'fulfilled') setPendingVacations(vacationRes.value.data || []);
      if (canReviewLetters && letterRes?.status === 'fulfilled') setPendingLetters(letterRes.value.data || []);
      if (!canReviewLetters) setPendingLetters([]);
      const failed = [[timesheetRes, 'timesheets'], [vacationRes, 'vacation requests'], [letterRes, 'letters']]
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
  ].sort((a, b) => new Date(b.submittedAt || 0) - new Date(a.submittedAt || 0)), [pendingTimesheets, pendingVacations, pendingLetters, user?.id]);

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
          <ScreenTitle title="Admin Dashboard" icon="check" eyebrow="REVIEW & APPROVE" />
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

      {error && <div className="error-message">{error} <button type="button" onClick={loadPendingItems}>Retry</button></div>}

      {['ADMIN', 'SUPER_ADMIN'].includes(user?.role) && <ProjectHealthOverview state={healthState} onOpen={id => navigate(`/projects?projectId=${id}`)} />}

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
