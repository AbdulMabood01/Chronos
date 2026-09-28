import { useEffect, useState } from 'react';
import { useAuth } from '../AuthContext';
import apiClient from '../api';

export default function ConnectionAndSession() {
  const { user, sessionWarning, staySignedIn, logout } = useAuth();
  const [lost, setLost] = useState(false);
  const [restored, setRestored] = useState(false);
  const [renewError, setRenewError] = useState('');

  useEffect(() => {
    const onLost = () => { setLost(true); setRestored(false); };
    const onRestored = () => { setLost(false); setRestored(true); };
    window.addEventListener('chronos:connection-lost', onLost);
    window.addEventListener('chronos:connection-restored', onRestored);
    window.addEventListener('offline', onLost);
    window.addEventListener('online', onRestored);
    return () => { window.removeEventListener('chronos:connection-lost', onLost); window.removeEventListener('chronos:connection-restored', onRestored); window.removeEventListener('offline', onLost); window.removeEventListener('online', onRestored); };
  }, []);
  useEffect(() => {
    if (!lost) return;
    const timer = setInterval(() => apiClient.get('/health', { background: true, publicAuth: true, recoveryProbe: true, timeout: 5000 }).catch(() => {}), 10000);
    return () => clearInterval(timer);
  }, [lost]);
  useEffect(() => {
    if (!restored) return;
    const timer = setTimeout(() => setRestored(false), 4000);
    return () => clearTimeout(timer);
  }, [restored]);
  return <>
    {lost && <div className="connection-banner" role="alert">Connection lost. Check your internet connection. Trying to reconnect…</div>}
    {restored && <div className="connection-banner connection-restored" role="status">Connection restored</div>}
    {user && sessionWarning && <div className="modal-backdrop"><div className="modal-card" role="dialog" aria-modal="true" aria-labelledby="session-warning-title">
      <h2 id="session-warning-title">Your session is about to expire</h2>
      <p>You'll be signed out in 2 minutes due to inactivity.</p>
      {renewError && <p role="alert" className="error-message">{renewError}</p>}
      <div className="action-bar compact-actions">
        <button className="button button-primary" type="button" onClick={() => staySignedIn().catch(e => setRenewError(e.userMessage || 'Unable to connect. Check your internet connection and try again.'))}>Stay Signed In</button>
        <button className="button button-secondary" type="button" onClick={logout}>Sign Out</button>
      </div>
    </div></div>}
  </>;
}
