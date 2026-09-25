import React, { useEffect, useRef, useState } from 'react';
import { notificationAPI } from '../api';
import Icon from './Icon';
import './EmailAlertPreferences.css';

const categories = [
  ['announcements', 'Announcements'], ['timesheets', 'Timesheets'], ['vacation', 'Vacation'],
  ['letters', 'Letters'], ['reports', 'Reports'], ['feedback', 'Feedback'], ['performance', 'Performance'],
];

function PreferencesDialog({ onClose }) {
  const dialog = useRef(null);
  const [preferences, setPreferences] = useState(null);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => { dialog.current.showModal(); }, []);
  useEffect(() => {
    let active = true;
    setError('');
    notificationAPI.getEmailPreferences().then(({ data }) => { if (active) setPreferences(data); })
      .catch(() => { if (active) setError('Could not load email preferences. Please try again.'); });
    return () => { active = false; };
  }, [attempt]);
  const toggle = key => { setPreferences(p => ({ ...p, [key]: !p[key] })); setSaved(false); setError(''); };
  const save = async () => {
    setSaving(true); setError(''); setSaved(false);
    try {
      const { data } = await notificationAPI.saveEmailPreferences(preferences);
      setPreferences(data); setSaved(true);
    } catch { setError('Could not save email preferences. Your changes have not been saved. Please try again.'); }
    finally { setSaving(false); }
  };
  const control = (key, label, disabled = false) => <button type="button" role="switch"
    className="email-alert-switch" aria-checked={preferences[key]} aria-label={label}
    disabled={saving || disabled} onClick={() => toggle(key)}><span aria-hidden="true" /></button>;
  return <dialog ref={dialog} className="email-alert-dialog" aria-labelledby="email-alert-title"
    aria-describedby="email-alert-description" onClose={onClose}>
    <div className="email-alert-heading"><h2 id="email-alert-title">Email alerts</h2>
      <button className="icon-button" aria-label="Close email preferences" onClick={onClose}><Icon name="close" /></button></div>
    <p id="email-alert-description">Choose which updates arrive at your work email. In-app notifications stay available.</p>
    {error && <p role="alert">{error}</p>}
    {!preferences && !error && <p role="status">Loading preferences…</p>}
    {!preferences && error && <button className="button button-secondary" onClick={() => setAttempt(n => n + 1)}>Try again</button>}
    {preferences && <>
      <div className="email-alert-row email-alert-master"><span><strong>Enable email alerts</strong><small>Turn all email alerts on or off</small></span>{control('enabled', 'Enable email alerts')}</div>
      <div role="group" aria-label="Email categories">
        {categories.map(([key, label]) => <div className="email-alert-row" key={key}><span>{label}</span>{control(key, label, !preferences.enabled)}</div>)}
      </div>
      <p className="email-alert-note">Includes submissions, approvals, rejections, and updates where available. Timesheets also includes reminders. Account invitations are separate.</p>
      {saved && <p role="status">Email preferences saved.</p>}
      <div className="email-alert-actions"><button className="button button-secondary" onClick={onClose}>Close</button>
        <button className="button button-primary" onClick={save} disabled={saving}>{saving ? 'Saving…' : 'Save preferences'}</button></div>
    </>}
  </dialog>;
}

export default function EmailAlertPreferences() {
  const [open, setOpen] = useState(false);
  const trigger = useRef(null);
  const close = () => { setOpen(false); trigger.current?.focus(); };
  return <><button ref={trigger} className="icon-button" aria-label="Email alerts" title="Email alerts"
    aria-haspopup="dialog" onClick={() => setOpen(true)}><Icon name="mail" /></button>
    {open && <PreferencesDialog onClose={close} />}</>;
}
