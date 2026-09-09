import React, { useEffect, useMemo, useState } from 'react';
import { format, startOfMonth, subMonths } from 'date-fns';
import { projectAPI, userAPI } from '../api';
import { useAuth } from '../AuthContext';
import { LoadingIndicator } from '../components/Hourglass';
import '../styles.css';

const projectStatuses = ['DRAFT', 'ACTIVE', 'ON_HOLD', 'COMPLETED', 'ARCHIVED'];
const projectTabs = [
  { id: 'overview', label: 'Overview' },
  { id: 'team', label: 'Team' },
  { id: 'hours', label: 'Hours' },
  { id: 'settings', label: 'Project Details' },
];

const emptyForm = {
  code: '',
  name: '',
  description: '',
  status: 'ACTIVE',
  totalAllocatedHours: '',
  projectManagerId: '',
  projectManagerHoursApproverId: '',
  isActive: true,
};

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

function dateText(value) {
  return value ? format(new Date(`${value}T00:00:00`), 'MMM dd, yyyy') : '-';
}

function labelize(value) {
  return String(value || '').replaceAll('_', ' ');
}

function statusClass(value) {
  return `status-badge status-${String(value || 'draft').toLowerCase()}`;
}

function setupWarnings(project) {
  const warnings = [];
  if (!project?.projectManagerId) warnings.push('No PM');
  if (!project?.projectManagerHoursApproverId) warnings.push('No PM approver');
  if (!(project?.assignments || []).some((assignment) => assignment.isActive)) warnings.push('No active team');
  if (project?.projectManagerId && project.projectManagerId === project.projectManagerHoursApproverId) warnings.push('PM self-approval');
  return warnings;
}

export default function ProjectManagement() {
  const { user } = useAuth();
  const options = useMemo(monthOptions, []);
  const [projects, setProjects] = useState([]);
  const [dashboardProjects, setDashboardProjects] = useState([]);
  const [users, setUsers] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [selectedProjectId, setSelectedProjectId] = useState(null);
  const [isCreatingProject, setIsCreatingProject] = useState(false);
  const [selectedPeriod, setSelectedPeriod] = useState(options[0].value);
  const [projectSearch, setProjectSearch] = useState('');
  const [projectFilter, setProjectFilter] = useState('ACTIVE');
  const [activeTab, setActiveTab] = useState('overview');
  const [assignDraft, setAssignDraft] = useState('');
  const [assignStartDate, setAssignStartDate] = useState('');
  const [assignEndDate, setAssignEndDate] = useState('');
  const [assignBillRate, setAssignBillRate] = useState('');
  const [assignmentDateDrafts, setAssignmentDateDrafts] = useState({});
  const [plannedHourDrafts, setPlannedHourDrafts] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const canManage = ['ADMIN', 'SUPER_ADMIN'].includes(user?.role);
  const selectedOption = options.find((option) => option.value === selectedPeriod) || options[0];
  const activeUsers = useMemo(() => users.filter((item) => item.isActive), [users]);
  const selectedProject = isCreatingProject ? null : projects.find((project) => project.id === selectedProjectId) || null;
  const selectedDashboard = dashboardProjects.find((project) => project.projectId === selectedProject?.id);
  const activeAssignments = (selectedProject?.assignments || []).filter((assignment) => assignment.isActive);
  const historicalAssignments = (selectedProject?.assignments || []).filter((assignment) => !assignment.isActive);

  useEffect(() => {
    if (!canManage) return;
    loadData();
  }, [canManage, selectedPeriod]);

  const seedDrafts = (loadedProjects, loadedDashboard) => {
    const hourDrafts = {};
    loadedDashboard.forEach((project) => {
      (project.employees || []).forEach((employee) => {
        hourDrafts[`${project.projectId}:${employee.userId}`] = employee.plannedHours ?? '';
      });
    });
    const dateDrafts = {};
    loadedProjects.forEach((project) => {
      (project.assignments || []).forEach((assignment) => {
        dateDrafts[`${project.id}:${assignment.userId}`] = {
          startDate: assignment.startDate || '',
          endDate: assignment.endDate || '',
          billRate: assignment.billRate ?? '',
        };
      });
    });
    setPlannedHourDrafts(hourDrafts);
    setAssignmentDateDrafts(dateDrafts);
  };

  const loadData = async () => {
    setLoading(true);
    try {
      const [projectRes, userRes, dashboardRes] = await Promise.all([
        projectAPI.getProjects(),
        userAPI.getAllUsersAsAdmin(),
        projectAPI.getHoursDashboard(selectedOption.year, selectedOption.month),
      ]);
      const loadedProjects = projectRes.data || [];
      const loadedDashboard = dashboardRes.data || [];
      setProjects(loadedProjects);
      setUsers(userRes.data || []);
      setDashboardProjects(loadedDashboard);
      seedDrafts(loadedProjects, loadedDashboard);
      if (selectedProjectId && !loadedProjects.some((project) => project.id === selectedProjectId)) {
        setSelectedProjectId(null);
        setProjectSearch('');
      }
      setError('');
    } catch (err) {
      setError('Failed to load project workspace');
    } finally {
      setLoading(false);
    }
  };

  const selectProject = (project) => {
    setSelectedProjectId(project?.id || null);
    setIsCreatingProject(false);
    setActiveTab('overview');
    setProjectSearch(project ? `${project.code} - ${project.name}` : '');
    setAssignDraft('');
    setAssignStartDate('');
    setAssignEndDate('');
    setAssignBillRate('');
    setForm({
      code: project?.code || '',
      name: project?.name || '',
      description: project?.description || '',
      status: project?.status || (project?.isActive ? 'ACTIVE' : 'ARCHIVED'),
      totalAllocatedHours: project?.totalAllocatedHours ?? '',
      projectManagerId: project?.projectManagerId || '',
      projectManagerHoursApproverId: project?.projectManagerHoursApproverId || '',
      isActive: project?.isActive ?? true,
    });
  };

  const startNewProject = () => {
    setSelectedProjectId(null);
    setIsCreatingProject(true);
    setActiveTab('settings');
    setProjectSearch('');
    setAssignDraft('');
    setAssignStartDate('');
    setAssignEndDate('');
    setAssignBillRate('');
    setForm(emptyForm);
  };

  const filteredProjects = projects.filter((project) => {
    if (projectFilter === 'ACTIVE' && project.status !== 'ACTIVE') return false;
    if (projectFilter === 'DRAFT' && project.status !== 'DRAFT') return false;
    if (projectFilter === 'ON_HOLD' && project.status !== 'ON_HOLD') return false;
    if (projectFilter === 'COMPLETED' && project.status !== 'COMPLETED') return false;
    if (projectFilter === 'ARCHIVED' && project.status !== 'ARCHIVED') return false;
    if (projectFilter === 'managed-by-me' && project.projectManagerId !== user?.id) return false;
    if (projectFilter === 'needs-setup' && setupWarnings(project).length === 0) return false;
    if (projectFilter === 'no-team' && (project.assignments || []).some((assignment) => assignment.isActive)) return false;
    if (projectFilter === 'over-plan') {
      const dashboard = dashboardProjects.find((item) => item.projectId === project.id);
      const budget = Number(project.totalAllocatedHours || dashboard?.plannedHours || 0);
      if (!budget || Number(dashboard?.totalLoggedHours || 0) <= budget) return false;
    }
    return true;
  });
  const searchText = projectSearch.trim().toLowerCase();
  const projectSuggestions = searchText
    ? filteredProjects
        .filter((project) => `${project.name} ${project.code}`.toLowerCase().includes(searchText))
        .slice(0, 8)
    : [];

  const handleProjectSearch = (value) => {
    setProjectSearch(value);
    if (selectedProjectId) {
      setSelectedProjectId(null);
      setIsCreatingProject(false);
    }
  };

  const saveProject = async (event) => {
    event.preventDefault();
    if (!form.projectManagerId || !form.projectManagerHoursApproverId) {
      setError('Project Manager and PM Hours Approver are required');
      return;
    }
    if (String(form.projectManagerId) === String(form.projectManagerHoursApproverId)) {
      setError('Project Manager cannot approve their own hours');
      return;
    }
    const allocated = String(form.totalAllocatedHours ?? '').trim();
    if (allocated !== '' && (Number.isNaN(Number(allocated)) || Number(allocated) < 0)) {
      setError('Total allocated hours must be zero or more');
      return;
    }
    const payload = {
      ...form,
      isActive: form.status === 'ACTIVE',
      totalAllocatedHours: allocated === '' ? null : Number(allocated),
      projectManagerId: Number(form.projectManagerId),
      projectManagerHoursApproverId: Number(form.projectManagerHoursApproverId),
    };
    try {
      const response = selectedProject?.id
        ? await projectAPI.updateProject(selectedProjectId, payload)
        : await projectAPI.createProject(payload);
      await loadData();
      setSelectedProjectId(response.data?.id || selectedProjectId);
      setIsCreatingProject(false);
      setError('');
    } catch (err) {
      setError('Failed to save project. Check required fields and project code uniqueness.');
    }
  };

  const assignEmployee = async () => {
    if (!selectedProject?.id || !assignDraft) return;
    if (!assignStartDate || !assignEndDate || assignBillRate === '') {
      setError('Start date, end date, and bill rate are required to assign an employee');
      return;
    }
    if (assignStartDate && assignEndDate && assignStartDate > assignEndDate) {
      setError('Employee project start date cannot be after end date');
      return;
    }
    if (Number.isNaN(Number(assignBillRate)) || Number(assignBillRate) < 0) {
      setError('Bill rate must be zero or more');
      return;
    }
    try {
      await projectAPI.assignEmployee(selectedProject.id, assignDraft, assignStartDate, assignEndDate, assignBillRate);
      setAssignDraft('');
      setAssignStartDate('');
      setAssignEndDate('');
      setAssignBillRate('');
      await loadData();
      setError('');
    } catch (err) {
      setError('Failed to assign employee');
    }
  };

  const updateAssignmentDraft = (assignment, field, value) => {
    const key = `${selectedProject.id}:${assignment.userId}`;
    setAssignmentDateDrafts({
      ...assignmentDateDrafts,
      [key]: { ...(assignmentDateDrafts[key] || {}), [field]: value },
    });
  };

  const updateAssignmentDates = async (assignment) => {
    const key = `${selectedProject.id}:${assignment.userId}`;
    const draft = assignmentDateDrafts[key] || {};
    if (!draft.startDate || !draft.endDate || draft.billRate === '') {
      setError('Start date, end date, and bill rate are required');
      return;
    }
    if (draft.startDate && draft.endDate && draft.startDate > draft.endDate) {
      setError('Employee project start date cannot be after end date');
      return;
    }
    if (Number.isNaN(Number(draft.billRate)) || Number(draft.billRate) < 0) {
      setError('Bill rate must be zero or more');
      return;
    }
    try {
      await projectAPI.updateAssignmentDates(selectedProject.id, assignment.userId, draft.startDate, draft.endDate, draft.billRate);
      await loadData();
      setError('');
    } catch (err) {
      setError('Failed to update assignment dates');
    }
  };

  const endAssignment = async (userId) => {
    if (!selectedProject?.id) return;
    try {
      await projectAPI.removeEmployee(selectedProject.id, userId);
      await loadData();
    } catch (err) {
      setError('Failed to end assignment');
    }
  };

  const updatePlannedHours = async (projectId, userId, value) => {
    const parsed = String(value ?? '').trim();
    if (parsed !== '' && (Number.isNaN(Number(parsed)) || Number(parsed) < 0)) {
      setError('Planned hours must be a positive number');
      return;
    }
    try {
      await projectAPI.updatePlannedHours(projectId, userId, parsed, selectedOption.year, selectedOption.month);
      await loadData();
      setError('');
    } catch (err) {
      setError('Failed to update planned hours');
    }
  };

  const totals = selectedDashboard || {};
  const warnings = selectedProject ? setupWarnings(selectedProject) : [];
  const planned = Number(totals.plannedHours || 0);
  const budget = Number(selectedProject?.totalAllocatedHours || planned || 0);
  const logged = Number(totals.totalLoggedHours || 0);
  const approved = Number(totals.approvedHours || 0);
  const unassignedUsers = activeUsers.filter((item) =>
    !(selectedProject?.assignments || []).some((assignment) => assignment.userId === item.id && assignment.isActive)
  );

  if (!canManage) {
    return <div className="page-container"><div className="error-message">You do not have permission to access this page.</div></div>;
  }

  if (loading) {
    return <div className="page-container"><div className="loading-panel"><LoadingIndicator label="Loading projects..." /></div></div>;
  }

  return (
    <div className="page-container admin-page projects-workspace-page">
      <div className="header-bar project-command-header">
        <div>
          <h1>Project Control</h1>
          <p className="page-subtitle">Setup, staffing, hours, approvals.</p>
        </div>
        <button className="button button-secondary" type="button" onClick={startNewProject}>New Project</button>
      </div>

      {error && <div className="error-message">{error}</div>}

      <section className="admin-panel project-selector-panel">
        <div className="project-search-field">
          <label htmlFor="project-search">Project Name</label>
          <input id="project-search" placeholder="Type project name" value={projectSearch} onChange={(event) => handleProjectSearch(event.target.value)} />
          {projectSuggestions.length > 0 && !isCreatingProject && (
            <div className="project-suggestion-list">
              {projectSuggestions.map((project) => (
                <button key={project.id} type="button" onClick={() => selectProject(project)}>
                  <strong>{project.name}</strong>
                  <span>{project.code} · {labelize(project.status)}</span>
                </button>
              ))}
            </div>
          )}
          {searchText && projectSuggestions.length === 0 && !isCreatingProject && (
            <div className="project-suggestion-empty">No matching projects in this filter.</div>
          )}
        </div>
        <div className="month-select-row">
          <label htmlFor="project-plan-month">Month</label>
          <select id="project-plan-month" value={selectedPeriod} onChange={(event) => setSelectedPeriod(event.target.value)}>
            {options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
          </select>
        </div>
        <div className="month-select-row">
          <label htmlFor="project-filter">Filter</label>
          <select
            id="project-filter"
            value={projectFilter}
            onChange={(event) => {
              setProjectFilter(event.target.value);
              setSelectedProjectId(null);
              setProjectSearch('');
              setIsCreatingProject(false);
            }}
          >
            <option value="ACTIVE">Active</option>
            <option value="all">All</option>
            <option value="COMPLETED">Completed</option>
            <option value="ARCHIVED">Archived</option>
            <option value="ON_HOLD">On Hold</option>
            <option value="DRAFT">Draft</option>
            <option value="managed-by-me">Managed by me</option>
            <option value="needs-setup">Needs setup</option>
            <option value="no-team">No team</option>
            <option value="over-plan">Over plan</option>
          </select>
        </div>
      </section>

      {!selectedProject && !isCreatingProject ? (
        <section className="admin-panel project-empty-panel">
          <h2>Select a project</h2>
          <p>Type a project name above, then choose one from the suggestions. Use the status filter to view completed or archived projects.</p>
        </section>
      ) : (
      <form className="admin-panel form project-identity-panel" onSubmit={saveProject}>
        <div className="panel-heading">
          <div>
            <h2>{selectedProject ? `${selectedProject.code} - ${selectedProject.name}` : 'New Project'}</h2>
            <p>{selectedProject ? 'Routing and controls' : 'PM and approver required'}</p>
          </div>
          <span className={statusClass(selectedProject?.status || form.status)}>{labelize(selectedProject?.status || form.status)}</span>
        </div>

        <div className="project-tabs">
          {projectTabs.map((tab) => (
            <button
              className={`project-tab ${activeTab === tab.id ? 'active' : ''}`}
              disabled={isCreatingProject && tab.id !== 'settings'}
              key={tab.id}
              type="button"
              onClick={() => setActiveTab(tab.id)}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {activeTab === 'overview' && selectedProject && (
          <>
            <div className="project-health-strip">
              <div><span>Budget</span><strong>{hours(budget)}</strong></div>
              <div><span>Planned</span><strong>{hours(planned)}</strong></div>
              <div><span>Logged</span><strong>{hours(logged)}</strong></div>
              <div><span>Approved</span><strong>{hours(approved)}</strong></div>
              <div><span>Alerts</span><strong>{warnings.length}</strong></div>
            </div>
            <div className="project-routing-grid">
              <div><span>Employee approvals</span><strong>{selectedProject.projectManagerName || '-'}</strong></div>
              <div><span>PM hours approval</span><strong>{selectedProject.projectManagerHoursApproverName || '-'}</strong></div>
              <div><span>Team</span><strong>{activeAssignments.length} active / {historicalAssignments.length} ended</strong></div>
              <div><span>Setup</span><strong>{warnings.length ? warnings.join(', ') : 'Ready'}</strong></div>
            </div>
          </>
        )}

        {activeTab === 'team' && selectedProject && (
          <>
            <div className="assign-employee-panel">
              <label className="compact-input-field">
                <span>Employee</span>
                <select
                  required
                  value={assignDraft}
                  onChange={(event) => {
                    const selectedUser = activeUsers.find((item) => String(item.id) === event.target.value);
                    setAssignDraft(event.target.value);
                    setAssignBillRate(selectedUser?.effectiveHourlyRate ?? selectedUser?.hourlyRate ?? assignBillRate);
                  }}
                >
                  <option value="">Select employee</option>
                  {unassignedUsers.map((item) => <option key={item.id} value={item.id}>{item.firstName} {item.lastName}</option>)}
                </select>
              </label>
              <label className="compact-input-field">
                <span>Start date</span>
                <input required type="date" value={assignStartDate} onChange={(event) => setAssignStartDate(event.target.value)} />
              </label>
              <label className="compact-input-field">
                <span>End date</span>
                <input required type="date" value={assignEndDate} onChange={(event) => setAssignEndDate(event.target.value)} />
              </label>
              <label className="compact-input-field">
                <span>Bill rate</span>
                <input required type="number" min="0" step="0.01" value={assignBillRate} onChange={(event) => setAssignBillRate(event.target.value)} placeholder="0.00" />
              </label>
              <button className="button button-small" type="button" onClick={assignEmployee} disabled={!assignDraft || !assignStartDate || !assignEndDate || assignBillRate === ''}>Assign</button>
            </div>
            <div className="table-container">
              <table className="data-table">
                <thead><tr><th>Employee</th><th>Designation</th><th>Start</th><th>End</th><th>Bill Rate</th><th>Status</th><th>Action</th></tr></thead>
                <tbody>
                  {(selectedProject.assignments || []).map((assignment) => {
                    const key = `${selectedProject.id}:${assignment.userId}`;
                    const draft = assignmentDateDrafts[key] || {};
                    return (
                      <tr key={assignment.id || assignment.userId}>
                        <td><strong>{assignment.userName}</strong><div className="table-subtext">{assignment.email}</div></td>
                        <td>{assignment.jobTitle || '-'}</td>
                        <td><input className="table-date-input" type="date" value={draft.startDate || ''} onChange={(event) => updateAssignmentDraft(assignment, 'startDate', event.target.value)} /></td>
                        <td><input className="table-date-input" type="date" value={draft.endDate || ''} onChange={(event) => updateAssignmentDraft(assignment, 'endDate', event.target.value)} /></td>
                        <td><input className="table-rate-input" type="number" min="0" step="0.01" value={draft.billRate ?? ''} onChange={(event) => updateAssignmentDraft(assignment, 'billRate', event.target.value)} /></td>
                        <td><span className={assignment.isActive ? 'status-badge status-approved' : 'status-badge status-locked'}>{assignment.isActive ? 'Active' : 'Ended'}</span></td>
                        <td>
                          <div className="row-actions">
                            <button className="button button-small button-secondary" type="button" onClick={() => updateAssignmentDates(assignment)}>Save</button>
                            {assignment.isActive && <button className="button button-small button-secondary" type="button" onClick={() => endAssignment(assignment.userId)}>End</button>}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                  {(selectedProject.assignments || []).length === 0 && <tr><td colSpan="7" className="text-center">No team assignments yet.</td></tr>}
                </tbody>
              </table>
            </div>
          </>
        )}

        {activeTab === 'hours' && selectedProject && (
          <div className="table-container">
            <table className="data-table">
              <thead><tr><th>Employee</th><th>Dates</th><th>Plan</th><th>Logged</th><th>Submitted</th><th>Approved</th><th>Status</th><th>Action</th></tr></thead>
              <tbody>
                {(selectedDashboard?.employees || []).map((employee) => {
                  const draftKey = `${selectedProject.id}:${employee.userId}`;
                  return (
                    <tr key={employee.userId}>
                      <td><strong>{employee.userName}</strong><div className="table-subtext">{employee.email}</div></td>
                      <td><strong>{dateText(employee.assignmentStartDate)}</strong><div className="table-subtext">to {dateText(employee.assignmentEndDate)}</div></td>
                      <td>
                        <input
                          className="table-hours-input"
                          type="number"
                          min="0"
                          step="0.5"
                          value={plannedHourDrafts[draftKey] ?? ''}
                          onChange={(event) => setPlannedHourDrafts({ ...plannedHourDrafts, [draftKey]: event.target.value })}
                          onBlur={(event) => updatePlannedHours(selectedProject.id, employee.userId, event.target.value)}
                          aria-label={`Planned hours for ${employee.userName}`}
                        />
                      </td>
                      <td>{hours(employee.totalLoggedHours)}</td>
                      <td>{hours(employee.submittedHours)}</td>
                      <td>{hours(employee.approvedHours)}</td>
                      <td><span className={statusClass(employee.status)}>{labelize(employee.status || 'DRAFT')}</span></td>
                      <td>{employee.timesheetId && <a className="button button-small button-secondary" href={`/timesheet/${employee.timesheetId}?projectId=${selectedProject.id}`}>Open</a>}</td>
                    </tr>
                  );
                })}
                {(selectedDashboard?.employees || []).length === 0 && <tr><td colSpan="8" className="text-center">No hours for {selectedOption.label}.</td></tr>}
              </tbody>
            </table>
          </div>
        )}

        {activeTab === 'settings' && (
          <>
            <div className="form-row">
              <div className="form-group">
                <label>Project Code</label>
                <input value={form.code} onChange={(event) => setForm({ ...form, code: event.target.value })} required />
              </div>
              <div className="form-group">
                <label>Project Name</label>
                <input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} required />
              </div>
              <div className="form-group">
                <label>Status</label>
                <select required value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value, isActive: event.target.value === 'ACTIVE' })}>
                  {projectStatuses.map((status) => <option key={status} value={status}>{labelize(status)}</option>)}
                </select>
              </div>
              <div className="form-group">
                <label>Total Allocated Hours</label>
                <input type="number" min="0" step="0.5" value={form.totalAllocatedHours} onChange={(event) => setForm({ ...form, totalAllocatedHours: event.target.value })} />
              </div>
            </div>
            <div className="form-row">
              <div className="form-group">
                <label>Project Manager</label>
                <select required value={form.projectManagerId} onChange={(event) => setForm({ ...form, projectManagerId: event.target.value })}>
                  <option value="">Select PM</option>
                  {activeUsers.map((item) => <option key={item.id} value={item.id}>{item.firstName} {item.lastName}</option>)}
                </select>
              </div>
              <div className="form-group">
                <label>PM Hours Approver</label>
                <select required value={form.projectManagerHoursApproverId} onChange={(event) => setForm({ ...form, projectManagerHoursApproverId: event.target.value })}>
                  <option value="">Select approver</option>
                  {activeUsers.map((item) => <option key={item.id} value={item.id}>{item.firstName} {item.lastName}</option>)}
                </select>
              </div>
            </div>
            <div className="form-group">
              <label>Description</label>
              <textarea value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} />
            </div>
            <div className="compact-actions">
              <button className="button button-primary" type="submit">{selectedProject ? 'Save Project' : 'Create Project'}</button>
            </div>
          </>
        )}

      </form>
      )}
    </div>
  );
}
