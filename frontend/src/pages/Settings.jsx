import React, { useState, useEffect } from 'react';
import { useAuth } from '../AuthContext';
import { settingsAPI } from '../api';
import { LoadingIndicator } from '../components/Hourglass';
import '../styles.css';

export default function Settings() {
  const { user } = useAuth();
  const [settings, setSettings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const canManageSettings = ['ADMIN', 'SUPER_ADMIN'].includes(user?.role);

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

  const handleUpdate = async (key, currentValue) => {
    const value = prompt(`Enter new value for "${key}":`, currentValue ?? '');
    if (value === null) return;

    try {
      await settingsAPI.updateSetting(key, value);
      await loadSettings();
    } catch (err) {
      setError('Failed to update setting');
    }
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
    <div className="page-container">
      <h1>System Settings</h1>
      <p className="page-subtitle">Timesheet reminder defaults are configurable here.</p>

      {error && <div className="error-message">{error}</div>}

      {settings.length === 0 ? (
        <div className="empty-state">
          <p>No settings configured yet.</p>
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
              {settings.map((s) => (
                <tr key={s.key}>
                  <td>{s.key}</td>
                  <td>{s.value}</td>
                  <td>
                    <button className="button button-small" onClick={() => handleUpdate(s.key, s.value)}>
                      Edit
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
