import ValidationMessage from '../components/ValidationMessage';
// Temporarily disabled: Project Health.
// import { ProjectHealthCard, ProjectHealthOverview, useProjectHealth } from '../components/ProjectHealth';
import ScreenTitle from '../components/ScreenTitle';
import Icon from '../components/Icon';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { format, startOfMonth, subMonths } from 'date-fns';
import { projectAPI, userAPI, expenseAPI, companyAPI } from '../api';
import { ProjectExpenseHistory } from './Expenses';
import { useAuth } from '../AuthContext';
import { useCompany } from '../CompanyContext';
import { canCreateProjectNow, canOpenWorkspaceRoute } from '../workspaceAccess';
import { LoadingIndicator } from '../components/Hourglass';
import '../styles.css';
import './ProjectManagement.css';

const projectStatuses = ['DRAFT', 'ACTIVE', 'ON_HOLD', 'COMPLETED', 'ARCHIVED'];
const projectTabs = [
  { id: 'overview', label: 'Overview', icon: 'grid' },
  { id: 'team', label: 'Team', icon: 'users' },
  { id: 'hours', label: 'Hours', icon: 'clock' },
  { id: 'expenses', label: 'Expenses', icon: 'file' },
  { id: 'settings', label: 'Project Details', icon: 'settings' },
];

const emptyForm = {
  companyId: '',
  ownerUserId:'',
  code: '',
  name: '',
  description: '',
  status: 'DRAFT',
  approvalFrequency: 'MONTHLY',
  projectManagerId: '',
  projectManagerHoursApproverId: '',
  isActive: false,
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

function money(value) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(Number(value || 0));
}

function chartHours(value) {
  return Number(value || 0) >= 10000
    ? new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 }).format(Number(value))
    : hours(value);
}

function chartMoney(value) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', notation: 'compact', maximumFractionDigits: 1 }).format(Number(value || 0));
}

function percent(value, total) {
  if (!total) return 0;
  return Math.max(0, Math.min(100, (Number(value || 0) / total) * 100));
}

function dateText(value) {
  return value ? format(new Date(`${value}T00:00:00`), 'MMM dd, yyyy') : '-';
}

function dateTimeText(value) {
  if (!value) return 'Not available';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? 'Not available' : format(date, 'MMM d, yyyy');
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
  const companyContext = useCompany();
  const { currentCompany, companyCapabilities, permissionsForProject, refreshCompanies } = companyContext;
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
  const [assignApproveHours,setAssignApproveHours]=useState(false),[assignApproveExpenses,setAssignApproveExpenses]=useState(false);
  useEffect(()=>{setAssignApproveHours(false);setAssignApproveExpenses(false);},[selectedProjectId,isCreatingProject]);
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
  const [success, setSuccess] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [savingProject, setSavingProject] = useState(false);
  const [budgetDraft, setBudgetDraft] = useState('');
  const [budgetError, setBudgetError] = useState('');
  const [savingBudget, setSavingBudget] = useState(false);
  const [codeEdited, setCodeEdited] = useState(false);
  const originalForm = useRef(JSON.stringify(emptyForm));
  const [expenseTotals, setExpenseTotals] = useState(null);
  const favoritesKey = 'chronos:project-favorites:' + user?.id;
  const [favorites, setFavorites] = useState([]);

  const canView = canOpenWorkspaceRoute('/projects', companyContext);
  const selectedProject = isCreatingProject ? null : projects.find((project) => project.id === selectedProjectId) || null;
  const canCreateProject = canCreateProjectNow(companyContext);
  const canManage = Boolean(isCreatingProject ? canCreateProject : selectedProject
    && permissionsForProject(selectedProject.id)?.capabilities?.canManageProject);
  const [companyMembers, setCompanyMembers] = useState([]);
  const [scopedProjectRoles, setScopedProjectRoles] = useState([]);
  // const healthState = useProjectHealth(canView, projects);
  const selectedOption = options.find((option) => option.value === selectedPeriod) || options[0];
  const activeUsers = useMemo(() => users.filter((item) => item.isActive
    && (!form.companyId || companyMembers.some(member => member.user_id === item.id && member.status === 'ACTIVE'))),
  [users, form.companyId, companyMembers]);
  const eligibleReviewers = activeUsers.filter(item=>!companyMembers.some(member=>member.user_id===item.id&&(member.platform_account||member.account_available===false)));
  const eligibleApprovers = eligibleReviewers.filter(item =>
    (companyCapabilities?.canManageCompanyPeople&&companyMembers.some(member => member.user_id === item.id && (member.roles || []).includes('PROJECT_ADMIN')))
    || scopedProjectRoles.some(role => role.user_id === item.id && role.role_key === 'PROJECT_ADMIN'));
  const projectFormDirty = JSON.stringify(form) !== originalForm.current;
  const budgetDirty = Boolean(selectedProject) && String(budgetDraft) !== String(selectedProject.expenseBudget ?? '');
  useEffect(() => {
    if (!(projectFormDirty || budgetDirty) || !(isCreatingProject || selectedProject)) return;
    const warn = (event) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [projectFormDirty, budgetDirty, isCreatingProject, selectedProject]);
  useEffect(() => {
    if (!selectedProjectId) { setExpenseTotals(null); return; }
    let active = true;
    expenseAPI.totals(selectedProjectId).then(res => { if (active) setExpenseTotals(res.data); }).catch(() => { if (active) setExpenseTotals(null); });
    return () => { active = false; };
  }, [selectedProjectId, projects]);
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
  }, [canView, selectedPeriod, currentCompany?.id]);

  useEffect(() => {
    if (!form.companyId) { setCompanyMembers([]); return; }
    companyAPI.members(form.companyId).then(response => setCompanyMembers(response.data || [])).catch(() => setCompanyMembers([]));
  }, [form.companyId]);
  useEffect(() => {
    if (!selectedProjectId || !form.companyId) { setScopedProjectRoles([]); return; }
    companyAPI.projectRoles(form.companyId, selectedProjectId).then(response => setScopedProjectRoles(response.data || []))
      .catch(() => setScopedProjectRoles([]));
  }, [form.companyId, selectedProjectId]);

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
        projectAPI.getProjects(currentCompany?.id),
        (companyCapabilities?.canManageProjects||companyCapabilities?.canCreateProjects) ? userAPI.getAllUsers() : Promise.resolve({ data: [] }),
        projectAPI.getHoursDashboard(selectedOption.year, selectedOption.month,currentCompany?.id),
      ]);
      const loadedProjects = (projectRes.data || []).filter(project => !currentCompany || String(project.companyId) === String(currentCompany.id));
      const projectIds = new Set(loadedProjects.map(project => String(project.id)));
      const loadedDashboard = (dashboardRes.data || []).filter(project => projectIds.has(String(project.projectId)));
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

  const selectProject = (project, skipUnsavedPrompt = false) => {
    if (!skipUnsavedPrompt && (projectFormDirty || budgetDirty) && (isCreatingProject || selectedProject) && !window.confirm('Discard unsaved project changes?')) return;
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
    const nextForm = {
      companyId: project?.companyId || '',
      code: project?.code || '',
      name: project?.name || '',
      description: project?.description || '',
      status: project?.status || (project?.isActive ? 'ACTIVE' : 'ARCHIVED'),
      approvalFrequency: project?.pendingApprovalFrequency || project?.approvalFrequency || 'MONTHLY',
      projectManagerId: project?.projectManagerId || '',
      projectManagerHoursApproverId: project?.projectManagerHoursApproverId || '',
      isActive: project?.isActive ?? true,
    };
    setForm(nextForm);
    originalForm.current = JSON.stringify(nextForm);
    setBudgetDraft(project?.expenseBudget == null ? '' : String(project.expenseBudget));
    setBudgetError('');
    setFieldErrors({});
    setSuccess('');
  };

  const startNewProject = () => {
    if ((projectFormDirty || budgetDirty) && (isCreatingProject || selectedProject) && !window.confirm('Discard unsaved project changes?')) return;
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
    const companyForm = { ...emptyForm, companyId: currentCompany?.id || '',ownerUserId:user.id };
    setForm(companyForm);
    originalForm.current = JSON.stringify(companyForm);
    setBudgetDraft('');
    setBudgetError('');
    setCodeEdited(false);
    setFieldErrors({});
    setSuccess('');
  };

  const cancelProjectEdit = () => {
    if ((projectFormDirty || budgetDirty) && !window.confirm('Discard unsaved project changes?')) return;
    if (selectedProject) {
      selectProject(selectedProject, true);
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
    if (selectedProjectId && (projectFormDirty || budgetDirty) && !window.confirm('Discard unsaved project changes?')) return;
    setProjectSearch(value);
    if (selectedProjectId) {
      setSelectedProjectId(null);
      setIsCreatingProject(false);
      setForm(emptyForm);
      originalForm.current = JSON.stringify(emptyForm);
      setBudgetDraft('');
    }
  };

  const saveProject = async (event) => {
    event.preventDefault();
    if (!canManage || savingProject) return;
    if (budgetDirty) {
      setError('Save or reset the expense budget before saving project details.');
      return;
    }
    const duplicate = projects.some(project => String(project.companyId) === String(form.companyId)
      && project.code?.toLowerCase() === form.code.trim().toLowerCase() && project.id !== selectedProject?.id);
    if (duplicate) {
      setFieldErrors({ code: 'This project code is already in use.' });
      return;
    }
    if (!isCreatingProject && form.status !== 'DRAFT' && (!form.projectManagerId || !form.projectManagerHoursApproverId)) {
      setError('Project Manager and PM Hours Approver are required');
      return;
    }
    const payload = {
      ...form,
      companyId: form.companyId ? Number(form.companyId) : null,
      ownerUserId: form.ownerUserId?Number(form.ownerUserId):undefined,
      status: isCreatingProject ? 'DRAFT' : form.status,
      isActive: !isCreatingProject && form.status === 'ACTIVE',
      projectManagerId: form.projectManagerId ? Number(form.projectManagerId) : null,
      projectManagerHoursApproverId: form.projectManagerHoursApproverId ? Number(form.projectManagerHoursApproverId) : null,
      expenseBudget: selectedProject?.expenseBudget ?? null,
    };
    setSavingProject(true);
    setFieldErrors({});
    try {
      const response = selectedProject?.id
        ? await projectAPI.updateProject(selectedProjectId, payload)
        : await projectAPI.createProject(payload);
      if (!selectedProject?.id) await refreshCompanies();
      await loadData();
      const savedProject = response.data;
      if (!selectedProject?.id && savedProject?.id) {
        setProjects((current) => [...current.filter((project) => project.id !== savedProject.id), savedProject]);
        setProjectSearch(savedProject.code + ' - ' + savedProject.name);
        setProjectFilter('DRAFT');
        setActiveTab('settings');
        setAssignDraft('');
        setAssignBillRate('');
        setAssignStartDate('');
        setAssignEndDate('');
        setAssignHours('');
      }
      setSelectedProjectId(savedProject?.id || selectedProjectId);
      setIsCreatingProject(false);
      setBudgetDraft(savedProject?.expenseBudget == null ? '' : String(savedProject.expenseBudget));
      const nextForm = { ...form, status: payload.status, isActive: payload.isActive };
      setForm(nextForm);
      originalForm.current = JSON.stringify(nextForm);
      setSuccess(selectedProject ? 'Project details saved.' : 'Draft created. Complete the setup steps below, then activate the project.');
      setError('');
    } catch (err) {
      const message = err.response?.data?.message || 'Failed to save project.';
      if (/code already exists/i.test(message)) setFieldErrors({ code: 'This project code is already in use.' });
      else setError(message);
    } finally {
      setSavingProject(false);
    }
  };

  const activateProject = async () => {
    if (!selectedProject || savingProject || projectFormDirty || budgetDirty) return;
    setSavingProject(true);
    try {
      const response = await projectAPI.updateProject(selectedProject.id, {
        ...form, status: 'ACTIVE', isActive: true,
        projectManagerId: Number(form.projectManagerId),
        projectManagerHoursApproverId: Number(form.projectManagerHoursApproverId),
        expenseBudget: selectedProject.expenseBudget ?? null,
      });
      await loadData();
      setProjects(current => [...current.filter(project => project.id !== selectedProject.id), response.data]);
      const nextForm = { ...form, status: 'ACTIVE', isActive: true };
      setForm(nextForm);
      originalForm.current = JSON.stringify(nextForm);
      setProjectFilter('ACTIVE');
      setSuccess('Project activated. The team can now use it.');
      setError('');
    } catch (err) {
      setError(err.response?.data?.message || 'Could not activate the project. Complete the setup steps first.');
    } finally {
      setSavingProject(false);
    }
  };

  const saveExpenseBudget = async () => {
    if (!canManage || !selectedProject || savingBudget || !budgetDirty) return;
    const value = budgetDraft.trim();
    if (value && !/^\d+(\.\d{1,2})?$/.test(value)) {
      setBudgetError('Enter a nonnegative amount with no more than two decimal places.');
      return;
    }
    setSavingBudget(true);
    setBudgetError('');
    try {
      const response = await projectAPI.updateProject(selectedProject.id, {
        code: selectedProject.code,
        name: selectedProject.name,
        description: selectedProject.description,
        status: selectedProject.status,
        isActive: selectedProject.isActive,
        projectManagerId: selectedProject.projectManagerId,
        projectManagerHoursApproverId: selectedProject.projectManagerHoursApproverId,
        expenseBudget: value ? Number(value) : null,
      });
      const saved = response.data;
      setProjects(current => current.map(project => project.id === saved.id ? saved : project));
      setBudgetDraft(saved.expenseBudget == null ? '' : String(saved.expenseBudget));
      setSuccess('Expense budget saved.');
      setError('');
    } catch (err) {
      setBudgetError(err.response?.data?.message || 'Could not save the expense budget.');
    } finally {
      setSavingBudget(false);
    }
  };

  const assignEmployee = async () => {
    if (!canManage) return;
    if (!selectedProject?.id || !assignDraft) return;
    if (!assignStartDate || !assignEndDate || (!assignApproveHours&&!assignApproveExpenses&&assignBillRate === '') || !Number.isFinite(Number(assignHours)) || Number(assignHours) < 0 || (Number(assignHours)===0&&!assignApproveHours&&!assignApproveExpenses)) {
      setError('Set valid dates and a bill rate. Assign positive hours or enable approval access.');
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
      if(assignApproveHours||assignApproveExpenses)await projectAPI.assignEmployee(selectedProject.id, assignDraft, assignStartDate, assignEndDate, assignBillRate||'0', assignHours||'0',assignApproveHours,assignApproveExpenses);
      else await projectAPI.assignEmployee(selectedProject.id, assignDraft, assignStartDate, assignEndDate, assignBillRate, assignHours);
      setAssignApproveHours(false);setAssignApproveExpenses(false);
      await refreshCompanies();
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
    { label: 'Remaining', value: remaining, color: 'var(--pc-remaining)' },
  ];
  let allocationEnd = 0;
  const allocationSegments = chartSlices.map((slice) => {
    const start = allocationEnd;
    allocationEnd += percent(slice.value, chartTotal) * 3.6;
    return `${slice.color} ${start}deg ${allocationEnd}deg`;
  });
  const expenseBudget = expenseTotals?.budget == null ? null : Number(expenseTotals.budget);
  const approvedExpenses = Number(expenseTotals?.approved || 0);
  const pendingExpenses = Number(expenseTotals?.pending || 0);
  const approvedBudgetPercent = expenseBudget > 0 ? Number(percent(approvedExpenses, expenseBudget).toFixed(2)) : 0;
  const pendingBudgetPercent = expenseBudget > 0 ? Number(Math.min(100 - approvedBudgetPercent, percent(pendingExpenses, expenseBudget)).toFixed(2)) : 0;
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
        {canCreateProject && <button className="button button-primary" type="button" onClick={startNewProject}><span aria-hidden="true">+</span> New Project</button>}
      </div>

      <ValidationMessage message={error} onDismiss={() => setError('')} />
      {success && <div className="pc-project-success" role="status">{success}</div>}
      {/* Temporarily disabled: Project Health.
      {!selectedProject && !isCreatingProject && canView && <ProjectHealthOverview state={healthState} onOpen={id => selectProject(projects.find(project => project.id === id))} />}
      */}
      {selectedProject&&selectedProject.status!=='ARCHIVED'&&companyCapabilities?.canManageCompanyPeople&&<button type="button" className="button button-secondary" disabled={savingProject} onClick={async()=>{setSavingProject(true);setError('');try{await projectAPI.archive(selectedProject.id);selectProject(null);await loadData();setSuccess('Project archived.');}catch(err){setError(err.userMessage||err.response?.data?.message||'Unable to archive project.');}finally{setSavingProject(false);}}}>Archive project</button>}

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
              if (projectFormDirty && (isCreatingProject || selectedProject) && !window.confirm('Discard unsaved project changes?')) return;
              setProjectFilter(event.target.value);
              setSelectedProjectId(null);
              setProjectSearch('');
              setIsCreatingProject(false);
              setForm(emptyForm);
              originalForm.current = JSON.stringify(emptyForm);
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

        {selectedProject?.status === 'DRAFT' && canManage && (
          <section className="pc-setup-panel" aria-label="Project setup">
            <div><span className="pc-project-code">PROJECT SETUP</span><h3>Prepare this draft for activation</h3><p>Save changes to Project Details before activating.</p></div>
            <ul className="pc-setup-list">
              <li className={selectedProject.projectManagerId ? 'complete' : ''}>Choose a project manager</li>
              <li className={selectedProject.projectManagerHoursApproverId ? 'complete' : ''}>Confirm the PM hours approver</li>
              <li className={activeAssignments.length ? 'complete' : ''}>Assign at least one team member</li>
              <li className={activeAssignments.some(assignment => assignment.userId === selectedProject.projectManagerId) ? 'complete' : ''}>Assign the PM if they will log hours (optional)</li>
            </ul>
            <div className="pc-setup-actions">
              <button type="button" className="button button-secondary" onClick={() => setActiveTab('settings')}>Set approval routing</button>
              <button type="button" className="button button-secondary" onClick={() => setActiveTab('team')}>Add team</button>
              <button type="button" className="button button-primary" disabled={savingProject || projectFormDirty || !selectedProject.projectManagerId || !selectedProject.projectManagerHoursApproverId || !activeAssignments.length} onClick={activateProject}>Activate Project</button>
            </div>
          </section>
        )}

      {/* Temporarily disabled: Project Health.
        {selectedProject && <ProjectHealthCard state={healthState} health={healthState.projects.find(project => project.projectId === selectedProject.id)} />}
      */}

        <div className="project-tabs">
          {projectTabs.map((tab) => (
            <button
              className={`project-tab ${activeTab === tab.id ? 'active' : ''}`}
              disabled={isCreatingProject && tab.id !== 'settings'}
              key={tab.id}
              type="button"
              aria-pressed={activeTab === tab.id}
              aria-controls="project-tab-content"
              onClick={() => {
                if (activeTab === 'expenses' && tab.id !== 'expenses' && budgetDirty) {
                  if (!window.confirm('Discard the unsaved expense budget?')) return;
                  setBudgetDraft(selectedProject?.expenseBudget == null ? '' : String(selectedProject.expenseBudget));
                  setBudgetError('');
                }
                setActiveTab(tab.id);
              }}
            >
              <Icon name={tab.icon} size={17} />{tab.label}
            </button>
          ))}
        </div>

        <fieldset id="project-tab-content" disabled={!canManage && activeTab !== 'expenses'} style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}>
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
                <h3 className="project-chart-title">Project Hours</h3>
                <div className="project-donut-left">
                  <div className="project-status-donut" role="img" aria-label={`Total project hours: ${hours(projectHours)}, summed across all project resources`} style={{ background: `radial-gradient(circle at center, var(--surface-color) 0 56%, transparent 57%), conic-gradient(${chartTotal > 0 ? allocationSegments.join(', ') : 'var(--chart-empty) 0deg 360deg'})` }}>
                    <span>{chartHours(projectHours)}</span>
                    <small>total project hours</small>
                  </div>
                  <div className="project-chart-legend">
                    {chartSlices.map((slice) => <span key={slice.label}><i style={{ background: slice.color }} />{slice.label}<strong>{hours(slice.value)}</strong></span>)}
                  </div>
                </div>
                {/* Temporarily disabled: Project Health overview details.
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
                */}
              </div>
              <div className="project-donut-card project-expense-card">
                <h3 className="project-chart-title">Project Expenses</h3>
                <div className="project-donut-left">
                  <div className="project-status-donut" role="img" aria-label={`Project expenses: ${Number(expenseTotals?.approved || 0).toFixed(2)} approved, ${Number(expenseTotals?.pending || 0).toFixed(2)} pending, ${expenseTotals?.remaining == null ? 'no budget set' : Number(expenseTotals.remaining).toFixed(2) + ' remaining'}`} style={{ background: `radial-gradient(circle at center, var(--surface-color) 0 56%, transparent 57%), conic-gradient(var(--pc-approved) 0deg ${percent(expenseTotals?.approved, Math.max(Number(expenseTotals?.budget || 0), Number(expenseTotals?.approved || 0))) * 3.6}deg, var(--pc-remaining) ${percent(expenseTotals?.approved, Math.max(Number(expenseTotals?.budget || 0), Number(expenseTotals?.approved || 0))) * 3.6}deg 360deg)` }}>
                    <span>{chartMoney(expenseTotals?.approved)}</span>
                    <small>approved expenses</small>
                  </div>
                  <div className="project-chart-legend">
                    <span><i style={{ background: 'var(--pc-approved)' }} />Approved Expenses<strong>{money(expenseTotals?.approved)}</strong></span>
                    <span><i style={{ background: 'var(--pc-remaining)' }} />Remaining Budget<strong>{expenseTotals?.remaining == null ? 'Not set' : money(expenseTotals.remaining)}</strong></span>
                    <span><i style={{ background: 'var(--pc-submitted)' }} />Pending Expenses<strong>{money(expenseTotals?.pending)}</strong></span>
                    <span><i />Expense Budget<strong>{expenseTotals?.budget == null ? 'Not set' : money(expenseTotals.budget)}</strong></span>
                  </div>
                </div>
                {canManage && expenseTotals?.budget != null && (Number(expenseTotals.budget) === 0 ? Number(expenseTotals.approved || 0) + Number(expenseTotals.pending || 0) > 0 : (Number(expenseTotals?.approved || 0) + Number(expenseTotals?.pending || 0)) / Number(expenseTotals.budget) >= .8) && <p role="alert" className="expense-budget-warning">Approved and pending expenses {Number(expenseTotals.approved || 0) + Number(expenseTotals.pending || 0) >= Number(expenseTotals.budget) ? 'meet or exceed' : 'are approaching'} the expense budget.</p>}
              </div>
            </div>
            <div className="pc-plan-progress">
              <div><strong>Project hours used</strong><span>{Number(projectHours) > 0 ? `${Math.round(logged / projectHours * 100)}% of ${hours(projectHours)} total project hrs` : projectHours == null ? 'Total project hours not set' : '0.00 total project hrs'}</span></div>
              <div className="pc-progress-track" role="meter" aria-label="Selected month usage of total project hours" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent(logged, Number(projectHours))} aria-valuetext={`${hours(logged)} hours logged in ${selectedOption.label}; total project hours: ${projectHours == null ? 'not set' : hours(projectHours)}`}><span style={{ width: `${percent(logged, Number(projectHours))}%` }} /></div>
              <p>Total project hours are the sum of hours assigned to all project resources.</p>
            </div>
            {expenseTotals && <div className="pc-plan-progress pc-expense-progress">
              <div><strong>Expense budget used</strong><span>{expenseBudget > 0 ? `${Math.round(approvedExpenses / expenseBudget * 100)}% approved of ${money(expenseBudget)}` : expenseBudget == null ? 'Expense budget not set' : 'Expense budget is $0.00'}</span></div>
              {expenseBudget > 0 && <div className="pc-progress-track pc-expense-progress-track" role="meter" aria-label="Approved expense budget used" aria-valuemin={0} aria-valuemax={100} aria-valuenow={approvedBudgetPercent} aria-valuetext={`${money(approvedExpenses)} approved of ${money(expenseBudget)} budget; ${money(pendingExpenses)} pending`}>
                <span className="pc-expense-approved" style={{ width: `${approvedBudgetPercent}%` }} />
                <span className="pc-expense-pending" style={{ width: `${pendingBudgetPercent}%` }} />
              </div>}
              <p className="pc-budget-breakdown"><span><i className="pc-expense-approved" />Approved {money(approvedExpenses)}</span><span><i className="pc-expense-pending" />Pending {money(pendingExpenses)}</span><span>Remaining {expenseBudget == null ? 'Not set' : money(expenseTotals.remaining)}</span></p>
              <p>Pending expenses are shown separately and are not counted as spent.</p>
            </div>}
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
              {offboarding.userId === selectedProject.projectManagerId && <label>Replacement PM<select value={replacementManagerId} onChange={(event) => setReplacementManagerId(event.target.value)}><option value="">Select a replacement project manager</option>{Array.from(new Map([...activeAssignments, ...activeUsers.filter((member) => scopedProjectRoles.some(role=>role.user_id===member.id&&role.role_key==='PROJECT_ADMIN')).map((member) => ({ userId: member.id, userName: member.name || member.fullName || [member.firstName, member.lastName].filter(Boolean).join(' ') || member.email }))].map((member) => [member.userId, member])).values()).filter((member) => member.userId !== offboarding.userId).map((member) => <option key={member.userId} value={member.userId}>{member.userName}</option>)}</select><span>Choose an active team member or admin. This person becomes the project manager.</span></label>}
              <div className="compact-actions"><button type="button" className="button button-primary" disabled={savingAssignment || offboarding.pendingApproval || (offboarding.userId === selectedProject.projectManagerId && !replacementManagerId)} onClick={() => endAssignment(offboarding.userId)}>Confirm offboarding</button><button type="button" className="button button-secondary" disabled={savingAssignment} onClick={() => setOffboarding(null)}>Cancel</button></div>
            </dialog>}
            <div className="pc-team-summary"><span><Icon name="users" size={18} /><strong>{activeAssignments.length}</strong> active members</span><span><strong>{historicalAssignments.length}</strong> ended assignments</span><span>PM: <strong>{selectedProject.projectManagerName || 'Not assigned'}</strong></span></div>
            {canManage && <div className="pc-assignment-heading"><h4>Add a team member</h4><p>Set dates and hours, or assign approval access without hours.</p><button type="button" className="button button-secondary" onClick={()=>{setAssignApproveHours(true);setAssignHours('0');setAssignBillRate('0');}}>Add moderator to project</button></div>}
            {canManage && <div className="assign-employee-panel">
              <label className="compact-input-field">
                <span>Employee</span>
                <select
                  required
                  value={assignDraft}
                  onChange={(event) => {
                    setAssignDraft(event.target.value);
                    const existing=selectedProject.assignments?.find(item=>String(item.userId)===event.target.value);
                    if(existing){setAssignStartDate(existing.startDate||'');setAssignEndDate(existing.endDate||'');setAssignHours(String(existing.plannedHours||0));setAssignBillRate(String(existing.billRate||0));setAssignApproveHours(assignApproveHours||Boolean(existing.canApproveHours));setAssignApproveExpenses(assignApproveExpenses||Boolean(existing.canApproveExpenses));}
                    else setAssignBillRate(assignApproveHours||assignApproveExpenses?'0':'');
                  }}
                >
                  <option value="">Select employee</option>
                  {activeUsers.filter(item=>item.id!==user.id).map((item) => <option key={item.id} value={item.id}>{item.firstName} {item.lastName}</option>)}
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
              <label className="compact-input-field"><span>Assigned hours</span><input type="number" min="0" step="0.5" value={assignHours} onChange={(event) => setAssignHours(event.target.value)} placeholder="0 for approval only" /></label>
              <fieldset className="project-assignment-approvals"><legend>Moderator access (optional)</legend><label><input type="checkbox" checked={assignApproveHours} onChange={event=>setAssignApproveHours(event.target.checked)}/>Can approve hours</label><label><input type="checkbox" checked={assignApproveExpenses} onChange={event=>setAssignApproveExpenses(event.target.checked)}/>Can approve expenses</label><small>Assign an employee with or without hours. No self-approval.</small></fieldset>
              <button className="button button-small" type="button" onClick={assignEmployee} disabled={savingAssignment || !assignDraft || !assignStartDate || !assignEndDate || assignStartDate > assignEndDate || (!assignApproveHours&&!assignApproveExpenses&&assignBillRate === '') || !Number.isFinite(Number(assignBillRate)) || Number(assignBillRate) < 0 || !Number.isFinite(Number(assignHours)) || Number(assignHours) < 0 || (Number(assignHours)===0&&!assignApproveHours&&!assignApproveExpenses)}>Assign</button>
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
                        <td>{assignment.jobTitle || '-'}{assignment.canApproveHours&&<div className="table-subtext">Approves hours</div>}{assignment.canApproveExpenses&&<div className="table-subtext">Approves expenses</div>}</td>
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

        {activeTab === 'expenses' && selectedProject && (
          <div className="pc-expenses-workspace">
            <section className="pc-expense-budget-panel" aria-label="Project expense budget">
              <div className="pc-expense-budget-heading">
                <div><span className="pc-project-code">EXPENSE PLANNING</span><h3>Expense budget</h3><p>Track approved spending here. Pending claims remain separate until reviewed.</p></div>
                <span className={statusClass(selectedProject.status)}>{labelize(selectedProject.status)}</span>
              </div>
              <div className="pc-expense-budget-metrics">
                <div><span>Budget</span><strong>{expenseBudget == null ? 'Not set' : money(expenseBudget)}</strong></div>
                <div><span>Approved</span><strong>{money(approvedExpenses)}</strong></div>
                <div><span>Pending</span><strong>{money(pendingExpenses)}</strong></div>
                <div><span>Remaining</span><strong>{expenseBudget == null ? 'Not set' : money(expenseTotals?.remaining)}</strong></div>
              </div>
              {canManage && <div className="pc-budget-editor">
                <div className="form-group">
                  <label htmlFor="project-expense-budget">Expense Budget</label>
                  <input id="project-expense-budget" type="number" inputMode="decimal" min="0" step="0.01" placeholder="No budget set" value={budgetDraft} aria-invalid={Boolean(budgetError)} aria-describedby={budgetError ? 'pc-budget-error' : undefined} onChange={event => { setBudgetDraft(event.target.value); setBudgetError(''); }} onKeyDown={event => { if (event.key === 'Enter') { event.preventDefault(); saveExpenseBudget(); } }} />
                  <small className="pc-field-hint">Optional. You can update this without changing other project details.</small>
                  {budgetError && <small id="pc-budget-error" className="pc-field-error" role="alert">{budgetError}</small>}
                </div>
                <button type="button" className="button button-primary" disabled={savingBudget || !budgetDirty} onClick={saveExpenseBudget}>{savingBudget ? 'Saving...' : 'Save Budget'}</button>
              </div>}
            </section>
            <ProjectExpenseHistory projectId={selectedProject.id} showSummary={false} />
          </div>
        )}

        {activeTab === 'settings' && (
          <div className="project-tab-surface project-details-surface">
            <div className="pc-details-heading">
              <div><span className="pc-project-code">PROJECT PROFILE</span><h3>{selectedProject ? 'Project details' : 'Create a project draft'}</h3><p>{selectedProject ? 'Keep the project identity, ownership, and status current.' : 'Start with the essentials. Set the expense budget in Expenses after creating the draft.'}</p></div>
              {selectedProject && <span className={statusClass(selectedProject.status)}>{labelize(selectedProject.status)}</span>}
            </div>
            <div className="pc-details-grid">
              <section className="pc-detail-card pc-identity-card" aria-labelledby="pc-identity-title">
                <div className="pc-detail-card-heading"><span className="pc-detail-icon"><Icon name="briefcase" size={19} /></span><div><h4 id="pc-identity-title">Identity</h4><p>What the team will see across Chronos.</p></div></div>
                <div className="pc-detail-fields">
                  {isCreatingProject && <div className="form-group"><label htmlFor="project-company">Company</label>
                    <input id="project-company" readOnly value={currentCompany?.name || 'Select a company in the header first'} />
                  </div>}
                  <div className="form-group">
                    <label htmlFor="projectmanagement-field-2">Project Name</label>
                    <input id="projectmanagement-field-2" maxLength={150} value={form.name} onChange={(event) => { const name = event.target.value; setForm({ ...form, name, code: isCreatingProject && !codeEdited ? name.trim().toUpperCase().replace(/[^A-Z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 50) : form.code }); }} required />
                  </div>
                  <div className="form-group">
                    <label htmlFor="projectmanagement-field-1">Project Code</label>
                    <input id="projectmanagement-field-1" maxLength={50} aria-invalid={Boolean(fieldErrors.code)} aria-describedby={fieldErrors.code ? 'project-code-error' : undefined} value={form.code} onChange={(event) => { setCodeEdited(true); setFieldErrors({ ...fieldErrors, code: '' }); setForm({ ...form, code: event.target.value.toUpperCase() }); }} required />
                    {isCreatingProject && <small className="pc-field-hint">Suggested from the name. You can edit it.</small>}
                    {fieldErrors.code && <small id="project-code-error" className="pc-field-error" role="alert">{fieldErrors.code}</small>}
                  </div>
                  <div className="form-group pc-description-field">
                    <label htmlFor="projectmanagement-field-7">Description</label>
                    <textarea id="projectmanagement-field-7" maxLength={500} placeholder="What is this project for?" value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} />
                    <small className="pc-field-hint">A short purpose helps the team identify the right project.</small>
                  </div>
                  {isCreatingProject && <div className="form-group">
                    <label htmlFor="new-project-owner">Initial project owner</label>
                    <select id="new-project-owner" required value={form.ownerUserId} onChange={event=>setForm({...form,ownerUserId:event.target.value})}>
                      <option value="">Choose a company member</option>
                      <option value={user.id}>{user.firstName||user.name||'You'} (you)</option>
                      {companyCapabilities?.canManageCompanyPeople&&eligibleReviewers.filter(person=>person.id!==user.id).map(person=><option key={person.id} value={person.id}>{person.name||person.fullName||[person.firstName,person.lastName].filter(Boolean).join(' ')||person.email}</option>)}
                    </select><small>The owner receives Project Admin access to this project.</small>
                  </div>}
                  {isCreatingProject && <div className="form-group">
                    <label htmlFor="new-project-frequency">Timesheet approval frequency</label>
                    <select id="new-project-frequency" value={form.approvalFrequency} onChange={event => setForm({ ...form, approvalFrequency: event.target.value })}>
                      <option value="DAILY">Daily</option><option value="WEEKLY">Weekly (Monday–Sunday)</option><option value="MONTHLY">Monthly</option>
                    </select>
                  </div>}
                </div>
              </section>
              {!isCreatingProject && <section className="pc-detail-card pc-routing-card" aria-labelledby="pc-routing-title">
                <div className="pc-detail-card-heading"><span className="pc-detail-icon"><Icon name="users" size={19} /></span><div><h4 id="pc-routing-title">Ownership & approvals</h4><p>Who leads the project and reviews the PM's hours.</p></div></div>
                <div className="pc-detail-fields">
                  <div className="form-group">
                    <label htmlFor="project-approval-frequency">Timesheet approval frequency</label>
                    <select id="project-approval-frequency" value={form.approvalFrequency} onChange={(event) => setForm({ ...form, approvalFrequency: event.target.value })}>
                      <option value="DAILY">Daily</option><option value="WEEKLY">Weekly (Monday–Sunday)</option><option value="MONTHLY">Monthly</option>
                    </select>
                    <small className="pc-field-hint">The calendar stays daily. Frequency changes take effect next approval period.{selectedProject?.approvalFrequencyEffectiveOn ? ` Scheduled for ${dateText(selectedProject.approvalFrequencyEffectiveOn)}.` : ''}</small>
                  </div>
                  <div className="form-group">
                    <label htmlFor="projectmanagement-field-5">Primary Project Manager</label>
                    <select id="projectmanagement-field-5" required={form.status !== 'DRAFT'} value={form.projectManagerId} onChange={(event) => setForm({ ...form, projectManagerId: event.target.value, projectManagerHoursApproverId: String(form.projectManagerHoursApproverId) === String(event.target.value) ? '' : form.projectManagerHoursApproverId })}>
                      <option value="">Select PM</option>
                      {!canManage && selectedProject?.projectManagerId && <option value={selectedProject.projectManagerId}>{selectedProject.projectManagerName || selectedProject.projectManagerId}</option>}
                      {eligibleReviewers.map((item) => <option key={item.id} value={item.id}>{item.firstName} {item.lastName}</option>)}
                    </select>
                  </div>
                  <div className="form-group">
                    <label htmlFor="projectmanagement-field-6">Approver for the PM's own hours</label>
                    <select id="projectmanagement-field-6" required={form.status !== 'DRAFT'} value={form.projectManagerHoursApproverId} onChange={(event) => setForm({ ...form, projectManagerHoursApproverId: event.target.value })}>
                      <option value="">Select approver</option>
                      {!canManage && selectedProject?.projectManagerHoursApproverId && <option value={selectedProject.projectManagerHoursApproverId}>{selectedProject.projectManagerHoursApproverName || selectedProject.projectManagerHoursApproverId}</option>}
                      {eligibleApprovers.filter(item => item.id !== Number(form.projectManagerId)).map((item) => <option key={item.id} value={item.id}>{item.firstName} {item.lastName}</option>)}
                    </select>
                    <small className="pc-field-hint">Choose a Project Admin. A manager cannot approve their own hours.</small>
                  </div>
                </div>
                {selectedProject?.status !== 'DRAFT' && selectedProject && (String(form.projectManagerId) !== String(selectedProject.projectManagerId) || String(form.projectManagerHoursApproverId) !== String(selectedProject.projectManagerHoursApproverId)) && <div className="pc-handover-note" role="status"><strong>Approval handover</strong><p>{selectedProject.pendingApprovalCount || 0} pending submissions will move to the responsible approver when you save. Completed approvals keep their history. The outgoing PM stays on the team until offboarded.</p></div>}
              </section>}
              {selectedProject && <section className="pc-detail-card pc-lifecycle-card" aria-labelledby="pc-lifecycle-title">
                <div className="pc-detail-card-heading"><span className="pc-detail-icon"><Icon name="clock" size={19} /></span><div><h4 id="pc-lifecycle-title">Lifecycle</h4><p>Status and useful project context in one place.</p></div></div>
                <div className="pc-lifecycle-content">
                  <div className="form-group">
                    <label htmlFor="projectmanagement-field-3">Status</label>
                    <select id="projectmanagement-field-3" required value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value, isActive: event.target.value === 'ACTIVE' })}>
                      {projectStatuses.filter(status => selectedProject.status !== 'DRAFT' || status === 'DRAFT').map((status) => <option key={status} value={status}>{labelize(status)}</option>)}
                    </select>
                    {selectedProject.status === 'DRAFT' && <small className="pc-field-hint">Use Activate Project above after setup is complete.</small>}
                  </div>
                  <dl className="pc-detail-facts">
                    <div><dt>Active team</dt><dd>{activeAssignments.length} member{activeAssignments.length === 1 ? '' : 's'}</dd></div>
                    <div><dt>Assigned hours</dt><dd>{hours(activeAssignments.reduce((sum, assignment) => sum + Number(assignment.plannedHours || 0), 0))}</dd></div>
                    <div><dt>Pending approvals</dt><dd>{selectedProject.pendingApprovalCount || 0}</dd></div>
                    <div><dt>Created</dt><dd>{dateTimeText(selectedProject.createdAt)}</dd></div>
                    <div><dt>Last updated</dt><dd>{dateTimeText(selectedProject.updatedAt)}</dd></div>
                  </dl>
                </div>
              </section>}
            </div>
            {canManage && <div className="pc-details-actions">
              <p>{isCreatingProject ? 'The draft stays private until activation.' : 'Changes to project details save together.'}</p>
              <div><button className="button button-secondary" type="button" onClick={cancelProjectEdit}>Cancel</button><button className="button button-primary" type="submit" disabled={savingProject}>{savingProject ? 'Saving...' : selectedProject ? (selectedProject.status !== 'DRAFT' && (String(form.projectManagerId) !== String(selectedProject.projectManagerId) || String(form.projectManagerHoursApproverId) !== String(selectedProject.projectManagerHoursApproverId)) ? 'Save and transfer pending approvals' : 'Save Project') : 'Create Draft'}</button></div>
            </div>}
          </div>
        )}
        </fieldset>
      </form>
      )}
    </div>
  );
}
