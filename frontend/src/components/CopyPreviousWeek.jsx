import React, { useEffect, useState } from 'react';
import { format, startOfWeek, addDays } from 'date-fns';
import { timesheetAPI } from '../api';

export default function CopyPreviousWeek({ timesheet, projectId, onCopied, disabled }) {
  const first = startOfWeek(new Date(timesheet.year, timesheet.month - 1, 1), { weekStartsOn: 1 });
  const weeks = [];
  for (let day = first; day <= new Date(timesheet.year, timesheet.month, 0); day = addDays(day, 7)) weeks.push(format(day, 'yyyy-MM-dd'));
  const [week, setWeek] = useState(() => { const current = format(startOfWeek(new Date(), { weekStartsOn: 1 }), 'yyyy-MM-dd'); return weeks.includes(current) ? current : weeks[0]; });
  const [preview, setPreview] = useState(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  useEffect(() => { setPreview(null); setMessage(''); setError(''); }, [week]);
  const run = async (apply) => {
    setBusy(true); setError(''); setMessage('');
    try {
      const result = await (apply ? timesheetAPI.copyPreviousWeek : timesheetAPI.previewPreviousWeek)(timesheet.id, projectId, week);
      if (apply) { setPreview(null); setMessage(result.data.copiedDays + (result.data.copiedDays === 1 ? ' day copied.' : ' days copied.') + ' Review the hours before submitting.'); await onCopied(result.data.copiedDays); }
      else setPreview(result.data);
    } catch (err) { setPreview(null); setError(err.response?.data?.message || 'Could not copy hours. Please try again.'); }
    finally { setBusy(false); }
  };
  return <section className="workflow-panel" aria-label="Copy last week">
    <h3>Copy last week's hours</h3>
    <p>Copy this project's hours from the preceding week. Existing entries and approved leave stay unchanged. Notes and clock times are not copied.</p>
    <div className="workflow-controls"><label>Destination week<select value={week} onChange={e => setWeek(e.target.value)} disabled={busy || disabled}>
      {weeks.map(value => <option key={value} value={value}>Week of {value}</option>)}
    </select></label><button type="button" className="button button-secondary" disabled={busy || disabled} onClick={() => run(false)}>Preview copy</button></div>
    {preview && <><ul className="copy-preview">{preview.days.map(day => <li key={day.date}><span>{day.date}</span><strong>{day.skippedReason || day.hours + ' hours'}</strong></li>)}</ul>
      <button type="button" className="button button-primary" disabled={busy || disabled || !preview.days.some(d => !d.skippedReason)} onClick={() => run(true)}>{busy ? 'Copying...' : 'Copy hours'}</button>
      <p>Assignment limits and daily hour limits are checked when saving. If a limit is exceeded, nothing is copied.</p></>}
    {message && <p role="status">{message}</p>}{error && <p role="alert" className="error-message">{error}</p>}
  </section>;
}
