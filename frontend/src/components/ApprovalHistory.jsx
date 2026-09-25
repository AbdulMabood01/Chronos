import React, { useEffect, useState } from 'react';
import { timesheetAPI } from '../api';
import './WorkflowFeatures.css';
const labels = { TIMESHEET_SUBMITTED: 'Submitted for approval', TIMESHEET_APPROVED: 'Approved', TIMESHEET_REJECTED: 'Rejected', TIMESHEET_REOPENED: 'Reopened', PROJECT_MANAGER_CHANGED: 'Approval responsibility transferred' };
export default function ApprovalHistory({ timesheetId, projectId, revision }) {
  const [open, setOpen] = useState(false);
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    let current = true;
    setLoading(true); setError('');
    Promise.resolve().then(() => timesheetAPI.getApprovalHistory(timesheetId, projectId))
      .then(response => { if (current) { setEvents(response.data || []); setLoaded(true); } })
      .catch(() => { if (current) setError('Could not load approval history.'); })
      .finally(() => { if (current) setLoading(false); });
    return () => { current = false; };
  }, [timesheetId, projectId, revision, refresh]);
  const disabled = !error && loaded && events.length === 0;
  return <>
    {!open && <button type="button" className="button button-secondary" aria-expanded="false" disabled={loading || disabled} onClick={() => setOpen(true)}>View approval history</button>}
    {open && <section className="workflow-panel" aria-label="Approval history">
      <div className="history-heading">
        <h3 className="history-title">Approval history</h3>
        <button type="button" className="button button-secondary history-close" aria-expanded="true" onClick={() => setOpen(false)}>Close approval history</button>
      </div>
      {loading ? <p role="status">Loading history...</p> : error ? <p role="alert">{error}</p> : events.length === 0 ? <p>No recorded approval events yet.</p> : <ol className="approval-timeline">
        {events.map(event => <li key={event.id}><strong>{labels[event.action] || event.action}</strong><p>{event.userName || 'System'}{event.createdAt && <> <span aria-hidden="true"> / </span><time dateTime={event.createdAt}>{new Date(event.createdAt).toLocaleString()}</time></>}</p>
          {['TIMESHEET_REJECTED', 'TIMESHEET_REOPENED'].includes(event.action) && event.details?.message && <p className="history-reason">{event.details.message}</p>}
        </li>)}
      </ol>}
      <button type="button" className="button button-secondary" disabled={loading} onClick={() => setRefresh(n => n + 1)}>Refresh history</button>
    </section>}
  </>;
}
