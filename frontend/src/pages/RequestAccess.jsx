import { useState } from 'react';
import { Link } from 'react-router-dom';
import { companyAPI } from '../api';
import { BrandLogo } from '../components/Hourglass';
import '../styles.css';
import './Login.css';

export default function RequestAccess() {
  const [form, setForm] = useState({ slug: '', firstName: '', lastName: '', email: '' });
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const change = event => setForm(current => ({ ...current, [event.target.name]: event.target.value }));
  const submit = async event => {
    event.preventDefault(); setBusy(true); setError('');
    try {
      const response = await companyAPI.requestAccess(Object.fromEntries(
        Object.entries(form).map(([key, value]) => [key, value.trim()])));
      setMessage(response.data.message);
    } catch (err) {
      setError(err.response?.status === 429 ? 'Too many requests. Please try again in a minute.'
        : err.userMessage || 'Unable to send your request. Please try again.');
    } finally { setBusy(false); }
  };
  return <div className="login-container"><main className="login-card account-card">
    <BrandLogo /><h1>Request company access</h1>
    {message ? <p role="status" className="inline-alert">{message}</p> : <>
      <p className="signin-subtitle">Ask your company administrator for the workspace ID. They will review your request and email an invitation. Your request does not grant access.</p>
      <form onSubmit={submit}>
        <div className="form-group"><label htmlFor="workspaceId">Workspace ID</label>
          <input id="workspaceId" name="slug" required maxLength={80} autoComplete="organization" value={form.slug} onChange={change} placeholder="acme-corporation" /></div>
        <div className="form-group"><label htmlFor="firstName">First name</label>
          <input id="firstName" name="firstName" required maxLength={100} autoComplete="given-name" value={form.firstName} onChange={change} /></div>
        <div className="form-group"><label htmlFor="lastName">Last name</label>
          <input id="lastName" name="lastName" required maxLength={100} autoComplete="family-name" value={form.lastName} onChange={change} /></div>
        <div className="form-group"><label htmlFor="requestEmail">Work email</label>
          <input id="requestEmail" name="email" type="email" required maxLength={255} autoComplete="email" value={form.email} onChange={change} /></div>
        <p>Review the <Link to="/legal/terms">Terms of Use</Link> and <Link to="/legal/privacy">Privacy Policy</Link> before continuing.</p>
        <button className="button button-primary" disabled={busy}>{busy ? 'Sending...' : 'Send request'}</button>
      </form>
    </>}
    {error && <p role="alert" className="error-message">{error}</p>}
    <Link className="account-link" to="/login">Back to sign in</Link>
  </main></div>;
}
