import ScreenTitle from '../components/ScreenTitle';
import React, { useEffect, useMemo, useState } from 'react';
import { useAuth } from '../AuthContext';
import { projectAPI, reportsAPI } from '../api';
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

export default function Reports() {
  const { user } = useAuth();
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [projectSummaries, setProjectSummaries] = useState([]);
  const [selectedProjectId, setSelectedProjectId] = useState('');
  const [employeeFilter, setEmployeeFilter] = useState('');
  const [selectedSubmissionIds, setSelectedSubmissionIds] = useState([]);
  const [loadingSummary, setLoadingSummary] = useState(true);
  const [error, setError] = useState('');
  const canViewReports = ['ADMIN', 'SUPER_ADMIN'].includes(user?.role);

  useEffect(() => {
    if (canViewReports) {
      loadSummary();
    }
  }, [canViewReports, year, month]);

  const selectedProject = useMemo(
    () => projectSummaries.find((project) => String(project.projectId) === String(selectedProjectId)),
    [projectSummaries, selectedProjectId],
  );
  const filteredRows = useMemo(() => {
    const rows = selectedProject?.employees || [];
    const query = employeeFilter.trim().toLowerCase();
    if (!query) return rows;
    return rows.filter((row) => `${row.userName || ''} ${row.employeeId || ''} ${row.email || ''}`.toLowerCase().includes(query));
  }, [selectedProject, employeeFilter]);
  const exportableSubmissionIds = useMemo(() => filteredRows
    .filter((row) => ['APPROVED', 'LOCKED'].includes(row.status) && row.submissionId)
    .map((row) => row.submissionId), [filteredRows]);
  const allSelected = exportableSubmissionIds.length > 0 && selectedSubmissionIds.length === exportableSubmissionIds.length;
  const selectedCount = selectedSubmissionIds.length;
  const selectedSet = useMemo(() => new Set(selectedSubmissionIds), [selectedSubmissionIds]);

  const loadSummary = async () => {
    setLoadingSummary(true);
    try {
      const response = await projectAPI.getHoursDashboard(year, month);
      const rows = response.data || [];
      setProjectSummaries(rows);
      setSelectedProjectId((current) => (rows.some((project) => String(project.projectId) === String(current))
        ? current
        : (rows[0]?.projectId || '')));
      setSelectedSubmissionIds([]);
      setError('');
    } catch (err) {
      setError('Failed to load project timesheet summary');
    } finally {
      setLoadingSummary(false);
    }
  };

  const toggleSubmission = (row) => {
    if (!['APPROVED', 'LOCKED'].includes(row.status) || !row.submissionId) {
      return;
    }
    setSelectedSubmissionIds((current) => (
      current.includes(row.submissionId)
        ? current.filter((id) => id !== row.submissionId)
        : [...current, row.submissionId]
    ));
  };

  const toggleAll = () => {
    setSelectedSubmissionIds(allSelected ? [] : exportableSubmissionIds);
  };

  const handleExportTimesheets = async () => {
    if (selectedSubmissionIds.length === 0) {
      setError('Select at least one approved project timesheet to export');
      return;
    }

    try {
      const response = await reportsAPI.exportProjectTimesheets(selectedSubmissionIds);
      downloadBlob(response.data, `project-timesheets-${year}-${month}.zip`);
      setError('');
    } catch (err) {
      setError('Failed to export selected project timesheets');
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
      <div className="page-container highlighted-workspace">
        <div className="error-message">You do not have permission to access this page.</div>
      </div>
    );
  }

  return (
    <div className="page-container highlighted-workspace admin-page">
      <div className="header-bar">
        <div>
          <ScreenTitle title="Reports" icon="chart" eyebrow="REPORTING CENTER" />
          <p className="page-subtitle">Export approved project timesheets by project and employee.</p>
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
            <label htmlFor="reports-field-1">Year</label>
            <input id="reports-field-1" type="number" value={year} onChange={(e) => setYear(Number(e.target.value))} />
          </div>
          <div className="form-group">
            <label htmlFor="reports-field-2">Month</label>
            <select id="reports-field-2" value={month} onChange={(e) => setMonth(Number(e.target.value))}>
              {monthNames.map((name, index) => (
                <option value={index + 1} key={name}>{name}</option>
              ))}
            </select>
          </div>
          <div className="form-group">
            <label htmlFor="reports-field-3">Project</label>
            <select id="reports-field-3" value={selectedProjectId} onChange={(e) => { setSelectedProjectId(e.target.value); setSelectedSubmissionIds([]); }}>
              {projectSummaries.map((project) => (
                <option value={project.projectId} key={project.projectId}>
                  {project.projectCode} - {project.projectName}
                </option>
              ))}
            </select>
          </div>
          <div className="form-group">
            <label htmlFor="reports-field-4">Employee Name</label>
            <input
              id="reports-field-4"
              value={employeeFilter}
              onChange={(e) => { setEmployeeFilter(e.target.value); setSelectedSubmissionIds([]); }}
              placeholder="All project members"
            />
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
            <h2>{selectedProject ? `${selectedProject.projectCode} - ${selectedProject.projectName}` : 'Project Timesheets'} - {monthNames[month - 1]} {year}</h2>
            <p>{selectedCount} approved project timesheet{selectedCount === 1 ? '' : 's'} selected. Draft, submitted, and rejected project timesheets cannot be selected.</p>
          </div>
          <button className="button button-primary report-download-button" onClick={handleExportTimesheets} disabled={selectedCount === 0}>
            Download Selected Timesheets
          </button>
        </div>

        {loadingSummary ? (
          <div className="loading-panel"><LoadingIndicator label="Loading project timesheets..." /></div>
        ) : projectSummaries.length === 0 ? (
          <div className="empty-state"><p>No projects available for this period.</p></div>
        ) : filteredRows.length === 0 ? (
          <div className="empty-state"><p>No project members match this filter.</p></div>
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
                      disabled={exportableSubmissionIds.length === 0}
                      aria-label="Select all approved project timesheets"
                    />
                  </th>
                  <th>Employee</th>
                  <th>Employee ID</th>
                  <th>Logged</th>
                  <th>Approved</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {filteredRows.map((row) => {
                  const canExport = ['APPROVED', 'LOCKED'].includes(row.status) && row.submissionId;
                  return (
                  <tr key={row.userId}>
                    <td>
                      <input
                        type="checkbox"
                        checked={selectedSet.has(row.submissionId)}
                        disabled={!canExport}
                        onChange={() => toggleSubmission(row)}
                        aria-label={`Select ${row.userName}`}
                      />
                    </td>
                    <td>{row.userName}</td>
                    <td>{row.employeeId || 'Not recorded'}</td>
                    <td>{Number(row.totalLoggedHours || 0).toFixed(2)}</td>
                    <td>{Number(row.approvedHours || 0).toFixed(2)}</td>
                    <td><span className={`status-badge status-${String(row.status).toLowerCase()}`}>{String(row.status).replace('_', ' ')}</span></td>
                  </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
