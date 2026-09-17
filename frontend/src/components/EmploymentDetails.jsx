import React, { useEffect, useState } from 'react';
import { format, intervalToDuration, isValid, parseISO } from 'date-fns';
import { userAPI } from '../api';
import './EmploymentDetails.css';

export default function EmploymentDetails({ user, editable = false, onSaved, onSavingChange }) {
  const [date, setDate] = useState(user.joiningDate || '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  useEffect(() => { setDate(user.joiningDate || ''); }, [user.id, user.joiningDate]);
  const parsed = user.joiningDate ? parseISO(user.joiningDate) : null;
  const start = parsed && isValid(parsed) ? parsed : null;
  const duration = start && start <= new Date() ? intervalToDuration({ start, end: new Date() }) : null;
  const tenure = duration ? ['years', 'months', 'days'].filter(unit => duration[unit]).map(unit => duration[unit] + ' ' + (duration[unit] === 1 ? unit.slice(0, -1) : unit)).join(', ') || 'First day' : start ? 'Starts soon' : 'Awaiting start date';
  const save = async event => {
    event.preventDefault(); setSaving(true); onSavingChange?.(true); setError(''); setMessage('');
    try { const response = await userAPI.updateJoiningDate(user.id, date); onSaved?.(response.data); setMessage('Employment details saved.'); }
    catch (err) { setError(err.response?.data?.message || 'Unable to save joining date.'); }
    finally { setSaving(false); onSavingChange?.(false); }
  };
  return <section className="employment-details" aria-label="Employment details">
    <div><span className="eyebrow">EMPLOYMENT</span><h2>Time with the team</h2><p className="employment-description">Joining date and length of service.</p></div>
    <dl className="employment-metrics">
      <div><dt>Joining date</dt><dd>{start ? format(start, 'MMMM d, yyyy') : 'Add a joining date'}</dd></div>
      <div><dt>Tenure</dt><dd>{tenure}</dd></div>
    </dl>
    {editable && <form onSubmit={save} className="employment-form"><label htmlFor="employment-start-date">Employment start date<input id="employment-start-date" type="date" value={date} onChange={e => { setDate(e.target.value); setMessage(''); }} disabled={saving} /></label><button className="button button-primary" disabled={saving}>{saving ? 'Saving...' : 'Save joining date'}</button></form>}
    {error && <p role="alert" className="error-message">{error}</p>}
    {message && <p role="status" className="employment-saved">{message}</p>}
  </section>;
}
