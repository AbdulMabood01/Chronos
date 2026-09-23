import ValidationMessage from '../components/ValidationMessage';
import { ProjectHealthCard, ProjectHealthOverview, useProjectHealth } from '../components/ProjectHealth';
import ScreenTitle from '../components/ScreenTitle';
import Icon from '../components/Icon';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { format, startOfMonth, subMonths } from 'date-fns';
import { projectAPI, userAPI } from '../api';
import { useAuth } from '../AuthContext';
import { LoadingIndicator } from '../components/Hourglass';
import '../styles.css';
import './ProjectManagement.css';

const projectStatuses = ['DRAFT', 'ACTIVE', 'ON_HOLD', 'COMPLETED', 'ARCHIVED'];
const projectTabs = [
  { id: 'overview', label: 'Overview', icon: 'grid' },
  { id: 'team', label: 'Team', icon: 'users' },
  { id: 'hours', label: 'Hours', icon: 'clock' },
  { id: 'settings', label: 'Project Details', icon: 'settings' },
];

const emptyForm = {
  code: '',
  name: '',
  description: '',
  status: 'ACTIVE',
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

function percent(value, total) {
  if (!total) return 0;
  return Math.max(0, Math.min(100, (Number(value || 0) / total) * 100));
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
  const [assignHours, setAssignHours] = useState('');
  const [replacementManagerId, setReplacementManagerId] = useState('');
  const [offboarding, setOffboarding] = useState(null);
  const offboardingDialog = useRef(null);

  useEffect(() => {
    if (!offboarding) return;
    const dialog = offboardingDialog.current;
    const previousFocus = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    dialog?.showModal?.();
    document.body.style.overflow = 'hidden';
    return () => {
      dialog?.close?.();
      document.body.style.overflow = previousOverflow;
      previousFocus?.focus?.();
    };
  }, [offboarding]);
  const [savingAssignment, setSavingAssignment] = useState(false);
  const [assignmentDateDrafts, setAssignmentDateDrafts] = useState({});
  const [plannedHourDrafts, setPlannedHourDrafts] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const favoritesKey = 'chronos:project-favorites:' + user?.id;
  const [favorites, setFavorites] = useState([]);

  const canView = user?.canReviewProjects || ['ADMIN', 'SUPER_ADMIN'].includes(user?.role);
  const canManage = user?.role === 'ADMIN';
  const healthState = useProjectHealth(canView, projects);
  const selectedOption = options.find((option) => option.value === selectedPeriod) || options[0];
  const activeUsers = useMemo(() => users.filter((item) => item.isActive), [users]);
  const eligibleReviewers = activeUsers.filter((item) => item.role !== 'SUPER_ADMIN');
  const selectedProject = isCreatingProject ? null : projects.find((project) => project.id === selectedProjectId) || null;
  const selectedDashboard = dashboardProjects.find((project) => project.projectId === selectedProject?.id);
  const dashboardEmployeesByUserId = useMemo(() => {
    const rows = {};
    (selectedDashboard?.employees || []).forEach((employee) => {
      rows[employee.userId] = employee;
    });
    return rows;
  }, [selectedDashboard]);
  const activeAssignments = (selectedProject?.assignments || []).filter((assignment) => assignment.isActive);
  const historicalAssignments = (selectedProject?.assignments || []).filter((assignment) => !assignment.isActive);

  useEffect(() => {
    if (!canView) return;
    loadData();
  }, [canView, selectedPeriod]);

  useEffect(() => {
    try {
      const saved = JSON.parse(localStorage.getItem(favoritesKey) || '[]');
      setFavorites(Array.isArray(saved) ? saved.map(String) : []);
    } catch {
      setFavorites([]);
    }
  }, [favoritesKey]);

  const seedDrafts = (loadedProjects, loadedDashboard) => {
    const hourDrafts = {};
    loadedProjects.forEach((project) => {
      (project.assignments || []).forEach((resource) => {
        const savedHours = loadedDashboard.find((row) => row.projectId === project.id)?.employees?.find((row) => row.userId === resource.userId)?.plannedHours;
        hourDrafts[`${project.id}:${resource.userId}`] = resource.plannedHours ?? savedHours ?? '';
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
        canManage ? userAPI.getAllUsers() : Promise.resolve({ data: [] }),
        projectAPI.getHoursDashboard(selectedOption.year, selectedOption.month),
      ]);
      const loadedProjects = projectRes.data || [];
      const loadedDashboard = dashboardRes.data || [];
      setProjects(loadedProjects);
      setUsers(userRes.data || []);
      setDashboardProjects(loadedDashboard);
      const linkedId = new URLSearchParams(window.location.search).get('projectId');
      if (!selectedProjectId && linkedId) {
        const linkedProject = loadedProjects.find(project => String(project.id) === linkedId);
        if (linkedProject) selectProject(linkedProject);
      }
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
    const managerAssignment = (project?.assignments || []).find((assignment) => assignment.userId === project?.projectManagerId);
    setSelectedProjectId(project?.id || null);
    setIsCreatingProject(false);
    setActiveTab('overview');
    setProjectSearch(project ? `${project.code} - ${project.name}` : '');
    setAssignDraft('');
    setAssignStartDate('');
    setAssignEndDate('');
    setAssignBillRate('');
    setAssignHours('');
    setOffboarding(null);
    setForm({
      code: project?.code || '',
      name: project?.name || '',
      description: project?.description || '',
      status: project?.status || (project?.isActive ? 'ACTIVE' : 'ARCHIVED'),
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
    setAssignHours('');
    setOffboarding(null);
    setForm(emptyForm);
  };

  const cancelProjectEdit = () => {
    if (selectedProject) {
      selectProject(selectedProject);
    } else {
      setIsCreatingProject(false);
      setSelectedProjectId(null);
      setProjectSearch('');
      setForm(emptyForm);
      setActiveTab('overview');
    }
    setError('');
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
      const budget = (project.assignments || []).reduce((total, resource) => total + Number(resource.plannedHours ?? dashboard?.employees?.find((row) => row.userId === resource.userId)?.plannedHours ?? 0), 0);
      if (!budget || Number(dashboard?.totalLoggedHours || 0) <= budget) return false;
    }
    return true;
  });
  const searchText = projectSearch.trim().toLowerCase();
  const visibleProjects = filteredProjects.filter((project) =>
    !searchText || `${project.name} ${project.code}`.toLowerCase().includes(searchText)
  ).sort((a, b) => Number(favorites.includes(String(b.id))) - Number(favorites.includes(String(a.id))) || String(a.code).localeCompare(String(b.code)));

  const toggleFavorite = (projectId) => {
    const id = String(projectId);
    const next = favorites.includes(id) ? favorites.filter((item) => item !== id) : [...favorites, id];
    setFavorites(next);
    try {
      localStorage.setItem(favoritesKey, JSON.stringify(next));
    } catch {
      setError('Your browser could not save favorites.');
    }
  };

  const handleProjectSearch = (value) => {
    setProjectSearch(value);
    if (selectedProjectId) {
      setSelectedProjectId(null);
      setIsCreatingProject(false);
    }
  };

  const saveProject = async (event) => {
    event.preventDefault();
    if (!canManage) return;
    if (!form.projectManagerId || !form.projectManagerHoursApproverId) {
      setError('Project Manager and PM Hours Approver are required');
      return;
    }
    const payload = {
      ...form,
      isActive: form.status === 'ACTIVE',
      projectManagerId: Number(form.projectManagerId),
      projectManagerHoursApproverId: Number(form.projectManagerHoursApproverId),
    };
    try {
      const response = selectedProject?.id
        ? await projectAPI.updateProject(selectedProjectId, payload)
        : await projectAPI.createProject(payload);
      await loadData();
      const savedProject = response.data;
      if (!selectedProject?.id && savedProject?.id) {
        setProjects((current) => [...current.filter((project) => project.id !== savedProject.id), savedProject]);
        setProjectSearch(savedProject.code + ' - ' + savedProject.name);
        setActiveTab('team');
        setAssignDraft(String(payload.projectManagerId));
        setAssignBillRate('');
        setAssignStartDate('');
        setAssignEndDate('');
        setAssignHours('');
      }
      setSelectedProjectId(savedProject?.id || selectedProjectId);
      setIsCreatingProject(false);
      setError('');
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to save project. Check required fields and project code uniqueness.');
    }
  };

  const assignEmployee = async () => {
    if (!canManage) return;
    if (!selectedProject?.id || !assignDraft) return;
    if (!assignStartDate || !assignEndDate || assignBillRate === '' || !Number.isFinite(Number(assignHours)) || Number(assignHours) <= 0) {
      setError('Start date, end date, bill rate, and positive assigned hours are required to assign an employee');
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
      setSavingAssignment(true);
      await projectAPI.assignEmployee(selectedProject.id, assignDraft, assignStartDate, assignEndDate, assignBillRate, assignHours);
      setAssignDraft('');
      setAssignStartDate('');
      setAssignEndDate('');
      setAssignBillRate('');
    setAssignHours('');
    setOffboarding(null);
      await loadData();
      setError('');
    } catch (err) {
      setError('Failed to assign employee');
    } finally {
      setSavingAssignment(false);
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
    if (!canManage) return;
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
    if (!canManage) return;
    if (!selectedProject?.id) return;
    try {
      setSavingAssignment(true);
      await projectAPI.removeEmployee(selectedProject.id, userId, replacementManagerId || undefined);
      await loadData();
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to offboard team member');
    } finally {
      setSavingAssignment(false);
      setOffboarding(null);
    }
  };

  const updatePlannedHours = async (projectId, userId, value) => {
    if (!canManage) return;
    const parsed = String(value ?? '').trim();
    if (parsed !== '' && (Number.isNaN(Number(parsed)) || Number(parsed) < 0)) {
      setError('Planned hours must be a positive number');
      return;
    }
    try {
      await projectAPI.updatePlannedHours(projectId, userId, parsed);
      await loadData();
      setError('');
    } catch (err) {
      setError('Failed to update planned hours');
    }
  };

  const totals = selectedDashboard || {};
  const warnings = selectedProject ? setupWarnings(selectedProject) : [];

  const logged = Number(totals.totalLoggedHours || 0) + historicalAssignments.reduce((sum, resource) => sum + Number(resource.plannedHours || 0) - Number(dashboardEmployeesByUserId[resource.userId]?.totalLoggedHours || 0), 0);
  const submitted = Number(totals.submittedHours || 0);
  const frozenApprovedAdjustment = historicalAssignments.reduce((sum, resource) => sum + Number(resource.plannedHours || 0) - Number(dashboardEmployeesByUserId[resource.userId]?.approvedHours || 0), 0);
  const approved = Number(totals.approvedHours || 0) + frozenApprovedAdjustment;
  const resources = (selectedProject?.assignments || []).map((resource) => ({
    ...resource,
    plannedHours: resource.plannedHours ?? dashboardEmployeesByUserId[resource.userId]?.plannedHours ?? 0,
  }));
  const projectHours = resources.reduce((total, resource) => total + Number(resource.plannedHours || 0), 0);
  const rejected = Number(totals.rejectedHours || 0);
  const draft = Math.max(0, logged - approved - submitted - rejected);
  const remaining = Math.max(0, projectHours - logged);
  const chartTotal = Math.max(projectHours, logged);
  const chartSlices = [
    { label: 'Approved', value: approved, color: 'var(--pc-approved)' },
    { label: 'Submitted', value: submitted, color: 'var(--pc-submitted)' },
    { label: 'Rejected', value: rejected, color: 'var(--pc-rejected)' },
    { label: 'Draft', value: draft, color: 'var(--pc-draft)' },
    { label: 'Remaining', value: remaining, color: 'var(--border-color)' },
  ];
  let allocationEnd = 0;
  const allocationSegments = chartSlices.map((slice) => {
    const start = allocationEnd;
    allocationEnd += percent(slice.value, chartTotal) * 3.6;
    return `${slice.color} ${start}deg ${allocationEnd}deg`;
  });
  const unassignedUsers = activeUsers.filter((item) =>
    !(selectedProject?.assignments || []).some((assignment) => assignment.userId === item.id && assignment.isActive)
  );

  if (!canView) {
    return <div className="page-container highlighted-workspace"><div className="error-message">You do not have permission to access this page.</div></div>;
  }

  if (loading) {
    return <div className="page-container highlighted-workspace"><div className="loading-panel"><LoadingIndicator label="Loading projects..." /></div></div>;
  }

  return (
    <div className="page-container highlighted-workspace admin-page projects-workspace-page">
      <div className="header-bar project-command-header">
        <div>
          <ScreenTitle title="Project Control" icon="briefcase" eyebrow="PROJECT OPERATIONS" />
          <p className="page-subtitle">Keep your projects, people, and delivery plans in focus.</p>
        </div>
        {canManage && <button className="button button-primary" type="button" onClick={startNewProject}><span aria-hidden="true">+</span> New Project</button>}
      </div>

      <ValidationMessage message={error} onDismiss={() => setError('')} />
      {!selectedProject && !isCreatingProject && ['ADMIN', 'SUPER_ADMIN'].includes(user?.role) && <ProjectHealthOverview state={healthState} onOpen={id => selectProject(projects.find(project => project.id === id))} />}

      <section className="admin-panel project-selector-panel">
        <div className="project-search-field">
          <label htmlFor="project-search">Project Name</label>
          <div className="pc-search-input"><Icon name="search" size={18} /><input id="project-search" placeholder="Search by project name or code" autoComplete="off" value={projectSearch} onChange={(event) => handleProjectSearch(event.target.value)} />{projectSearch && <button className="pc-clear-search" type="button" aria-label="Clear project selection" onClick={() => selectProject(null)}><Icon name="close" size={16} /></button>}</div>

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
        <div className="month-select-row">
          <label htmlFor="project-period">Reporting month</label>
          <select id="project-period" value={selectedPeriod} onChange={(event) => setSelectedPeriod(event.target.value)}>
            {options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
          </select>
        </div>
      </section>

      {!selectedProject && !isCreatingProject ? (
        <section className="admin-panel pc-project-library">
          <div className="project-section-heading"><div><span>YOUR WORKSPACE</span><h2>Select a project</h2></div><p>{visibleProjects.length} project{visibleProjects.length === 1 ? '' : 's'}</p></div>
          <p className="pc-muted">Open a project to review progress, manage its team, and plan hours.</p>
          <div className="pc-project-grid">
            {visibleProjects.map((project) => {
              const isFavorite = favorites.includes(String(project.id));
              return (
                <article className="pc-project-card" key={project.id}>
                  <div className="pc-card-top">
                    <span className="pc-project-code">{project.code}</span>
                    <button type="button" className={`pc-favorite-button${isFavorite ? ' active' : ''}`} aria-label={isFavorite ? 'Remove from favorites' : 'Add to favorites'} aria-pressed={isFavorite} onClick={() => toggleFavorite(project.id)}>
                      <Icon name="heart" size={17} />
                    </button>
                    <span className={statusClass(project.status)}>{labelize(project.status)}</span>
                  </div>
                  <button className="pc-project-open" type="button" onClick={() => selectProject(project)}>
                    <strong>{project.name}</strong>
                    <span className="pc-muted">{project.projectManagerName || 'Project manager not assigned'}</span>
                    <span className="pc-card-footer"><span><Icon name="users" size={16} /> {(project.assignments || []).filter((item) => item.isActive).length} members</span><Icon name="arrow" size={18} /></span>
                  </button>
                </article>
              );
            })}
          </div>
          {!visibleProjects.length && <div className="pc-empty"><Icon name="briefcase" size={32} /><h3>No projects found</h3><p>Try another name or change the status filter.</p></div>}
        </section>
      ) : (
      <form className="admin-panel form project-identity-panel" onSubmit={(event) => { if (canManage) saveProject(event); else event.preventDefault(); }}>
        <div className="panel-heading">
          <div>
            <span className="pc-project-code">{selectedProject?.code || 'NEW WORKSPACE'}</span>
            <h2>{selectedProject ? selectedProject.name : 'New Project'}</h2>
            <p>{selectedProject ? selectedProject.description || 'Manage delivery, staffing, and approvals in one place.' : 'Start with the essentials. Add your team after creating the project.'}</p>
          </div>
          <span className={statusClass(selectedProject?.status || form.status)}>{labelize(selectedProject?.status || form.status)}</span>
        </div>

        {selectedProject && <ProjectHealthCard state={healthState} health={healthState.projects.find(project => project.projectId === selectedProject.id)} />}

        <div className="project-tabs">
          {projectTabs.map((tab) => (
            <button
              className={`project-tab ${activeTab === tab.id ? 'active' : ''}`}
              disabled={isCreatingProject && tab.id !== 'settings'}
              key={tab.id}
              type="button"
              aria-pressed={activeTab === tab.id}
              aria-controls="project-tab-content"
              onClick={() => setActiveTab(tab.id)}
            >
              <Icon name={tab.icon} size={17} />{tab.label}
            </button>
          ))}
        </div>

        <fieldset id="project-tab-content" disabled={!canManage} style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}>
        {activeTab === 'overview' && selectedProject && (
          <>
            <div className="project-section-heading pc-overview-heading"><div><span>PROJECT SNAPSHOT</span><h3>Every hour, accounted for</h3></div><p>{selectedOption.label}</p></div>
            <div className="project-mini-metrics pc-overview-metrics">
              <div><Icon name="calendar" size={18} /><span>Total project hours</span><strong>{projectHours == null ? 'Not set' : hours(projectHours)} {projectHours != null && <small>hrs</small>}</strong></div>
              <div><Icon name="clock" size={18} /><span>Logged hours</span><strong>{hours(logged)} <small>hrs</small></strong></div>
              <div><Icon name="check" size={18} /><span>Approved hours</span><strong>{hours(approved)} <small>hrs</small></strong></div>
              <div><Icon name="users" size={18} /><span>Active members</span><strong>{activeAssignments.length} <small>people</small></strong></div>
            </div>
            <div className="project-overview-hero">
              <div className="project-donut-card">
                <div className="project-donut-left">
                  <div className="project-status-donut" role="img" aria-label={`Total project hours: ${hours(projectHours)}, summed across all project resources`} style={{ background: `radial-gradient(circle at center, var(--surface-color) 0 56%, transparent 57%), conic-gradient(${chartTotal > 0 ? allocationSegments.join(', ') : 'var(--border-color) 0deg 360deg'})` }}>
                    <span>{hours(projectHours)}</span>
                    <small>total project hours</small>
                  </div>
                  <div className="project-chart-legend">
                    {chartSlices.map((slice) => <span key={slice.label}><i style={{ background: slice.color }} />{slice.label}<strong>{hours(slice.value)}</strong></span>)}
                  </div>
                </div>
                <aside className="project-insight-panel" aria-label="Project overview details">
                  <div className="project-insight-header">
                    <span>PROJECT HEALTH</span>
                    <strong>{warnings.length ? `${warnings.length} setup alert${warnings.length === 1 ? '' : 's'}` : 'Ready to run'}</strong>
                  </div>
                  <div className="project-insight-grid">
                    <div><span>Project Manager</span><strong>{selectedProject.projectManagerName || '-'}</strong></div>
                    <div><span>PM Hours Approver</span><strong>{selectedProject.projectManagerHoursApproverName || '-'}</strong></div>
                    <div><span>Active Team</span><strong>{activeAssignments.length}</strong></div>
                    <div><span>Ended Assignments</span><strong>{historicalAssignments.length}</strong></div>
                    <div><span>Total project hours</span><strong>{hours(projectHours)} hrs</strong></div>
                    <div><span>Resources with assigned hours</span><strong>{resources.filter((resource) => Number(resource.plannedHours) > 0).length}</strong></div>
                  </div>
                  <div className={warnings.length ? 'project-alert-card warning' : 'project-alert-card'}>
                    <span>{warnings.length ? 'Needs attention' : 'Setup status'}</span>
                    <strong>{warnings.length ? warnings.join(', ') : 'No setup issues found'}</strong>
                  </div>
                </aside>
              </div>
            </div>
            <div className="pc-plan-progress">
              <div><strong>Project hours used</strong><span>{Number(projectHours) > 0 ? `${Math.round(logged / projectHours * 100)}% of ${hours(projectHours)} total project hrs` : projectHours == null ? 'Total project hours not set' : '0.00 total project hrs'}</span></div>
              <div className="pc-progress-track" role="meter" aria-label="Selected month usage of total project hours" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent(logged, Number(projectHours))} aria-valuetext={`${hours(logged)} hours logged in ${selectedOption.label}; total project hours: ${projectHours == null ? 'not set' : hours(projectHours)}`}><span style={{ width: `${percent(logged, Number(projectHours))}%` }} /></div>
              <p>Total project hours are the sum of hours assigned to all project resources.</p>
            </div>
          </>
        )}

        {activeTab === 'team' && selectedProject && (
          <div className="project-tab-surface">
            <div className="project-section-heading">
              <div>
                <span>TEAM SETUP</span>
                <h3>Project members and billing windows</h3>
              </div>
              <p>{activeAssignments.length} active member{activeAssignments.length === 1 ? '' : 's'}</p>
            </div>
            {canManage && offboarding && <dialog ref={offboardingDialog} className="pc-offboarding-dialog" role="dialog" aria-modal="true" aria-label="Offboard team member" onCancel={(event) => { event.preventDefault(); if (!savingAssignment) setOffboarding(null); }}>
              <strong>Offboard {offboarding.userName}?</strong>
              <p>This ends their active membership immediately and freezes assigned hours at their total approved hours to date. Their history is retained.</p>
              {offboarding.pendingApproval && <p role="alert">Please approve or reject pending hours before offboarding this employee.</p>}
              {offboarding.userId === selectedProject.projectManagerId && <label>Replacement PM<select value={replacementManagerId} onChange={(event) => setReplacementManagerId(event.target.value)}><option value="">Select a replacement project manager</option>{Array.from(new Map([...activeAssignments, ...activeUsers.filter((member) => member.role === 'ADMIN').map((member) => ({ userId: member.id, userName: member.name || member.fullName || [member.firstName, member.lastName].filter(Boolean).join(' ') || member.email }))].map((member) => [member.userId, member])).values()).filter((member) => member.userId !== offboarding.userId).map((member) => <option key={member.userId} value={member.userId}>{member.userName}</option>)}</select><span>Choose an active team member or admin. This person becomes the project manager.</span></label>}
              <div className="compact-actions"><button type="button" className="button button-primary" disabled={savingAssignment || offboarding.pendingApproval || (offboarding.userId === selectedProject.projectManagerId && !replacementManagerId)} onClick={() => endAssignment(offboarding.userId)}>Confirm offboarding</button><button type="button" className="button button-secondary" disabled={savingAssignment} onClick={() => setOffboarding(null)}>Cancel</button></div>
            </dialog>}
            <div className="pc-team-summary"><span><Icon name="users" size={18} /><strong>{activeAssignments.length}</strong> active members</span><span><strong>{historicalAssignments.length}</strong> ended assignments</span><span>PM: <strong>{selectedProject.projectManagerName || 'Not assigned'}</strong></span></div>
            {canManage && <div className="pc-assignment-heading"><h4>Add a team member</h4><p>Enter their assignment dates, hourly bill rate, and total assigned hours.</p></div>}
            {canManage && <div className="assign-employee-panel">
              <label className="compact-input-field">
                <span>Employee</span>
                <select
                  required
                  value={assignDraft}
                  onChange={(event) => {
                    setAssignDraft(event.target.value);
                    setAssignBillRate('');
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
              <label className="compact-input-field"><span>Assigned hours</span><input required type="number" min="0.5" step="0.5" value={assignHours} onChange={(event) => setAssignHours(event.target.value)} placeholder="0.00" /></label>
              <button className="button button-small" type="button" onClick={assignEmployee} disabled={savingAssignment || !assignDraft || !assignStartDate || !assignEndDate || assignStartDate > assignEndDate || assignBillRate === '' || !Number.isFinite(Number(assignBillRate)) || Number(assignBillRate) < 0 || !Number.isFinite(Number(assignHours)) || Number(assignHours) <= 0}>Assign</button>
            </div>}
            <div className="table-container">
              <table className="data-table project-control-table">
                <thead><tr><th>Employee</th><th>Designation</th><th>Start</th><th>End</th><th>Bill Rate</th><th>Status</th>{canManage && <th>Actions</th>}</tr></thead>
                <tbody>
                  {(selectedProject.assignments || []).map((assignment) => {
                    const key = `${selectedProject.id}:${assignment.userId}`;
                    const draft = assignmentDateDrafts[key] || {};
                    return (
                      <tr key={assignment.id || assignment.userId}>
                        <td><strong>{assignment.userName}</strong><div className="table-subtext">{assignment.email}</div></td>
                        <td>{assignment.jobTitle || '-'}</td>
                        <td><input className="table-date-input" aria-label={`Start date for ${assignment.userName}`} type="date" value={draft.startDate || ''} onChange={(event) => updateAssignmentDraft(assignment, 'startDate', event.target.value)} onBlur={() => updateAssignmentDates(assignment)} /></td>
                        <td><input className="table-date-input" aria-label={`End date for ${assignment.userName}`} type="date" value={draft.endDate || ''} onChange={(event) => updateAssignmentDraft(assignment, 'endDate', event.target.value)} onBlur={() => updateAssignmentDates(assignment)} /></td>
                        <td><input className="table-rate-input" aria-label={`Bill rate for ${assignment.userName}`} type="number" min="0" step="0.01" value={draft.billRate ?? ''} onChange={(event) => updateAssignmentDraft(assignment, 'billRate', event.target.value)} onBlur={() => updateAssignmentDates(assignment)} /></td>
                        <td><span className={assignment.isActive ? 'status-badge status-approved' : 'status-badge status-locked'}>{assignment.isActive ? 'Active' : 'Ended'}</span></td>
                        {canManage && <td>{assignment.isActive && <button type="button" className="button button-small button-secondary" onClick={() => { setReplacementManagerId(''); setOffboarding(assignment); }}>Offboard {assignment.userName}</button>}</td>}
                      </tr>
                    );
                  })}
                  {(selectedProject.assignments || []).length === 0 && <tr><td colSpan={canManage ? 7 : 6} className="text-center">No team assignments yet.</td></tr>}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {activeTab === 'hours' && selectedProject && (
          <div className="project-tab-surface">
            <div className="project-section-heading">
              <div>
                <span>HOURS CONTROL</span>
                <h3>Project hours and approval status</h3>
              </div>
              <p>{selectedOption.label}</p>
            </div>
            <div className="project-mini-metrics">
              <div><span>Total project hours</span><strong>{projectHours == null ? 'Not set' : hours(projectHours)}</strong></div>
              <div><span>Logged</span><strong>{hours(logged)}</strong></div>
              <div><span>Submitted</span><strong>{hours(submitted)}</strong></div>
              <div><span>Approved</span><strong>{hours(approved)}</strong></div>
            </div>
            <div className="table-container">
            <table className="data-table project-control-table">
              <thead><tr><th>Employee</th><th>Dates</th><th>Assigned Project Hours</th><th>Logged</th><th>Submitted</th><th>Approved</th><th>Remaining Hours</th></tr></thead>
              <tbody>
                {(selectedProject.assignments || []).map((assignment) => {
                  const employee = dashboardEmployeesByUserId[assignment.userId] || assignment;
                  const draftKey = `${selectedProject.id}:${assignment.userId}`;
                  return (
                    <tr key={assignment.id || assignment.userId}>
                      <td><strong>{assignment.userName}</strong><div className="table-subtext">{assignment.email}</div></td>
                      <td><strong>{dateText(assignment.startDate)}</strong><div className="table-subtext">to {dateText(assignment.endDate)}</div></td>
                      <td>
                        <input
                          className="table-hours-input"
                          type="number"
                          min="0"
                          step="0.5"
                          disabled={!assignment.isActive}
                          value={plannedHourDrafts[draftKey] ?? ''}
                          onChange={(event) => setPlannedHourDrafts({ ...plannedHourDrafts, [draftKey]: event.target.value })}
                          onBlur={(event) => { if (assignment.isActive) updatePlannedHours(selectedProject.id, assignment.userId, event.target.value); }}
                          aria-label={`Planned hours for ${assignment.userName}`}
                        />
                      </td>
                      <td>{hours(employee.totalLoggedHours)}</td>
                      <td>{hours(employee.submittedHours)}</td>
                      <td>{hours(assignment.approvedHoursToDate ?? employee.approvedHours)}</td>
                      <td>{hours(Number(plannedHourDrafts[draftKey] || 0) - Number(assignment.approvedHoursToDate ?? employee.approvedHours ?? 0))}</td>
                    </tr>
                  );
                })}
                {(selectedProject.assignments || []).length === 0 && <tr><td colSpan="7" className="text-center">No team assignments yet.</td></tr>}
              </tbody>
            </table>
            </div>
          </div>
        )}

        {activeTab === 'settings' && (
          <div className="project-tab-surface project-details-surface">
            <div className="project-section-heading">
              <div>
                <span>PROJECT DETAILS</span>
                <h3>{selectedProject ? 'Edit project identity and routing' : 'Create a new project workspace'}</h3>
              </div>
              <p>{selectedProject ? 'Changes save when you submit' : 'Manager and approver are required'}</p>
            </div>
            <div className="pc-form-section-title"><Icon name="briefcase" size={19} /><div><h4>Project essentials</h4><p>Define the project identity and status. Total hours are calculated from resource assignments.</p></div></div>
            <div className="form-row pc-essentials-row">
              <div className="form-group">
                <label htmlFor="projectmanagement-field-1">Project Code</label>
                <input id="projectmanagement-field-1" value={form.code} onChange={(event) => setForm({ ...form, code: event.target.value })} required />
              </div>
              <div className="form-group">
                <label htmlFor="projectmanagement-field-2">Project Name</label>
                <input id="projectmanagement-field-2" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} required />
              </div>
              <div className="form-group">
                <label htmlFor="projectmanagement-field-3">Status</label>
                <select id="projectmanagement-field-3" required value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value, isActive: event.target.value === 'ACTIVE' })}>
                  {projectStatuses.map((status) => <option key={status} value={status}>{labelize(status)}</option>)}
                </select>
              </div>

            </div>
            <div className="pc-form-section-title"><Icon name="users" size={19} /><div><h4>Ownership & approval routing</h4><p>Team hours go to the primary PM. Choose who approves the PM’s own hours; the primary PM can also approve their own hours. Onboard the PM through Team to log hours.</p></div></div>
            <div className="form-row pc-routing-row">
              <div className="form-group">
                <label htmlFor="projectmanagement-field-5">Primary Project Manager</label>
                <select id="projectmanagement-field-5" required value={form.projectManagerId} onChange={(event) => {
                  const managerAssignment = (selectedProject?.assignments || []).find((assignment) => String(assignment.userId) === event.target.value);
                  setForm({
                    ...form,
                    projectManagerId: event.target.value,
                  });
                }}>
                  <option value="">Select PM</option>
                  {!canManage && selectedProject?.projectManagerId && <option value={selectedProject.projectManagerId}>{selectedProject.projectManagerName || selectedProject.projectManagerId}</option>}
                  {eligibleReviewers.map((item) => <option key={item.id} value={item.id}>{item.firstName} {item.lastName}</option>)}
                </select>
              </div>
              <div className="form-group">
                <label htmlFor="projectmanagement-field-6">Approver for the PM’s own hours</label>
                <select id="projectmanagement-field-6" required value={form.projectManagerHoursApproverId} onChange={(event) => setForm({ ...form, projectManagerHoursApproverId: event.target.value })}>
                  <option value="">Select approver</option>
                  {!canManage && selectedProject?.projectManagerHoursApproverId && <option value={selectedProject.projectManagerHoursApproverId}>{selectedProject.projectManagerHoursApproverName || selectedProject.projectManagerHoursApproverId}</option>}
                  {eligibleReviewers.map((item) => <option key={item.id} value={item.id}>{item.firstName} {item.lastName}</option>)}
                </select>
              </div>
            </div>
            {selectedProject && (String(form.projectManagerId) !== String(selectedProject.projectManagerId) || String(form.projectManagerHoursApproverId) !== String(selectedProject.projectManagerHoursApproverId)) && (
              <div role="status">
                <strong>Approval handover</strong>
                <p>Primary PM: {selectedProject.projectManagerName} to {eligibleReviewers.find((item) => String(item.id) === String(form.projectManagerId))?.firstName || 'Select PM'}.</p>
                <p>{selectedProject.pendingApprovalCount || 0} pending submissions will be checked and transferred where the responsible approver changes. Incoming approvers will be notified. Completed approvals keep their history.</p>
                <p>The outgoing PM stays on the team. To end their membership, use Team and Offboard, which includes a replacement PM step. Onboard the incoming PM through Team if they need to log hours.</p>
              </div>
            )}
            <div className="pc-form-section-title"><Icon name="file" size={19} /><div><h4>Additional context</h4><p>Give the team a short description of the project and its purpose.</p></div></div>
            <div className="form-group">
              <label htmlFor="projectmanagement-field-7">Description</label>
              <textarea id="projectmanagement-field-7" value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} />
            </div>
            {canManage && <div className="compact-actions">
              <button className="button button-primary" type="submit">{selectedProject ? (String(form.projectManagerId) !== String(selectedProject.projectManagerId) || String(form.projectManagerHoursApproverId) !== String(selectedProject.projectManagerHoursApproverId) ? 'Save and transfer pending approvals' : 'Save Project') : 'Create Project'}</button>
              <button className="button button-secondary" type="button" onClick={cancelProjectEdit}>Cancel</button>
            </div>}
          </div>
        )}

        </fieldset>
      </form>
      )}
    </div>
  );
}
