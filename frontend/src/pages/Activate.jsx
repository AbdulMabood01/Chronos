import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { authAPI } from '../api';
import { BrandLogo } from '../components/Hourglass';
import './Login.css';

export default function Activate() {
  const [token] = useState(() => new URLSearchParams(window.location.search).get('token') || '');
  const [employee, setEmployee] = useState(null);
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [done, setDone] = useState(false);
  useEffect(() => {
    // Remove the secret from browser history before making requests.
    window.history.replaceState(window.history.state, '', '/activate');
    let cancelled = false;
    authAPI.validateInvitation(token).then(response => {
      if (!cancelled) setEmployee(response.data);
    }).catch(err => {
      if (!cancelled) setError(err.response?.data?.message || 'Unable to validate this invitation. Ask your administrator for a new link.');
    }).finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [token]);
  const submit = async event => {
    event.preventDefault();
    if (password !== confirmation) { setError('Passwords do not match.'); return; }
    if (password.length < 12 || new TextEncoder().encode(password).length > 72 || !/[a-z]/.test(password) || !/[A-Z]/.test(password) || !/[0-9]/.test(password)) {
      setError('Use at least 12 characters with uppercase, lowercase and a number (maximum 72 UTF-8 bytes).'); return;
    }
    setLoading(true); setError('');
    try { await authAPI.activate(token, password); setPassword(''); setConfirmation(''); setDone(true); }
    catch (err) { setError(err.response?.data?.message || 'Activation failed. Please retry or contact your administrator.'); }
    finally { setLoading(false); }
  };
  return <div className="login-container"><main className="login-card">
    <BrandLogo /><h1>Activate your Chronos account</h1>
    {loading && <p role="status">Please wait…</p>}
    {error && <p role="alert" className="error-message">{error}</p>}
    {done ? <p role="status">Your account is active. <Link to="/login">Sign in</Link></p> : employee && <>
      <p>Welcome, {employee.firstName} {employee.lastName}.<br />{employee.email}</p>
      <form onSubmit={submit}>
        <p id="password-help">Use at least 12 characters with uppercase, lowercase and a number. Maximum 72 UTF-8 bytes.</p>
        <div className="form-group"><label htmlFor="new-password">Create password</label>
          <input id="new-password" type="password" autoComplete="new-password" aria-describedby="password-help" required minLength={12} maxLength={72} value={password} onChange={e => setPassword(e.target.value)} /></div>
        <div className="form-group"><label htmlFor="confirm-password">Confirm password</label>
          <input id="confirm-password" type="password" autoComplete="new-password" required maxLength={72} value={confirmation} onChange={e => setConfirmation(e.target.value)} /></div>
        <button className="button button-primary" disabled={loading}>Activate account</button>
      </form>
    </>}
  </main></div>;
}
