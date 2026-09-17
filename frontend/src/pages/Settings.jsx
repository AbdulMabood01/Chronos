import ScreenTitle, { RecordSummary } from '../components/ScreenTitle';
import React, { useState, useEffect } from 'react';
import { useAuth } from '../AuthContext';
import { settingsAPI } from '../api';
import { LoadingIndicator } from '../components/Hourglass';
import '../styles.css';

const knownSettings = {
  company_name: {
    title: 'Company Name',
    description: 'Shown on exports, letters, and workspace documents.',
    type: 'text',
    fallback: 'Maxwell Network',
  },
  sick_days_per_year: { title: 'Sick Days', description: 'Default annual sick leave allowance.', type: 'number', fallback: '5', suffix: 'days' },
  bereavement_days_per_year: { title: 'Bereavement Days', description: 'Default annual bereavement allowance.', type: 'number', fallback: '3', suffix: 'days' },
  vacation_days_per_year: {
    title: 'Paid Vacation Days',
    description: 'Default annual allowance for vacation and other paid leave.',
    type: 'number',
    fallback: '15',
    suffix: 'days',
  },
  'timesheet.reminders.enabled': {
    title: 'Timesheet Reminders',
    description: 'Controls whether the weekday reminder job sends in-app reminders.',
    type: 'boolean',
    fallback: 'true',
  },
  'timesheet.reminders.initial_days_before_month_end': {
    title: 'First Reminder Lead Time',
    description: 'How many days before month-end the first reminder should go out.',
    type: 'number',
    fallback: '7',
    suffix: 'days',
  },
  'timesheet.reminders.final_working_week_daily': {
    title: 'Daily Final Week Nudges',
    description: 'Sends reminders during the final working week until the timesheet is submitted.',
    type: 'boolean',
    fallback: 'true',
  },
  'timesheet.reminders.frequency': {
    title: 'Reminder Frequency',
    description: 'Policy label for reminder cadence. Current reminder job uses final-week rules above.',
    type: 'select',
    fallback: 'DAILY',
    options: ['DAILY', 'WEEKLY'],
  },
  'timesheet.reminders.notification_method': {
    title: 'Notification Method',
    description: 'Policy label for reminder delivery. Current supported delivery is in-app notification.',
    type: 'select',
    fallback: 'IN_APP',
    options: ['IN_APP'],
  },
};

const primaryKeys = ['company_name'];
const leaveKeys = ['vacation_days_per_year', 'sick_days_per_year', 'bereavement_days_per_year'];
const reminderKeys = [
  'timesheet.reminders.enabled',
  'timesheet.reminders.initial_days_before_month_end',
  'timesheet.reminders.final_working_week_daily',
  'timesheet.reminders.frequency',
  'timesheet.reminders.notification_method',
];

export default function Settings() {
  const { user } = useAuth();
  const [settings, setSettings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [editingKey, setEditingKey] = useState(null);
  const [draftValue, setDraftValue] = useState('');
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [leaveYear, setLeaveYear] = useState(new Date().getFullYear());
  const [bulkMessage, setBulkMessage] = useState('');
  const canManageSettings = user?.role === 'SUPER_ADMIN';
  const settingMap = settings.reduce((acc, item) => ({ ...acc, [item.key]: item }), {});
  const enabledSetting = settingMap['timesheet.reminders.enabled']?.value ?? knownSettings['timesheet.reminders.enabled'].fallback;
  const finalWeekSetting = settingMap['timesheet.reminders.final_working_week_daily']?.value ?? knownSettings['timesheet.reminders.final_working_week_daily'].fallback;
  const advancedSettings = settings.filter((item) => !knownSettings[item.key]);

  useEffect(() => {
    if (!canManageSettings) {
      return;
    }
    loadSettings();
  }, [canManageSettings]);

  const loadSettings = async () => {
    try {
      const response = await settingsAPI.getAllSettings();
      setSettings(response.data || []);
      setError('');
    } catch (err) {
      setError('Failed to load settings');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleUpdate = async (key, nextValue = draftValue) => {
    setSaving(true); setSaved(false);
    try {
      await settingsAPI.updateSetting(key, nextValue);
      setEditingKey(null); setSaved(true);
      await loadSettings();
    } catch (err) {
      setError('Failed to update setting. Your changes are still available below.');
    } finally { setSaving(false); }
  };

  const applyDefaults = async () => {
    if (!window.confirm(`Apply saved leave defaults to all employees and admins for ${leaveYear}? This overwrites annual allowances and resets extra days to zero. Approved leave remains deducted. Other years are unchanged.`)) return;
    setSaving(true); setError(''); setBulkMessage('');
    try {
      const response = await settingsAPI.applyLeaveDefaults(leaveYear);
      setBulkMessage(`Defaults applied to ${response.data.updated} employees for ${leaveYear}.`);
    } catch (err) { setError(err.response?.data?.message || 'Unable to apply leave defaults.'); }
    finally { setSaving(false); }
  };

  const valueFor = (key) => settingMap[key]?.value ?? knownSettings[key]?.fallback ?? '';

  const startEdit = (key) => {
    setEditingKey(key);
    setDraftValue(valueFor(key));
    setSaved(false);
  };

  const renderSettingControl = (key) => {
    const config = knownSettings[key];
    const value = valueFor(key);
    const isEditing = editingKey === key;
    if (config.type === 'boolean') {
      const checked = String(value).toLowerCase() === 'true';
      return (
        <button
          className={`settings-toggle ${checked ? 'active' : ''}`}
          disabled={saving}
          type="button"
          onClick={() => handleUpdate(key, checked ? 'false' : 'true')}
          aria-pressed={checked}
        >
          <span>{checked ? 'On' : 'Off'}</span>
        </button>
      );
    }
    if (isEditing) {
      const input = config.type === 'select'
        ? (
          <select className="setting-card-input" value={draftValue} onChange={(event) => setDraftValue(event.target.value)} disabled={saving} autoFocus>
            {config.options.map((option) => <option key={option} value={option}>{option.replace('_', ' ')}</option>)}
          </select>
        )
        : (
          <input
            className="setting-card-input"
            type={config.type}
            min={config.type === 'number' ? '0' : undefined}
            step="1"
            value={draftValue}
            onChange={(event) => setDraftValue(event.target.value)}
            autoFocus
            disabled={saving}
          />
        );
      return (
        <div className="setting-card-edit">
          {input}
          <button className="button button-small button-primary" disabled={saving} type="button" onClick={() => handleUpdate(key)}>{saving ? 'Saving...' : 'Save'}</button>
          <button className="button button-small button-secondary" disabled={saving} type="button" onClick={() => setEditingKey(null)}>Cancel</button>
        </div>
      );
    }
    return (
      <div className="setting-card-value">
        <strong>{config.prefix || ''}{value || 'Not configured'}{config.suffix ? ` ${config.suffix}` : ''}</strong>
        <button className="button button-small button-secondary" type="button" onClick={() => startEdit(key)}>Change</button>
      </div>
    );
  };

  const renderSettingCard = (key) => {
    const config = knownSettings[key];
    return (
      <article className="settings-control-card" key={key}>
        <div>
          <span>{key}</span>
          <h2>{config.title}</h2>
          <p>{config.description}</p>
        </div>
        {renderSettingControl(key)}
      </article>
    );
  };

  if (!canManageSettings) {
    return (
      <div className="page-container">
        <div className="error-message">You do not have permission to access this page.</div>
      </div>
    );
  }

  if (loading) {
    return <div className="page-container"><div className="loading-panel"><LoadingIndicator label="Loading settings..." /></div></div>;
  }

  return (
    <div className="page-container settings-page">
      <div className="header-bar">
        <div>
          <ScreenTitle title="System Settings" icon="settings" eyebrow="WORKSPACE CONTROLS" />
          <p className="page-subtitle">Manage company defaults and automated timesheet reminder behavior.</p>
        </div>
      </div>

      {error && <div className="error-message" role="alert">{error}</div>}
      {saved && <div className="success-message" role="status">Setting updated.</div>}

      <RecordSummary items={[
        { label: 'Configured settings', value: settings.length, icon: 'settings' },
        { label: 'Reminders', value: String(enabledSetting).toLowerCase() === 'true' ? 'On' : 'Off', icon: 'bell' },
        { label: 'Final week nudges', value: String(finalWeekSetting).toLowerCase() === 'true' ? 'On' : 'Off', icon: 'clock' },
      ]} />

      <section className="settings-section" aria-label="Leave policy">
        <h2>Leave Defaults / Leave Policy</h2>
        <div className="settings-card-grid">{leaveKeys.map(renderSettingCard)}</div>
        <p>Apply saved allowances to all employees and admins, including inactive employees. Approved leave remains deducted from these annual allowances.</p>
        <label>Leave year <input type="number" min="1900" max="9998" value={leaveYear} disabled={saving} onChange={e => setLeaveYear(Number(e.target.value))} /></label>
        <button type="button" className="button button-primary" disabled={saving || editingKey !== null || !Number.isInteger(leaveYear) || leaveYear < 1900 || leaveYear > 9998} onClick={applyDefaults}>Apply Defaults to All Employees</button>
        {bulkMessage && <p role="status">{bulkMessage}</p>}
      </section>

      <section className="settings-hero-card">
        <div>
          <span className="eyebrow">REMINDER ENGINE</span>
          <h2>Keep month-end timesheets moving automatically.</h2>
          <p>Reminders run on weekdays at 9:00 AM. SuperAdmins are excluded from reminder delivery.</p>
        </div>
        <div className="settings-hero-status">
          <strong>{String(enabledSetting).toLowerCase() === 'true' ? 'Active' : 'Paused'}</strong>
          <span>Timesheet reminder job</span>
        </div>
      </section>

      <section className="settings-section">
        <div className="settings-section-heading">
          <span>COMPANY DEFAULTS</span>
          <h2>Business identity and default policies</h2>
        </div>
        <div className="settings-card-grid">
          {primaryKeys.map(renderSettingCard)}
        </div>
      </section>

      <section className="settings-section">
        <div className="settings-section-heading">
          <span>TIMESHEET REMINDERS</span>
          <h2>Reminder schedule and delivery</h2>
        </div>
        <div className="settings-card-grid">
          {reminderKeys.map(renderSettingCard)}
        </div>
      </section>

      <section className="settings-section">
        <div className="settings-section-heading">
          <span>ADVANCED</span>
          <h2>Other raw settings</h2>
        </div>
        {advancedSettings.length === 0 ? (
          <div className="empty-state">
            <p>No additional advanced settings configured.</p>
          </div>
        ) : (
          <div className="table-container">
          <table className="data-table">
            <thead>
              <tr>
                <th>Key</th>
                <th>Value</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {advancedSettings.map((s) => (
                <tr key={s.key}>
                  <td>{s.key}</td>
                  <td>{editingKey === s.key ? <input className="setting-value-input" aria-label={`Value for ${s.key}`} value={draftValue} onChange={e => setDraftValue(e.target.value)} autoFocus disabled={saving}/> : <code className="setting-value">{s.value || "Not configured"}</code>}</td>
                  <td>
                    {editingKey === s.key ? <div className="compact-actions"><button className="button button-small button-primary" disabled={saving} onClick={() => handleUpdate(s.key)}>{saving ? 'Saving...' : 'Save'}</button><button className="button button-small button-secondary" disabled={saving} onClick={() => setEditingKey(null)}>Cancel</button></div> : <button className="button button-small button-secondary" onClick={() => startEdit(s.key)}>Edit</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
        )}
      </section>
    </div>
  );
}
