import ScreenTitle from '../components/ScreenTitle';
import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { format, startOfMonth, subMonths } from 'date-fns';
import { projectAPI } from '../api';
import { useAuth } from '../AuthContext';
import { LoadingIndicator } from '../components/Hourglass';
import '../styles.css';

function monthOptions() {
  const now = startOfMonth(new Date());
  return Array.from({ length: 12 }).map((_, index) => {
    const date = subMonths(now, index);
    return {
      value: `${date.getFullYear()}-${date.getMonth() + 1}`,
      label: format(date, 'MMM yyyy'),
      year: date.getFullYear(),
      month: date.getMonth() + 1,
    };
  });
}

function hours(value) {
  return Number(value || 0).toFixed(2);
}

export default function ProjectHoursDashboard() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const options = useMemo(monthOptions, []);
  const [selectedPeriod, setSelectedPeriod] = useState(options[0].value);
  const [projects, setProjects] = useState([]);
  const [plannedHourDrafts, setPlannedHourDrafts] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const canView = user?.canReviewProjects || ['ADMIN', 'SUPER_ADMIN'].includes(user?.role);
  const canPlan = canView && user?.role !== 'SUPER_ADMIN';

  const selectedOption = options.find((option) => option.value === selectedPeriod) || options[0];

  useEffect(() => {
    if (!canView) return;
    loadDashboard();
  }, [canView, selectedPeriod]);

  const loadDashboard = async () => {
    setLoading(true);
    try {
      const response = await projectAPI.getHoursDashboard(selectedOption.year, selectedOption.month);
      setProjects(response.data || []);
      const drafts = {};
      (response.data || []).forEach((project) => {
        (project.employees || []).forEach((employee) => {
          drafts[`${project.projectId}:${employee.userId}`] = employee.plannedHours ?? '';
        });
      });
      setPlannedHourDrafts(drafts);
      setError('');
    } catch (err) {
      setError('Failed to load project hours dashboard');
    } finally {
      setLoading(false);
    }
  };

  const totals = useMemo(() => projects.reduce((acc, project) => ({
    planned: acc.planned + Number(project.plannedHours || 0),
    logged: acc.logged + Number(project.totalLoggedHours || 0),
    submitted: acc.submitted + Number(project.submittedHours || 0),
    approved: acc.approved + Number(project.approvedHours || 0),
    rejected: acc.rejected + Number(project.rejectedHours || 0),
  }), { planned: 0, logged: 0, submitted: 0, approved: 0, rejected: 0 }), [projects]);

  const updatePlannedHours = async (projectId, userId, value) => {
    if (!canPlan) return;
    const parsed = String(value ?? '').trim();
    if (parsed !== '' && (Number.isNaN(Number(parsed)) || Number(parsed) < 0)) {
      setError('Planned hours must be a positive number');
      return;
    }
    try {
      await projectAPI.updatePlannedHours(projectId, userId, parsed, selectedOption.year, selectedOption.month);
      await loadDashboard();
      setError('');
    } catch (err) {
      setError('Failed to update planned hours');
    }
  };

  if (!canView) {
    return <div className="page-container"><div className="error-message">You do not have permission to access this page.</div></div>;
  }

  return (
    <div className="page-container admin-page">
      <div className="header-bar">
        <div>
          <ScreenTitle title="Project Hours" icon="chart" eyebrow="CAPACITY & DELIVERY" />
          <p className="page-subtitle">Planned, logged, submitted, and approved hours by project.</p>
        </div>
        <div className="month-select-row">
          <label htmlFor="project-hours-month">Month</label>
          <select id="project-hours-month" value={selectedPeriod} onChange={(event) => setSelectedPeriod(event.target.value)}>
            {options.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}

      <div className="timesheet-summary-grid">
        <div className="summary-tile"><span>Planned</span><strong>{hours(totals.planned)}</strong></div>
        <div className="summary-tile"><span>Logged</span><strong>{hours(totals.logged)}</strong></div>
        <div className="summary-tile"><span>Submitted</span><strong>{hours(totals.submitted)}</strong></div>
        <div className="summary-tile"><span>Approved</span><strong>{hours(totals.approved)}</strong></div>
      </div>

      {loading ? (
        <div className="loading-panel"><LoadingIndicator label="Loading project hours..." /></div>
      ) : projects.length === 0 ? (
        <div className="empty-state"><p>No projects to show.</p></div>
      ) : (
        <div className="project-hours-list">
          {projects.map((project) => (
            <section className="admin-panel" key={project.projectId}>
              <div className="panel-heading">
                <div>
                  <h2>{project.projectCode} - {project.projectName}</h2>
                  <p>Project Manager: {project.projectManagerName || '-'}</p>
                </div>
                <div className="project-hours-totals">
                  <span>Planned {hours(project.plannedHours)}</span>
                  <span>Logged {hours(project.totalLoggedHours)}</span>
                  <span>Approved {hours(project.approvedHours)}</span>
                </div>
              </div>
              <div className="table-container">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Employee</th>
                      <th>Designation</th>
                      <th>Planned</th>
                      <th>Logged</th>
                      <th>Submitted</th>
                      <th>Approved</th>
                      <th>Rejected</th>
                      <th>Status</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(project.employees || []).map((employee) => {
                      const draftKey = `${project.projectId}:${employee.userId}`;
                      return (
                      <tr key={employee.userId}>
                        <td>
                          <strong>{employee.userName}</strong>
                          <div className="table-subtext">{employee.email}</div>
                          {!employee.assignmentActive && <div className="table-subtext">Historical hours only</div>}
                        </td>
                        <td>{employee.jobTitle || '-'}</td>
                        <td>
                          <input
                            className="table-hours-input"
                            disabled={!canPlan}
                            type="number"
                            min="0"
                            step="0.5"
                            value={plannedHourDrafts[draftKey] ?? ''}
                            onChange={(event) => setPlannedHourDrafts({ ...plannedHourDrafts, [draftKey]: event.target.value })}
                            onBlur={(event) => updatePlannedHours(project.projectId, employee.userId, event.target.value)}
                            aria-label={`Planned hours for ${employee.userName}`}
                          />
                        </td>
                        <td>{hours(employee.totalLoggedHours)}</td>
                        <td>{hours(employee.submittedHours)}</td>
                        <td>{hours(employee.approvedHours)}</td>
                        <td>{hours(employee.rejectedHours)}</td>
                        <td><span className={`status-badge status-${String(employee.status || 'draft').toLowerCase()}`}>{employee.status || 'DRAFT'}</span></td>
                        <td>
                          {employee.timesheetId ? (
                            <button
                              className="button button-small button-secondary"
                              type="button"
                              onClick={() => navigate(`/timesheet/${employee.timesheetId}?projectId=${project.projectId}`)}
                            >
                              Open
                            </button>
                          ) : '-'}
                        </td>
                      </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </section>
          ))}
        </div>
      )}
    </div>
  );
}
