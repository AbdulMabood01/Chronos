import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { authAPI } from '../api';
import { useAuth } from '../AuthContext';
import { BrandLogo } from '../components/Hourglass';
import PasswordForm from '../components/PasswordForm';
import './Login.css';

function RecoveryCard({ title, children }) {
  return <div className="login-container"><main className="login-card">
    <BrandLogo /><h1>{title}</h1>{children}
    <p className="login-note"><Link to="/login">Back to sign in</Link></p>
  </main></div>;
}

export function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const submit = async event => {
    event.preventDefault(); setBusy(true); setError('');
    try {
      const response = await authAPI.forgotPassword(email.trim());
      setMessage(response.data.message);
    } catch (err) {
      setError(err.response?.status === 429 ? 'Too many attempts. Please wait a minute.'
        : err.response?.data?.message || 'Unable to request a reset link. Please try again.');
    } finally { setBusy(false); }
  };
  return <RecoveryCard title="Forgot password?">
    {message ? <p role="status">{message}</p> : <>
      <p className="signin-subtitle">Enter your work email to request a password reset link.</p>
      <form onSubmit={submit}>
        <div className="form-group"><label htmlFor="reset-email">Work email</label>
          <input id="reset-email" type="email" autoComplete="email" required maxLength={255} disabled={busy}
            value={email} onChange={e => setEmail(e.target.value)} /></div>
        <button className="button button-primary" disabled={busy}>{busy ? 'Sending...' : 'Send reset link'}</button>
      </form>
    </>}
    {error && <p role="alert" className="error-message">{error}</p>}
  </RecoveryCard>;
}

export function ResetPassword() {
  const { logout } = useAuth();
  const [token] = useState(() => new URLSearchParams(window.location.hash.slice(1)).get('token') || '');
  const [state, setState] = useState('loading');
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    window.history.replaceState(window.history.state, '', '/reset-password');
    let cancelled = false;
    if (!token) { setError('This reset link is invalid or expired. Request a new reset link.'); setState('invalid'); return; }
    setState('loading'); setError('');
    authAPI.validatePasswordReset(token).then(() => { if (!cancelled) setState('ready'); })
      .catch(err => {
        if (!cancelled) {
          setError(err.response?.status === 429 ? 'Too many attempts. Please wait a minute.'
            : err.response?.data?.message || 'Unable to validate this link. Please try again.');
          setState(err.response?.status === 400 ? 'invalid' : 'retry');
        }
      });
    return () => { cancelled = true; };
  }, [token, attempt]);
  const submit = async data => {
    await authAPI.resetPassword({ token, ...data });
    logout(); setState('done');
  };
  return <RecoveryCard title="Reset password">
    {state === 'loading' && <p role="status">Checking your reset link...</p>}
    {error && <p role="alert" className="error-message">{error}</p>}
    {state === 'retry' && <button className="button" onClick={() => setAttempt(attempt + 1)}>Try again</button>}
    {state === 'ready' && <PasswordForm onSubmit={submit} label="Reset password" />}
    {state === 'done' ? <p role="status">Password reset. Sign in with your new password. You have been signed out on all devices.</p>
      : <p className="login-note"><Link to="/forgot-password">Request a new reset link</Link></p>}
  </RecoveryCard>;
}
