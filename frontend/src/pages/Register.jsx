import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { authAPI } from '../api';
import { BrandLogo } from '../components/Hourglass';
import '../styles.css';
import './Login.css';

export default function Register() {
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '' });
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const change = event => setForm(current => ({ ...current, [event.target.name]: event.target.value }));
  const submit = async event => {
    event.preventDefault();
    setBusy(true); setError('');
    try {
      const response = await authAPI.register({
        firstName: form.firstName.trim(), lastName: form.lastName.trim(), email: form.email.trim(),
      });
      setMessage(response.data.message);
    } catch (err) {
      setError(err.response?.status === 429 ? 'Too many attempts. Please wait a minute.'
        : err.response?.data?.message || 'Unable to register. Please try again or contact your administrator.');
    } finally { setBusy(false); }
  };
  return <div className="login-container"><main className="login-card account-card">
    <BrandLogo /><h1>Create your employee account</h1>
    {message ? <p role="status" className="inline-alert">{message}</p> : <>
      <p className="signin-subtitle">Enter your details. We’ll email you a link to create your password.</p>
      <form onSubmit={submit}>
        <div className="form-group"><label htmlFor="firstName">First name</label>
          <input id="firstName" name="firstName" autoComplete="given-name" required maxLength={100} disabled={busy} value={form.firstName} onChange={change} /></div>
        <div className="form-group"><label htmlFor="lastName">Last name</label>
          <input id="lastName" name="lastName" autoComplete="family-name" required maxLength={100} disabled={busy} value={form.lastName} onChange={change} /></div>
        <div className="form-group"><label htmlFor="registerEmail">Work email</label>
          <input id="registerEmail" name="email" type="email" autoComplete="email" required maxLength={255} disabled={busy} value={form.email} onChange={change} /></div>
        <button className="button button-primary" disabled={busy}>{busy ? 'Sending...' : 'Register'}</button>
      </form>
    </>}
    {error && <p role="alert" className="error-message">{error}</p>}
    <Link className="account-link" to="/login">Back to sign in</Link>
  </main></div>;
}
