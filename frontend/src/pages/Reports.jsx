import React, { useEffect, useMemo, useState } from 'react';
import { useAuth } from '../AuthContext';
import { reportsAPI } from '../api';
import { LoadingIndicator } from '../components/Hourglass';
import '../styles.css';

const monthNames = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

function downloadBlob(blob, filename) {
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}

function money(value) {
  return value == null ? '-' : new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(Number(value));
}

export default function Reports() {
  const { user } = useAuth();
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [summary, setSummary] = useState([]);
  const [selectedUserIds, setSelectedUserIds] = useState([]);
  const [loadingSummary, setLoadingSummary] = useState(true);
  const [error, setError] = useState('');
  const canViewReports = ['ADMIN', 'SUPER_ADMIN'].includes(user?.role);

  useEffect(() => {
    if (canViewReports) {
      loadSummary();
    }
  }, [canViewReports, year, month]);

  const allSelected = summary.length > 0 && selectedUserIds.length === summary.length;
  const selectedCount = selectedUserIds.length;

  const selectedSet = useMemo(() => new Set(selectedUserIds), [selectedUserIds]);

  const loadSummary = async () => {
    setLoadingSummary(true);
    try {
      const response = await reportsAPI.getMonthlySummary(year, month);
      const rows = response.data || [];
      setSummary(rows);
      setSelectedUserIds(rows.map((row) => row.userId));
      setError('');
    } catch (err) {
      setError('Failed to load monthly summary');
    } finally {
      setLoadingSummary(false);
    }
  };

  const toggleUser = (userId) => {
    setSelectedUserIds((current) => (
      current.includes(userId)
        ? current.filter((id) => id !== userId)
        : [...current, userId]
    ));
  };

  const toggleAll = () => {
    setSelectedUserIds(allSelected ? [] : summary.map((row) => row.userId));
  };

  const handleExportTimesheets = async () => {
    if (selectedUserIds.length === 0) {
      setError('Select at least one employee to export');
      return;
    }

    try {
      const response = await reportsAPI.exportTimesheets(year, month, selectedUserIds);
      downloadBlob(response.data, `timesheets-${year}-${month}.zip`);
      setError('');
    } catch (err) {
      setError('Failed to export selected timesheets');
    }
  };

  const handleExportVacation = async () => {
    try {
      const response = await reportsAPI.exportVacationRequests(year);
      downloadBlob(response.data, `vacation-requests-${year}.xlsx`);
      setError('');
    } catch (err) {
      setError('Failed to export vacation requests');
    }
  };

  if (!canViewReports) {
    return (
      <div className="page-container">
        <div className="error-message">You do not have permission to access this page.</div>
      </div>
    );
  }

  return (
    <div className="page-container admin-page">
      <div className="header-bar">
        <div>
          <h1>Reports</h1>
          <p className="page-subtitle">Export selected employee timesheets from the monthly summary.</p>
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}

      <div className="card report-panel">
        <div className="panel-heading">
          <div>
            <h2>Select Period</h2>
            <p>Choose the month and year for reports.</p>
          </div>
        </div>
        <div className="form-row">
          <div className="form-group">
            <label>Year</label>
            <input type="number" value={year} onChange={(e) => setYear(Number(e.target.value))} />
          </div>
          <div className="form-group">
            <label>Month</label>
            <select value={month} onChange={(e) => setMonth(Number(e.target.value))}>
              {monthNames.map((name, index) => (
                <option value={index + 1} key={name}>{name}</option>
              ))}
            </select>
          </div>
        </div>
        <div className="action-buttons">
          <button className="button button-secondary" onClick={handleExportVacation}>
            Export Vacation Requests
          </button>
        </div>
      </div>

      <div className="card">
        <div className="panel-heading">
          <div>
            <h2>Monthly Summary - {monthNames[month - 1]} {year}</h2>
            <p>{selectedCount} employee{selectedCount === 1 ? '' : 's'} selected. Status is sorted by approved, draft, then rejected.</p>
          </div>
          <button className="button button-primary report-download-button" onClick={handleExportTimesheets} disabled={selectedCount === 0}>
            Download Selected Timesheets
          </button>
        </div>

        {loadingSummary ? (
          <div className="loading-panel"><LoadingIndicator label="Loading monthly summary..." /></div>
        ) : summary.length === 0 ? (
          <div className="empty-state"><p>No timesheets for this period.</p></div>
        ) : (
          <div className="table-container">
            <table className="data-table selectable-table">
              <thead>
                <tr>
                  <th>
                    <input
                      type="checkbox"
                      checked={allSelected}
                      onChange={toggleAll}
                      aria-label="Select all employees"
                    />
                  </th>
                  <th>Employee</th>
                  <th>Status</th>
                  <th>Total Hours</th>
                  <th>Rate</th>
                </tr>
              </thead>
              <tbody>
                {summary.map((row) => (
                  <tr key={row.userId}>
                    <td>
                      <input
                        type="checkbox"
                        checked={selectedSet.has(row.userId)}
                        onChange={() => toggleUser(row.userId)}
                        aria-label={`Select ${row.employee}`}
                      />
                    </td>
                    <td>{row.employee}</td>
                    <td><span className={`status-badge status-${String(row.status).toLowerCase()}`}>{String(row.status).replace('_', ' ')}</span></td>
                    <td>{Number(row.totalHours || 0).toFixed(2)}</td>
                    <td>{money(row.effectiveRate ?? row.approvedHourlyRate)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
