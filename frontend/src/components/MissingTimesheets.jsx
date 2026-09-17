import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { timesheetAPI } from '../api';
import './MissingTimesheets.css';

const statusLabels = { NOT_STARTED: 'Not started', DRAFT: 'Draft', SUBMITTED: 'Submitted', APPROVED: 'Approved', LOCKED: 'Locked', REJECTED: 'Needs correction', CHANGE_REQUESTED: 'Change requested' };
const columns = [['userName', 'Employee'], ['projectCode', 'Project'], ['status', 'Status'], ['hours', 'Hours'], ['details', 'Details']];
const detailsLabel = row => row.timesheetId ? 'View timesheet' : 'No timesheet yet';
const sortValue = (row, key) => key === 'hours' ? Number(row.hours || 0) : key === 'status' ? statusLabels[row.status] || row.status : key === 'details' ? detailsLabel(row) : row[key] || '';

export default function MissingTimesheets() {
  const now = new Date();
  const [period, setPeriod] = useState(now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0'));
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);
  const [projectId, setProjectId] = useState('');
  const [sort, setSort] = useState({ key: 'userName', direction: 'asc' });
  const projects = [...new Map(rows.map(row => [String(row.projectId), row.projectCode])).entries()]
    .sort((a, b) => a[1].localeCompare(b[1]));
  const displayedRows = rows.filter(row => !projectId || String(row.projectId) === projectId).sort((a, b) => {
    const left = sortValue(a, sort.key), right = sortValue(b, sort.key);
    const comparison = sort.key === 'hours' ? left - right : String(left).localeCompare(String(right), undefined, { numeric: true, sensitivity: 'base' });
    return comparison * (sort.direction === 'asc' ? 1 : -1) || a.userName.localeCompare(b.userName) || a.projectCode.localeCompare(b.projectCode);
  });
  const outstanding = displayedRows.filter(row => ['NOT_STARTED', 'DRAFT', 'REJECTED'].includes(row.status)).length;
  const changeSort = key => setSort(previous => ({ key, direction: previous.key === key && previous.direction === 'asc' ? 'desc' : 'asc' }));
  useEffect(() => {
    let current = true;
    setLoading(true); setError(''); setRows([]);
    const [year, month] = period.split('-').map(Number);
    if (!year || !month) { setLoading(false); return; }
    Promise.resolve().then(() => timesheetAPI.getMissingTimesheets(year, month))
      .then(response => { if (current) setRows(response.data || []); })
      .catch(() => { if (current) setError('Could not load missing timesheets.'); })
      .finally(() => { if (current) setLoading(false); });
    return () => { current = false; };
  }, [period, refresh]);
  return <section className="admin-panel missing-timesheets" aria-labelledby="missing-title">
    <div className="panel-heading"><div><h2 id="missing-title">Missing timesheets</h2><p>Track submissions and approved records for active projects. The current month is still in progress.</p></div>
      </div>
    <div className="missing-timesheets-toolbar"><div className="form-group"><label htmlFor="missing-submission-month">Submission month</label><input id="missing-submission-month" type="month" value={period} onChange={e => { setPeriod(e.target.value); setProjectId(''); }} /></div>
      <div className="form-group"><label htmlFor="missing-project-filter">Project (optional)</label><select id="missing-project-filter" value={projectId} disabled={loading} onChange={e => setProjectId(e.target.value)}><option value="">All active projects</option>{projects.map(([id, code]) => <option key={id} value={id}>{code}</option>)}</select></div>
      <button className="button button-secondary" type="button" disabled={loading || !period} onClick={() => setRefresh(n => n + 1)}>Refresh missing timesheets</button></div>
    {loading ? <div className="empty-state" role="status">Loading missing timesheets...</div> : error ? <div className="error-message" role="alert">{error}</div> : !period ? <div className="empty-state">Select a month.</div> : <>
      <p className="missing-timesheets-summary" role="status"><strong>{new Set(displayedRows.map(row => row.userId)).size}</strong> employees / <strong>{displayedRows.length}</strong> records / <strong>{outstanding}</strong> outstanding</p>
      {!displayedRows.length ? <div className="empty-state"><p>No timesheet records for this month{projectId ? ' and project' : ''}.</p></div> : <div className="table-container" tabIndex={0} role="region" aria-label="Project timesheet records"><table className="data-table"><thead><tr>{columns.map(([key, label]) => <th key={key} scope="col" className={key === 'hours' ? 'missing-timesheets-hours' : undefined} aria-sort={sort.key === key ? sort.direction === 'asc' ? 'ascending' : 'descending' : 'none'}><button type="button" className="missing-timesheets-sort" onClick={() => changeSort(key)}>{label}<span aria-hidden="true">{sort.key === key ? sort.direction === 'asc' ? ' ↑' : ' ↓' : ' ↕'}</span></button></th>)}</tr></thead>
        <tbody>{displayedRows.map(row => <tr key={row.userId + '-' + row.projectId}><td className="missing-timesheets-employee">{row.userName}</td><td>{row.projectCode}</td><td><span className={`status-badge status-${row.status === 'NOT_STARTED' ? 'draft' : row.status.toLowerCase()}`}>{statusLabels[row.status] || row.status}</span></td><td className="missing-timesheets-hours">{Number(row.hours || 0).toFixed(2)}</td><td>{row.timesheetId ? <Link className="missing-timesheets-link" to={'/timesheet/' + row.timesheetId + '?projectId=' + row.projectId}>View timesheet</Link> : <span className="missing-timesheets-muted">No timesheet yet</span>}</td></tr>)}</tbody></table></div>}
    </>}
  </section>;
}
