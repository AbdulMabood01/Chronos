import React, { useEffect, useState } from 'react';
import { eachDayOfInterval, isWeekend, parseISO } from 'date-fns';
import { userAPI } from '../api';
import './WorkflowFeatures.css';

export function leaveBucket(type) {
  return type === 'UNPAID_LEAVE' ? null : type === 'SICK' ? 'sick' : type === 'BEREAVEMENT' ? 'bereavement' : 'vacation';
}
function datesInYear(request, year) {
  const start = request.startDate > year + '-01-01' ? request.startDate : year + '-01-01';
  const end = request.endDate < year + '-12-31' ? request.endDate : year + '-12-31';
  if (start > end) return [];
  return eachDayOfInterval({ start: parseISO(start), end: parseISO(end) }).filter(d => !isWeekend(d)).map(d => d.getTime());
}
export function calculateLeavePreview(form, requests, balances, editingId) {
  const bucket = leaveBucket(form.vacationType);
  return balances.map(({ year, balance }) => {
    const requested = datesInYear(form, year).length;
    const pending = new Set(requests.filter(r => String(r.id) !== String(editingId) && r.status === 'SUBMITTED' && leaveBucket(r.vacationType) === bucket).flatMap(r => datesInYear(r, year))).size;
    if (bucket && !balance?.[bucket]) throw new Error('Leave balance unavailable');
    const available = bucket ? Math.max(0, Number(balance[bucket].remainingDays) - pending) : 0;
    return { year, requested, pending: bucket ? pending : 0, available, remaining: Math.max(0, available - requested), unpaid: Math.max(0, requested - available), configured: balance?.configured };
  });
}
export default function LeaveBalancePreview({ userId, form, requests, editingId }) {
  const [state, setState] = useState({ rows: [], loading: false, error: '' });
  const valid = /^\d{4}-\d{2}-\d{2}$/.test(form.startDate) && /^\d{4}-\d{2}-\d{2}$/.test(form.endDate) && form.startDate <= form.endDate;
  useEffect(() => {
    let active = true;
    setState({ rows: [], loading: valid, error: '' });
    if (!valid || !userId) return;
    const first = Number(form.startDate.slice(0, 4)), last = Number(form.endDate.slice(0, 4));
    if (last - first > 10) { setState({ rows: [], loading: false, error: 'Choose a date range of ten years or less.' }); return; }
    const years = Array.from({ length: last - first + 1 }, (_, i) => first + i);
    Promise.all(years.map(async year => ({ year, balance: leaveBucket(form.vacationType) ? (await userAPI.getLeaveBalance(userId, year)).data : null })))
      .then(balances => { if (active) setState({ rows: calculateLeavePreview(form, requests, balances, editingId), loading: false, error: '' }); })
      .catch(() => { if (active) setState({ rows: [], loading: false, error: 'Balance preview is unavailable. Your balance will be checked again when submitting.' }); });
    return () => { active = false; };
  }, [form.startDate, form.endDate, form.vacationType, userId, requests, editingId, valid]);
  if (!valid) return null;
  return <section className="workflow-panel" aria-label="Leave balance preview" aria-live="polite">
    <h3>Balance after this request</h3>
    <p>Estimate in working days, after approved leave and pending requests. Weekends are excluded.</p>
    {state.loading && <p>Calculating balance...</p>}{state.error && <p role="alert">{state.error}</p>}
    {state.rows.map(row => <div key={row.year}><h4>{row.year}</h4>
      {leaveBucket(form.vacationType) && row.configured === false && <p>No allowance has been configured for this year.</p>}
      <dl className="leave-preview-grid"><div><dt>Available before request</dt><dd>{row.available}</dd></div><div><dt>Requested days</dt><dd>{row.requested}</dd></div><div><dt>Paid days remaining</dt><dd>{row.remaining}</dd></div><div><dt>Unpaid days in request</dt><dd>{row.unpaid}</dd></div></dl>
      {row.pending > 0 && <p>{row.pending} days reserved by pending requests.</p>}
    </div>)}
  </section>;
}
