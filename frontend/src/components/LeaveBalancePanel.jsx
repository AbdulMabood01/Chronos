import React, { useEffect, useState } from 'react';
import { userAPI } from '../api';
import './LeaveBalancePanel.css';

export default function LeaveBalancePanel({ userId, editable = false, onSavingChange }) {
  const [year, setYear] = useState(new Date().getFullYear());
  const [balance, setBalance] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [form, setForm] = useState({ vacationDays: 0, sickDays: 0, bereavementDays: 0, addVacationDays: 0, addSickDays: 0, reason: '' });

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setLoading(true); setBalance(null); setError(''); setMessage('');
      if (!Number.isInteger(year) || year < 1900 || year > 9998) { setLoading(false); setError('Enter a year between 1900 and 9998.'); return; }
      try {
        const response = await userAPI.getLeaveBalance(userId, year);
        if (cancelled) return;
        setBalance(response.data);
        setForm({ vacationDays: response.data.vacation.allowanceDays, sickDays: response.data.sick.allowanceDays, bereavementDays: response.data.bereavement?.allowanceDays ?? 0, addVacationDays: 0, addSickDays: 0, reason: '' });
      } catch (err) { if (!cancelled) setError(err.response?.data?.message || 'Unable to load leave balances.'); }
      finally { if (!cancelled) setLoading(false); }
    };
    load();
    if (!editable) window.addEventListener('focus', load);
    return () => { cancelled = true; window.removeEventListener('focus', load); };
  }, [userId, year, editable]);

  const save = async (event) => {
    event.preventDefault(); setSaving(true); onSavingChange?.(true); setError(''); setMessage('');
    try {
      const response = await userAPI.updateLeaveAllowance(userId, { year, ...form,
        vacationDays: Number(form.vacationDays), sickDays: Number(form.sickDays), bereavementDays: Number(form.bereavementDays),
        addVacationDays: Number(form.addVacationDays), addSickDays: Number(form.addSickDays) });
      setBalance(response.data);
      setForm(current => ({ ...current, addVacationDays: 0, addSickDays: 0, reason: '' }));
      setMessage('Leave allowance saved.');
    } catch (err) { setError(err.response?.data?.message || 'Unable to save leave allowance.'); }
    finally { setSaving(false); onSavingChange?.(false); }
  };

  return <section className="leave-balance-panel" aria-label="Leave balances">
    <div className="leave-balance-heading">
      <div>
        <h2>Leave balances</h2>
        <p>Review the selected year and adjust annual or extra leave days.</p>
      </div>
      <label className="leave-year-field">Year <input type="number" min="1900" max="9998" value={year || ''} disabled={saving} onChange={e => setYear(Number(e.target.value))} /></label>
    </div>
    {error && <p role="alert" className="error-message">{error}</p>}
    {message && <p role="status" className="leave-status-message">{message}</p>}
    {loading && <p role="status" className="leave-loading">Loading balances...</p>}
    {balance && <>
      {!balance.configured && <p className="leave-policy-note">No allowance set for {year}. Super Admin can assign vacation, sick, and bereavement days.</p>}
      <div className="leave-balance-grid">{['vacation', 'sick', 'bereavement'].filter(type => balance[type]).map(type => <div className="leave-balance-tile" key={type}>
        <h3>{type === 'vacation' ? 'Vacation' : type === 'sick' ? 'Sick leave' : 'Bereavement leave'}</h3>
        <strong>{Number(balance[type].remainingDays).toFixed(1)} <small>days remaining</small></strong>
        <dl>
          <div><dt>Annual</dt><dd>{Number(balance[type].allowanceDays)}</dd></div>
          <div><dt>Extra</dt><dd>{Number(balance[type].extraDays)}</dd></div>
          <div><dt>Approved</dt><dd>{Number(balance[type].usedDays)}</dd></div>
          <div><dt>Unpaid</dt><dd>{Number(balance[type].unpaidDays)}</dd></div>
        </dl>
      </div>)}</div>
      <p className="leave-policy-note">Approved weekday leave uses the annual allowance, then extra days. Further days are unpaid. One day equals 8 hours. Allowances apply to the selected calendar year.</p>
      {editable && <form onSubmit={save} className="leave-allowance-form">
        <fieldset disabled={saving} className="leave-allowance-fields"><legend>Set allowance and add extra days</legend>
          {[['vacationDays', 'Annual vacation days'], ['sickDays', 'Annual sick days'], ['bereavementDays', 'Annual bereavement days'], ['addVacationDays', 'Add extra vacation days'], ['addSickDays', 'Add extra sick days']].map(([key, label]) =>
            <label key={key}>{label}<input type="number" required min="0" max="366" step="0.01" value={form[key]} onChange={e => setForm(current => ({ ...current, [key]: e.target.value }))} /></label>)}
          <label className="leave-reason">Reason<input required maxLength={500} value={form.reason} onChange={e => setForm(current => ({ ...current, reason: e.target.value }))} placeholder="Annual allocation or additional leave grant" /></label>
        </fieldset>
        <button className="button button-primary" disabled={saving}>{saving ? 'Saving...' : 'Save allowance'}</button>
      </form>}
    </>}
  </section>;
}
