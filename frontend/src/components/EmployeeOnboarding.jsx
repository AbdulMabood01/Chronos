import React, { useState } from 'react';
import { userAPI } from '../api';

export default function EmployeeOnboarding({ onCreated }) {
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const submit = async event => {
    event.preventDefault(); setBusy(true); setError('');
    try {
      await userAPI.createEmployee(form);
      setForm({ firstName: '', lastName: '', email: '' });
      await onCreated();
    } catch (err) { setError(err.response?.data?.message || 'Unable to create employee.'); }
    finally { setBusy(false); }
  };
  return <section className="employee-section" aria-label="Add employee">
    <h2>Add employee</h2><p>Create the employee, then send an invitation from their row below.</p>
    <form onSubmit={submit}>
      {Object.entries({ firstName: 'First name', lastName: 'Last name', email: 'Work email' }).map(([field, label]) =>
        <div className="form-group" key={field}><label htmlFor={'employee-' + field}>{label}</label>
          <input id={'employee-' + field} required type={field === 'email' ? 'email' : 'text'} maxLength={field === 'email' ? 255 : 100}
            value={form[field]} onChange={e => setForm({ ...form, [field]: e.target.value })} /></div>)}
      <button className="button button-primary" disabled={busy}>{busy ? 'Creating…' : 'Create employee'}</button>
      {error && <p role="alert" className="error-message">{error}</p>}
    </form>
  </section>;
}
