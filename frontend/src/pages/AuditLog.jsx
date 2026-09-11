import ScreenTitle, { RecordSearch } from '../components/ScreenTitle';
import React, { useState, useEffect } from 'react';
import { useAuth } from '../AuthContext';
import { auditAPI } from '../api';
import { format } from 'date-fns';
import { LoadingIndicator } from '../components/Hourglass';
import '../styles.css';

export default function AuditLog() {
  const { user } = useAuth();
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const visibleRecords = logs.filter(record => [record.userName,record.action,record.entityType,record.details].join(' ').toLowerCase().includes(search.toLowerCase()));

  useEffect(() => {
    if (user?.role !== 'SUPER_ADMIN') {
      return;
    }
    loadLogs();
  }, [user]);

  const loadLogs = async () => {
    try {
      const response = await auditAPI.getAuditLogs();
      setLogs(response.data || []);
      setError('');
    } catch (err) {
      setError('Failed to load audit logs');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  if (user?.role !== 'SUPER_ADMIN') {
    return (
      <div className="page-container">
        <div className="error-message">You do not have permission to access this page.</div>
      </div>
    );
  }

  if (loading) {
    return <div className="page-container"><div className="loading-panel"><LoadingIndicator label="Loading audit logs..." /></div></div>;
  }

  return (
    <div className="page-container">
      <ScreenTitle title="Audit Log" icon="file" eyebrow="ACTIVITY & ACCOUNTABILITY" />

      <RecordSearch value={search} onChange={setSearch} placeholder="Search activity, people or details" label="Search activity, people or details" />
      {search && <p className="filter-count">{visibleRecords.length} matching records</p>}
      {error && <div className="error-message" role="alert">{error}</div>}

      {logs.length === 0 ? (
        <div className="empty-state">
          <p>No audit log entries yet.</p>
        </div>
      ) : (
        <div className="table-container">
          <table className="data-table">
            <thead>
              <tr>
                <th>Date</th>
                <th>User</th>
                <th>Action</th>
                <th>Entity</th>
                <th>Details</th>
              </tr>
            </thead>
            <tbody>
              {logs
                .slice()
                .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
                .map((log) => (
                  <tr key={log.id}>
                    <td>{format(new Date(log.createdAt), 'MMM dd, yyyy HH:mm')}</td>
                    <td>{log.userName || 'System'}</td>
                    <td>{log.action}</td>
                    <td>{log.entityType}{log.entityId ? ` #${log.entityId}` : ''}</td>
                    <td>{log.details?.message || '-'}</td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
