import React, { useId, useState } from 'react';
import './PasswordForm.css';

export const passwordHelp = 'Use at least 12 characters with uppercase, lowercase and a number (maximum 72 UTF-8 bytes).';

export default function PasswordForm({ requireCurrent = false, onSubmit, label }) {
  const id = useId();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const submit = async event => {
    event.preventDefault();
    setError('');
    if (newPassword !== confirmation) { setError('Passwords do not match.'); return; }
    if (newPassword.length < 12 || new TextEncoder().encode(newPassword).length > 72
        || !/[a-z]/.test(newPassword) || !/[A-Z]/.test(newPassword) || !/[0-9]/.test(newPassword)) {
      setError(passwordHelp); return;
    }
    setBusy(true);
    try {
      await onSubmit({ ...(requireCurrent ? { currentPassword } : {}), newPassword, confirmation });
      setCurrentPassword(''); setNewPassword(''); setConfirmation('');
    } catch (err) {
      setError(err.response?.status === 429 ? 'Too many attempts. Please wait a minute.'
        : err.response?.data?.message || 'Unable to update your password. Please try again.');
    } finally { setBusy(false); }
  };
  return <form className="password-form" onSubmit={submit}>
    <p id={`${id}-help`} className="password-help">{passwordHelp}</p>
    {requireCurrent && <div className="form-group"><label htmlFor={`${id}-current`}>Current password</label>
      <input id={`${id}-current`} type="password" autoComplete="current-password" required maxLength={72} disabled={busy}
        value={currentPassword} onChange={e => setCurrentPassword(e.target.value)} /></div>}
    <div className="form-group"><label htmlFor={`${id}-new`}>New password</label>
      <input id={`${id}-new`} type="password" autoComplete="new-password" required minLength={12} maxLength={72}
        aria-describedby={`${id}-help`} disabled={busy} value={newPassword} onChange={e => setNewPassword(e.target.value)} /></div>
    <div className="form-group"><label htmlFor={`${id}-confirm`}>Confirm new password</label>
      <input id={`${id}-confirm`} type="password" autoComplete="new-password" required maxLength={72} disabled={busy}
        value={confirmation} onChange={e => setConfirmation(e.target.value)} /></div>
    {error && <p role="alert" className="error-message">{error}</p>}
    <button className="button button-primary" disabled={busy}>{busy ? 'Saving...' : label}</button>
  </form>;
}
