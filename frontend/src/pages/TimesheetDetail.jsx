import '../components/WorkflowFeatures.css';
import ValidationMessage from '../components/ValidationMessage';
import ScreenTitle from '../components/ScreenTitle';
import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { useCompany } from '../CompanyContext';
import { timesheetAPI, reportsAPI, projectAPI } from '../api';
import { hasActiveAssignment } from '../utils/projectAssignments';
import {
  addMonths,
  addDays,
  endOfMonth,
  eachDayOfInterval,
  format,
  getDay,
  isAfter,
  isBefore,
  isSameMonth,
  isWeekend,
  startOfMonth,
  subMonths,
} from 'date-fns';
import { LoadingIndicator } from '../components/Hourglass';
import '../styles.css';
import './TimesheetDetail.css';
import Icon from '../components/Icon';

const MIN_TIMESHEET_MONTH = new Date(2025, 0, 1);

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

function currency(value) {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
  }).format(value || 0);
}

function buildMonthOptions(maxMonth) {
  const months = [];
  let cursor = startOfMonth(maxMonth);

  while (!isBefore(cursor, MIN_TIMESHEET_MONTH)) {
    months.push({
      year: cursor.getFullYear(),
      month: cursor.getMonth() + 1,
      label: format(cursor, 'MMM yyyy'),
      date: cursor,
    });
    cursor = subMonths(cursor, 1);
  }

  return months;
}

function minutesFromTime(value) {
  if (!value || !value.includes(':')) {
    return null;
  }
  const [hours, minutes] = value.split(':').map(Number);
  if (Number.isNaN(hours) || Number.isNaN(minutes)) {
    return null;
  }
  return hours * 60 + minutes;
}

function calculateSessionHours(sessions) {
  const totalMinutes = sessions.reduce((sum, session) => {
    const login = minutesFromTime(session.loginTime);
    const logout = minutesFromTime(session.logoutTime);
    if (login === null || logout === null || logout <= login) {
      return sum;
    }
    return sum + (logout - login);
  }, 0);
  return Number((totalMinutes / 60).toFixed(2));
}

function apiErrorMessage(err, fallback) {
  return err?.response?.data?.message || fallback;
}

export default function TimesheetDetail({ openCurrentMonth = false, showMonthScroller = false }) {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const { user } = useAuth();
  const { currentCompany, platformAdmin, companyCapabilities, permissionsForProject } = useCompany();
  const [currentPeriod] = useState(() => {
    const now = new Date();
    const year = now.getFullYear();
    const month = now.getMonth() + 1;
    return {
      year,
      month,
      monthStart: startOfMonth(new Date(year, month - 1, 1)),
    };
  });
  const currentYear = currentPeriod.year;
  const currentMonth = currentPeriod.month;
  const currentMonthStart = currentPeriod.monthStart;
  const [selectedPeriod, setSelectedPeriod] = useState({ year: currentYear, month: currentMonth });
  const [activeTimesheetId, setActiveTimesheetId] = useState(id || null);
  const [availableTimesheets, setAvailableTimesheets] = useState([]);
  const [timesheet, setTimesheet] = useState(null);
  const [days, setDays] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [savingDayKeys, setSavingDayKeys] = useState([]);
  const [editMode, setEditMode] = useState(false);
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [fallbackReason, setFallbackReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [assignedProjects, setAssignedProjects] = useState([]);
  const [assignmentsLoaded, setAssignmentsLoaded] = useState(false);
  const favoritesKey = 'chronos:project-favorites:' + user?.id;
  const [favorites, setFavorites] = useState([]);
  useEffect(() => {
    try { const saved = JSON.parse(localStorage.getItem(favoritesKey) || '[]'); setFavorites(Array.isArray(saved) ? saved.map(String) : []); }
    catch { setFavorites([]); }
  }, [favoritesKey]);
  const [selectedProjectId, setSelectedProjectId] = useState('');
  const selectedCompanyId = currentCompany ? String(currentCompany.id) : '';
  const [approvalDate, setApprovalDate] = useState(() => new URLSearchParams(location.search).get('date') || '');
  const approvalMonth = `${selectedPeriod.year}-${String(selectedPeriod.month).padStart(2, '0')}`;
  const profileToday = new Intl.DateTimeFormat('sv-SE', { timeZone: user?.timezone || 'America/Chicago',
    year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
  const defaultApprovalDate = approvalMonth === profileToday.slice(0, 7)
    ? profileToday
    : format(new Date(selectedPeriod.year, selectedPeriod.month, 0), 'yyyy-MM-dd');
  const periodDate = approvalDate.startsWith(`${approvalMonth}-`) ? approvalDate
    : approvalDate && approvalDate < `${approvalMonth}-01` ? `${approvalMonth}-01` : defaultApprovalDate;
  const [projectSubmissionState, setProjectSubmission] = useState(null);
  const [approvalPeriods, setApprovalPeriods] = useState([]);
  const [batchDates, setBatchDates] = useState([]);
  const [submissionNotice, setSubmissionNotice] = useState('');
  const [batchMode, setBatchMode] = useState(false);
  const [submissionReview, setSubmissionReview] = useState(null);
  const [saveNotice, setSaveNotice] = useState('');
  const submissionDialog = useRef(null);
  const submissionTrigger = useRef(null);
  const submissionPanel = useRef(null);
  useEffect(() => { if (submissionReview) submissionDialog.current?.showModal(); }, [submissionReview]);
  useEffect(() => { setBatchDates([]); setBatchMode(false); setSubmissionNotice(''); setSaveNotice(''); }, [selectedProjectId, approvalMonth]);
  const projectSubmissionRequest = useRef(0);
  const projectSubmission = [projectSubmissionState,...approvalPeriods].find(period => period
    && String(period.projectId) === String(selectedProjectId)
    && period.periodStart <= periodDate && period.periodEnd >= periodDate
    && String(period.userId) === String(timesheet?.userId)) || null;
  const [projectSubmissionLoading, setProjectSubmissionLoading] = useState(false);
  const [approvalHistory, setApprovalHistory] = useState([]);
  useEffect(() => {
    if (!projectSubmission?.id) { setApprovalHistory([]); return; }
    let active = true;
    timesheetAPI.getApprovalPeriodHistory(projectSubmission.id)
      .then(({ data }) => { if (active) setApprovalHistory(data || []); })
      .catch(() => { if (active) setApprovalHistory([]); });
    return () => { active = false; };
  }, [projectSubmission?.id, projectSubmission?.status, projectSubmission?.submittedAt, projectSubmission?.reviewedAt]);
  const [openingComment, setOpeningComment] = useState('');
  const [openingFormVisible, setOpeningFormVisible] = useState(false);
  const [openingBusy, setOpeningBusy] = useState(false);
  const [timeDialogDay, setTimeDialogDay] = useState(null);
  const [timeDrafts, setTimeDrafts] = useState([]);
  const [notesDay, setNotesDay] = useState(null);
  const [notesDraft, setNotesDraft] = useState('');
  const [notesSaving, setNotesSaving] = useState(false);
  const [notesError, setNotesError] = useState('');
  const notesDialog = useRef(null);
  const notesTrigger = useRef(null);
  useEffect(() => { if (notesDay) notesDialog.current?.showModal(); }, [notesDay]);
  const closeNotes = () => { if (!notesSaving) { setNotesDay(null); notesTrigger.current?.focus(); } };
  const requestedProjectId = useMemo(() => new URLSearchParams(location.search).get('projectId') || '', [location.search]);

  useEffect(() => {
    setActiveTimesheetId(id || null);
    setEditMode(false);
  }, [id]);

  const allMonthOptions = useMemo(() => buildMonthOptions(currentMonthStart), [currentMonthStart]);
  const selectedMonthStart = startOfMonth(new Date(selectedPeriod.year, selectedPeriod.month - 1, 1));

  const buildCalendar = useCallback((ts) => {
    const monthStart = new Date(ts.year, ts.month - 1, 1);
    const monthEnd = endOfMonth(monthStart);
    const entryByDate = {};
    const vacationByDate = {};
    const projectId = String(selectedProjectId || '');

    (ts.timeEntries ? Array.from(ts.timeEntries) : []).forEach((entry) => {
      if (!projectId || String(entry.projectId || '') !== projectId) {
        return;
      }
      entryByDate[entry.entryDate] = entry;
    });

    (ts.vacationDays ? Array.from(ts.vacationDays) : []).forEach((vacationDay) => {
      vacationByDate[vacationDay.date] = vacationDay;
    });

    const calendarDays = eachDayOfInterval({ start: monthStart, end: monthEnd }).map((date) => {
      const dateStr = format(date, 'yyyy-MM-dd');
      const entry = entryByDate[dateStr];
      const vacationDay = vacationByDate[dateStr];
      const isApprovedVacation = vacationDay?.status === 'APPROVED' || vacationDay?.status === 'LOCKED';
      const hours = isApprovedVacation ? '0' : (entry ? String(entry.hours) : '0');
      return {
        date,
        dateStr,
        entryId: entry ? entry.id : null,
        projectId: entry ? entry.projectId : '',
        projectCode: entry ? entry.projectCode : '',
        projectName: entry ? entry.projectName : '',
        hours,
        originalHours: hours,
        sessions: entry?.sessions || [],
        notes: entry?.notes || '',
        vacationDay,
        isApprovedVacation,
      };
    });
    setDays(calendarDays);
  }, [selectedProjectId]);

  const loadAvailableTimesheets = useCallback(async () => {
    if (!showMonthScroller) {
      return [];
    }

    try {
      const response = await timesheetAPI.getMyTimesheets();
      const sortedTimesheets = (response.data || []).filter(sheet => String(sheet.companyId) === String(currentCompany?.id)).sort((a, b) => {
        if (a.year !== b.year) return b.year - a.year;
        return b.month - a.month;
      });
      setAvailableTimesheets(sortedTimesheets);
      return sortedTimesheets;
    } catch (err) {
      console.error('Error loading timesheet list:', err);
      return [];
    }
  }, [showMonthScroller, currentCompany?.id]);

  const applyTimesheet = useCallback((ts) => {
    if (!currentCompany || String(ts.companyId) !== String(currentCompany.id)) {
      setTimesheet(null);
      throw new Error('Timesheet does not belong to the selected company');
    }
    setTimesheet(ts);
    setSelectedPeriod({ year: ts.year, month: ts.month });
    setError('');
  }, [currentCompany?.id]);

  const loadTimesheetById = useCallback(async (timesheetId) => {
    try {
      const response = await timesheetAPI.getTimesheetById(timesheetId, requestedProjectId || undefined);
      applyTimesheet(response.data);
    } catch (err) {
      setError(err.userMessage || apiErrorMessage(err, 'Unable to load this timesheet. Please retry.'));
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, [applyTimesheet, requestedProjectId]);

  const loadTimesheetForMonth = useCallback(async (year, month, companyOverride) => {
    const targetMonthStart = startOfMonth(new Date(year, month - 1, 1));
    if (isBefore(targetMonthStart, MIN_TIMESHEET_MONTH)) {
      return;
    }

    try {
      setEditMode(false);
      const response = await timesheetAPI.getTimesheet(year, month, companyOverride ?? (selectedCompanyId || undefined));
      setActiveTimesheetId(response.data.id);
      applyTimesheet(response.data);
      await loadAvailableTimesheets();
    } catch (err) {
      setError(err.userMessage || apiErrorMessage(err, 'Unable to open a timesheet for this month. Please retry.'));
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, [applyTimesheet, currentMonthStart, loadAvailableTimesheets, selectedCompanyId]);

  useEffect(() => {
    setLoading(true);

    if (activeTimesheetId) {
      loadTimesheetById(activeTimesheetId);
      loadAvailableTimesheets();
      return;
    }

    if (openCurrentMonth) {
      loadTimesheetForMonth(selectedPeriod.year, selectedPeriod.month);
    }
  }, [activeTimesheetId, loadAvailableTimesheets, loadTimesheetById, loadTimesheetForMonth, openCurrentMonth, selectedPeriod.month, selectedPeriod.year]);

  useEffect(() => {
    const loadAssignedProjects = async () => {
      try {
        const reviewingAnotherUser = companyCapabilities?.canReviewWork && timesheet?.userId && timesheet.userId !== user.id;
        const response = reviewingAnotherUser
          ? await projectAPI.getProjects()
          : await projectAPI.getAssignedProjects(selectedPeriod.year, selectedPeriod.month);
        const projects = reviewingAnotherUser
          ? (response.data || []).filter(project => (project.assignments || []).some(assignment =>
            String(assignment.userId) === String(timesheet.userId)
            && (!assignment.startDate || assignment.startDate <= format(endOfMonth(new Date(selectedPeriod.year, selectedPeriod.month - 1, 1)), 'yyyy-MM-dd'))
            && (!assignment.endDate || assignment.endDate >= format(new Date(selectedPeriod.year, selectedPeriod.month - 1, 1), 'yyyy-MM-dd'))))
          : response.data || [];
        setAssignedProjects(projects.filter(project => String(project.companyId) === String(currentCompany?.id)));
        setAssignmentsLoaded(true);
      } catch (err) {
        setError(err.userMessage || 'Unable to load assigned projects. Please try again.');
      }
    };

    if (!user) return;
    loadAssignedProjects();
    const refreshAssignments = () => { if (document.visibilityState !== 'hidden') loadAssignedProjects(); };
    window.addEventListener('focus', refreshAssignments);
    document.addEventListener('visibilitychange', refreshAssignments);
    return () => {
      window.removeEventListener('focus', refreshAssignments);
      document.removeEventListener('visibilitychange', refreshAssignments);
    };
  }, [selectedPeriod.month, selectedPeriod.year, timesheet?.userId, user, currentCompany?.id, companyCapabilities?.canReviewWork]);

  useEffect(() => {
    if (timesheet) {
      buildCalendar(timesheet);
    }
  }, [buildCalendar, timesheet]);

  const timesheetMonthStart = timesheet ? new Date(timesheet.year, timesheet.month - 1, 1) : null;
  const isReviewerRole = companyCapabilities?.canReviewWork === true;
  const isAdminReview = isReviewerRole && timesheet?.userId !== user?.id;
  const isOwner = timesheet?.userId === user?.id;
  const timesheetProjectOptions = useMemo(() => {
    const projects = new Map();
    if (timesheet?.primaryProjectId) projects.set(String(timesheet.primaryProjectId), {
      id: timesheet.primaryProjectId, code: timesheet.primaryProjectCode, name: timesheet.primaryProjectName,
    });
    (timesheet?.timeEntries || []).forEach((entry) => {
      if (entry.projectId) {
        projects.set(String(entry.projectId), {
          id: entry.projectId,
          code: entry.projectCode,
          name: entry.projectName,
        });
      }
    });
    return Array.from(projects.values()).sort((left, right) => String(left.code).localeCompare(String(right.code)));
  }, [timesheet]);
  const projectOptions = useMemo(() => {
    const projects = new Map(timesheetProjectOptions.map(project => [String(project.id), project]));
    if (isOwner || isReviewerRole) assignedProjects
      .filter(project => String(project.companyId) === String(timesheet?.companyId))
      .forEach(project => projects.set(String(project.id), project));
    return Array.from(projects.values()).sort((a, b) => Number(favorites.includes(String(b.id))) - Number(favorites.includes(String(a.id))) || String(a.code).localeCompare(String(b.code)));
  }, [assignedProjects, isOwner, timesheetProjectOptions, favorites, isReviewerRole, timesheet?.companyId]);
  const hasCurrentProject = assignedProjects.some(project => hasActiveAssignment(project, user?.id, format(new Date(), 'yyyy-MM-dd')));
  const selectedProject = projectOptions.find((project) => String(project.id) === String(selectedProjectId));
  const selectedAssignment = (selectedProject?.assignments || []).find((assignment) => String(assignment.userId) === String(timesheet?.userId));
  const isOffboarded = selectedAssignment?.isActive === false;
  const projectOnHold = selectedProject?.status === 'ON_HOLD';
  const projectEnded = ['COMPLETED', 'ARCHIVED'].includes(selectedProject?.status) || (!projectOnHold && selectedProject?.isActive === false);
  const assignmentEnded = selectedAssignment?.endDate && new Date(selectedAssignment.endDate + 'T23:59:59') < new Date();
  const membershipLabel = projectOnHold ? 'On Hold' : selectedProject?.status === 'COMPLETED' ? 'Completed'
    : selectedProject?.status === 'ARCHIVED' ? 'Archived'
    : projectEnded ? 'Ended' : isOffboarded ? 'Offboarded' : 'Assignment ended';
  const membershipNotice = projectOnHold ? 'This project is currently on hold.' : selectedProject?.status === 'COMPLETED' ? 'This project has been completed.'
    : selectedProject?.status === 'ARCHIVED' ? 'This project has been archived.'
    : projectEnded ? 'This project has ended.'
    : isOffboarded ? 'You have been offboarded from this project.'
    : assignmentEnded ? 'Your assignment on this project has ended.' : '';
  const projectUnavailable = projectEnded || (selectedProject?.status && selectedProject.status !== 'ACTIVE');
  const correctionOpen = typeof projectSubmission?.openingActive === 'boolean'
    ? projectSubmission.openingActive
    : projectSubmission?.openingStatus === 'APPROVED';
  const assignmentLastMonth = selectedAssignment?.endDate ? startOfMonth(new Date(selectedAssignment.endDate + 'T00:00:00')) : currentMonthStart;
  const assignmentMonthOptions = isAfter(assignmentLastMonth, currentMonthStart) ? buildMonthOptions(assignmentLastMonth) : allMonthOptions;
  const isFutureMonth = timesheetMonthStart && isAfter(timesheetMonthStart, currentMonthStart);
  const monthOptions = assignmentMonthOptions.filter((option) => !isOwner || (selectedAssignment
    && (!selectedAssignment.startDate || endOfMonth(option.date) >= new Date(selectedAssignment.startDate + 'T00:00:00'))
    && (!selectedAssignment.endDate || option.date <= new Date(selectedAssignment.endDate + 'T00:00:00'))));
  const periodIndex = monthOptions.findIndex(option => option.year === selectedPeriod.year && option.month === selectedPeriod.month);
  const canGoPreviousMonth = periodIndex >= 0 && periodIndex < monthOptions.length - 1;
  const canGoNextMonth = periodIndex > 0;
  useEffect(() => {
    if (isOwner && selectedAssignment && periodIndex < 0 && monthOptions.length) {
      const period = monthOptions[0];
      loadTimesheetForMonth(period.year, period.month);
    }
  }, [isOwner, selectedAssignment, periodIndex, monthOptions, loadTimesheetForMonth]);
  const isEditable = Boolean(timesheet && isOwner && !projectSubmissionLoading && !isFutureMonth && (!projectUnavailable || correctionOpen)
    && (!isOffboarded || correctionOpen)
    && !platformAdmin && permissionsForProject(selectedProjectId)?.capabilities.canSubmitWork && projectSubmission?.editable);
  const canStartEdit = false;
  const canApprove = !isOwner && !platformAdmin && permissionsForProject(selectedProjectId)?.capabilities.canReviewTime && projectSubmission?.reviewAllowed
    && ['SUBMITTED', 'CHANGE_REQUESTED'].includes(projectSubmission?.status);
  const canReject = canApprove;
  const needsFallback = Boolean(projectSubmission?.fallbackRequired);
  const openingDeadline = projectSubmission?.periodEnd ? new Date(`${projectSubmission.periodEnd}T00:00:00`) : null;
  if (openingDeadline) openingDeadline.setDate(openingDeadline.getDate() + 30);
  const openingRequestPending = projectSubmission?.openingStatus === 'PENDING';
  const approvedOrLocked = ['APPROVED', 'LOCKED'].includes(projectSubmission?.status);
  const canRequestOpening = isOwner && !platformAdmin && permissionsForProject(selectedProjectId)?.capabilities.canSubmitWork && approvedOrLocked
    && projectSubmission?.periodEnd && new Date() > new Date(`${projectSubmission.periodEnd}T23:59:59`) && selectedProjectId
    && projectSubmission && !correctionOpen && projectSubmission.status !== 'SUBMITTED'
    && !openingRequestPending && openingDeadline && new Date() <= new Date(openingDeadline.getFullYear(), openingDeadline.getMonth(), openingDeadline.getDate(), 23, 59, 59);
  const totalHours = useMemo(() => days.reduce((sum, day) => sum + (parseFloat(day.hours) || 0), 0), [days]);

  const plannedHours = Number(projectSubmission?.plannedHours ?? selectedAssignment?.plannedHours ?? 0);
  const remainingHours = Math.max(plannedHours - totalHours, 0);
  const numericBillRate = Number(projectSubmission?.billRate ?? selectedAssignment?.billRate ?? 0);
  const grossPay = totalHours * numericBillRate;
  const selectedStatus = projectSubmission?.status || 'DRAFT';
  const isApproved = ['APPROVED', 'LOCKED'].includes(selectedStatus);
  const isReviewView = !isOwner;
  const showReadOnlyHours = isReviewView;
  const dayPeriod = day => approvalPeriods.find(period => String(period.projectId) === String(selectedProjectId)
    && String(period.userId) === String(timesheet?.userId) && day.dateStr >= period.periodStart && day.dateStr <= period.periodEnd)
    || (projectSubmission && day.dateStr >= projectSubmission.periodStart && day.dateStr <= projectSubmission.periodEnd ? projectSubmission : null);
  const canEditDay = day => Boolean(day && isOwner && !platformAdmin && !isFutureMonth
    && dayPeriod(day)
    && (!isOffboarded || dayPeriod(day)?.openingActive)
    && permissionsForProject(selectedProjectId)?.capabilities.canSubmitWork && dayPeriod(day)?.editable
    && (!projectUnavailable || dayPeriod(day)?.openingActive) && !day.isApprovedVacation
    && (!selectedAssignment?.startDate || day.dateStr >= selectedAssignment.startDate)
    && (!selectedAssignment?.endDate || day.dateStr <= selectedAssignment.endDate));
  const readyPeriods = approvalPeriods.filter(period => period.frequency === projectSubmission?.frequency
    && period.editable && ['DRAFT','REJECTED'].includes(period.status)
    && Number(period.totalHours)>0 && period.requiresSubmission && period.periodStart <= profileToday
    && (!selectedAssignment?.startDate || period.periodEnd >= selectedAssignment.startDate)
    && (!selectedAssignment?.endDate || period.periodStart <= selectedAssignment.endDate));
  const canBatchSubmit = isOwner && !platformAdmin && !isOffboarded && !projectUnavailable
    && permissionsForProject(selectedProjectId)?.capabilities.canSubmitWork;
  const visiblePeriods = approvalPeriods.filter(period => (!selectedAssignment?.startDate || period.periodEnd >= selectedAssignment.startDate)
    && (!selectedAssignment?.endDate || period.periodStart <= selectedAssignment.endDate));
  const periodUnit = projectSubmission?.frequency === 'WEEKLY' ? 'week' : projectSubmission?.frequency === 'DAILY' ? 'day' : 'month';
  const periodStatus = period => ({SUBMITTED:'Awaiting Approval',REJECTED:'Changes Requested',APPROVED:'Approved',LOCKED:'Approved',CHANGE_REQUESTED:'Awaiting Approval'})[period.status]
    || (period.periodStart > profileToday ? 'Upcoming' : 'Draft');
  const nextPeriodDate = projectSubmission?.periodEnd ? format(addDays(new Date(`${projectSubmission.periodEnd}T12:00:00`),1),'yyyy-MM-dd') : null;
  const canSelectNextPeriod = nextPeriodDate && (!selectedAssignment?.endDate || nextPeriodDate <= selectedAssignment.endDate);
  useEffect(() => {
    if (!projectSubmissionLoading) setBatchDates(current => current.filter(date => readyPeriods.some(period => period.selectionDate === date)));
  }, [approvalPeriods, projectSubmissionLoading]);
  const approvingManagerName = projectSubmission?.routedApproverName
    || (String(projectSubmission?.userId || timesheet?.userId || '') === String(selectedProject?.projectManagerId || '')
      ? selectedProject?.projectManagerHoursApproverName
      : selectedProject?.projectManagerName)
    || '-';
  const selectedRejectionReason = projectSubmission?.rejectionReason;
  const selectedApprovedAt = projectSubmission?.approvedAt;
  const selectedApprovedByName = projectSubmission?.approvedByName;
  const hasUnsavedHours = days.some(day => Number(day.hours || 0) !== Number(day.originalHours || 0));
  const canSubmitProjectTimesheet = isEditable && !hasUnsavedHours && Number(projectSubmission?.totalHours || 0) <= plannedHours;
  const selectedHours = days.filter(day => day.dateStr >= projectSubmission?.periodStart && day.dateStr <= projectSubmission?.periodEnd)
    .reduce((sum, day) => sum + Number(day.hours || 0), 0)
    + (projectSubmission?.timeEntries || []).filter(entry => !entry.entryDate.startsWith(approvalMonth))
      .reduce((sum, entry) => sum + Number(entry.hours || 0), 0);
  const periodLabel = period => period ? `${format(new Date(`${period.periodStart}T12:00:00`), 'MMM d')}${period.periodEnd !== period.periodStart
    ? ` – ${format(new Date(`${period.periodEnd}T12:00:00`), 'MMM d')}` : ''}` : '';
  const toggleBatch = (period, checked) => setBatchDates(current => checked
    ? [...new Set([...current, period.selectionDate])] : current.filter(date => date !== period.selectionDate));
  const reviewSubmission = (event, batch = false) => {
    if (savingDayKeys.length || hasUnsavedHours || projectSubmissionLoading || submitting) return;
    const periods = batch ? readyPeriods.filter(period => batchDates.includes(period.selectionDate)) : [projectSubmission];
    if (!periods.length || periods.some(period => !period?.editable) || (!batch && !canSubmitProjectTimesheet)) return;
    submissionTrigger.current = event.currentTarget;
    setSubmissionReview({ batch, dates: batch ? periods.map(period => period.selectionDate) : [periodDate],
      periods: periods.map(period => ({ ...period, totalHours: batch ? Number(period.totalHours) : selectedHours })) });
  };
  const closeSubmissionReview = () => { if (!submitting) { setSubmissionReview(null); submissionTrigger.current?.focus(); } };
  const dayLockReason = day => {
    const period = dayPeriod(day);
    if (isReviewView) return '';
    if (day.isApprovedVacation) return 'Approved vacation';
    if (['SUBMITTED', 'CHANGE_REQUESTED'].includes(period?.status)) return 'Awaiting approval';
    if (['APPROVED', 'LOCKED'].includes(period?.status) && !period?.openingActive) return 'Approved';
    if (isFutureMonth || period?.periodStart > profileToday) return 'Upcoming period';
    if ((selectedAssignment?.startDate && day.dateStr < selectedAssignment.startDate)
      || (selectedAssignment?.endDate && day.dateStr > selectedAssignment.endDate)) return 'Outside assignment';
    if ((isOffboarded || projectUnavailable) && !period?.openingActive) return membershipLabel;
    if (!permissionsForProject(selectedProjectId)?.capabilities.canSubmitWork) return 'No editing permission';
    if (!period?.editable) return 'Status unavailable';
    if (remainingHours <= 0 && Number(day.originalHours || 0) === 0) return 'Planned hours reached';
    return '';
  };

  useEffect(() => {
    if (projectOptions.length === 0) {
      if (selectedProjectId) {
        setSelectedProjectId('');
      }
      return;
    }
    if (requestedProjectId && projectOptions.some((project) => String(project.id) === String(requestedProjectId))) {
      setSelectedProjectId(String(requestedProjectId));
      return;
    }
    if (!projectOptions.some((project) => String(project.id) === String(selectedProjectId))) {
      setSelectedProjectId(String(projectOptions[0].id));
    }
  }, [projectOptions, requestedProjectId, selectedProjectId]);

  const loadProjectSubmission = useCallback(async () => {
    const request = ++projectSubmissionRequest.current;
    if (!timesheet?.id || !selectedProjectId) {
      setProjectSubmission(null);
      setApprovalPeriods([]);
      setProjectSubmissionLoading(false);
      return null;
    }
    try {
      setProjectSubmissionLoading(true);
      const [response, monthResponse] = await Promise.all([
        timesheetAPI.getApprovalPeriod(selectedProjectId, periodDate, timesheet.userId),
        timesheetAPI.getApprovalMonth(selectedProjectId, `${approvalMonth}-01`, timesheet.userId),
      ]);
      if (request === projectSubmissionRequest.current) { setProjectSubmission(response.data); setApprovalPeriods(monthResponse.data); }
      return response.data;
    } catch (err) {
      if (request === projectSubmissionRequest.current) {
        setProjectSubmission(null);
        setApprovalPeriods([]);
        setError('Failed to load project timesheet status. Use Refresh status to try again.');
      }
      return null;
    } finally {
      if (request === projectSubmissionRequest.current) setProjectSubmissionLoading(false);
    }
  }, [selectedProjectId, timesheet?.id, timesheet?.userId, periodDate, approvalMonth]);

  useEffect(() => {
    loadProjectSubmission();
    const refreshOnFocus = () => loadProjectSubmission();
    window.addEventListener('focus', refreshOnFocus);
    return () => {
      ++projectSubmissionRequest.current;
      window.removeEventListener('focus', refreshOnFocus);
    };
  }, [loadProjectSubmission]);

  useEffect(() => {
    if (projectSubmission?.openingStatus !== 'PENDING') return undefined;
    const timer = window.setInterval(loadProjectSubmission, 10000);
    return () => window.clearInterval(timer);
  }, [projectSubmission?.openingStatus, loadProjectSubmission]);

  const getDayClassName = (day) => {
    const classes = ['calendar-day'];
    if (isWeekend(day.date)) classes.push('calendar-day-weekend');
    if (Number(day.hours) > 0) classes.push('calendar-day-logged');
    if (day.dateStr === profileToday) classes.push('calendar-day-today');
    if (day.vacationDay) classes.push(`calendar-day-status-${day.vacationDay.status.toLowerCase()}`);
    if (day.isApprovedVacation) classes.push('calendar-day-vacation');
    const period = dayPeriod(day);
    if (period?.late && Number(day.hours)>0) classes.push('calendar-day-late');
    if (period?.periodStart === projectSubmission?.periodStart) classes.push('calendar-day-selected-period');
    if (batchDates.includes(period?.selectionDate)) classes.push('calendar-day-batch-selected');
    return classes.join(' ');
  };

  const handleHoursChange = (index, value) => {
    if (days[index]?.isApprovedVacation) {
      return;
    }
    const proposedHours = Number(value || 0);
    if (!Number.isNaN(proposedHours)) {
      const currentHours = Number(days[index]?.hours || 0);
      const projectedTotal = totalHours - currentHours + proposedHours;
      if (projectedTotal > plannedHours) {
        setError(`Only ${remainingHours.toFixed(2)} project hour${remainingHours === 1 ? '' : 's'} remaining.`);
        return;
      }
      setError('');
    }
    setDays((prev) => prev.map((d, i) => (i === index ? { ...d, hours: value } : d)));
    setSaveNotice(proposedHours !== Number(days[index]?.originalHours || 0) ? 'Unsaved hours — leave the cell to save.' : '');
  };

  const buildEntryPayload = (day, hours, sessions = day.sessions || []) => ({
    entryDate: day.dateStr,
    hours: String(hours),
    notes: day.notes || '',
    projectId: Number(selectedProjectId || 0) || null,
    sessions,
  });

  const handleHoursSave = async (index) => {
    const day = days[index];
    if (!timesheet || !canEditDay(day) || !selectedProjectId || savingDayKeys.includes(day.dateStr)) {
      return;
    }

    const rawHours = String(day.hours ?? '').trim();
    if (rawHours === '' || rawHours === '.') {
      setDays((prev) => prev.map((d, i) => (i === index ? { ...d, hours: d.originalHours } : d)));
      return;
    }

    const parsedHours = Number(rawHours);
    if (Number.isNaN(parsedHours) || parsedHours < 0 || parsedHours > 24) {
      setError('Hours must be a number between 0 and 24');
      setDays((prev) => prev.map((d, i) => (i === index ? { ...d, hours: d.originalHours } : d)));
      return;
    }

    const hours = parsedHours;
    const originalHours = parseFloat(day.originalHours) || 0;
    const projectedTotal = totalHours - (parseFloat(day.hours) || 0) + hours;

    if (projectedTotal > plannedHours) {
      setError(`Cannot save ${hours.toFixed(2)} hours. Only ${remainingHours.toFixed(2)} project hour${remainingHours === 1 ? '' : 's'} remaining.`);
      setDays((prev) => prev.map((d, i) => (i === index ? { ...d, hours: d.originalHours } : d)));
      return;
    }

    if (hours === originalHours) {
      setDays((prev) => prev.map((d, i) => (i === index ? { ...d, hours: String(originalHours) } : d)));
      return;
    }

    setSavingDayKeys((current) => [...current, day.dateStr]);
    try {
      if (hours > 0 || day.notes) {
        if (day.entryId) {
          await timesheetAPI.updateTimeEntry(timesheet.id, day.entryId, buildEntryPayload(day, hours, []));
        } else {
          await timesheetAPI.addTimeEntry(timesheet.id, buildEntryPayload(day, hours, []));
        }
      } else if (day.entryId) {
        await timesheetAPI.deleteTimeEntry(timesheet.id, day.entryId);
      }
      await loadTimesheetById(timesheet.id);
      await loadProjectSubmission();
      await loadAvailableTimesheets();
      setError('');
      setSaveNotice(`Saved hours for ${format(day.date, 'MMM d')}.`);
    } catch (err) {
      setDays((prev) => prev.map((d, i) => (i === index ? { ...d, hours: d.originalHours } : d)));
      setError(day.isApprovedVacation
        ? 'Approved vacation days must stay at 0 hours'
        : apiErrorMessage(err, `Failed to save hours for ${day.dateStr}. Check project code and time entries.`));
      setSaveNotice('Hours could not be saved. Please try again.');
    } finally {
      setSavingDayKeys((current) => current.filter((dateStr) => dateStr !== day.dateStr));
    }
  };

  const openTimeDialog = (index) => {
    setApprovalDate(days[index].dateStr);
    setTimeDialogDay(index);
    const sessions = days[index]?.sessions?.length ? days[index].sessions : [{ loginTime: '', logoutTime: '' }];
    setTimeDrafts(sessions.map((session) => ({
      loginTime: session.loginTime || '',
      logoutTime: session.logoutTime || '',
    })));
  };

  const saveTimeDialog = async () => {
    if (timeDialogDay === null) return;
    const hasPartialSession = timeDrafts.some((session) => Boolean(session.loginTime) !== Boolean(session.logoutTime));
    if (hasPartialSession) {
      setError('Each login time must have a logout time.');
      return;
    }
    const sessions = timeDrafts
      .filter((session) => session.loginTime && session.logoutTime)
      .map((session) => ({ loginTime: session.loginTime, logoutTime: session.logoutTime }));
    const calculatedHours = calculateSessionHours(sessions);
    if (sessions.length > 0 && calculatedHours <= 0) {
      setError('Logout time must be after login time.');
      return;
    }
    const currentHours = Number(days[timeDialogDay]?.hours || 0);
    const projectedTotal = totalHours - currentHours + calculatedHours;
    if (projectedTotal > plannedHours) {
      setError(`Cannot save those login/logout times. Only ${remainingHours.toFixed(2)} project hour${remainingHours === 1 ? '' : 's'} remaining.`);
      return;
    }
    const day = {
      ...days[timeDialogDay],
      hours: sessions.length > 0 ? String(calculatedHours) : days[timeDialogDay].hours,
      sessions,
    };
    setDays((prev) => prev.map((d, i) => (i === timeDialogDay ? day : d)));
    setTimeDialogDay(null);
    setTimeDrafts([]);
    await handleSaveDay(day);
  };

  const handleSaveDay = async (day) => {
    const index = days.findIndex((item) => item.dateStr === day.dateStr);
    const parsedHours = Number(day.hours || 0);
    if (Number.isNaN(parsedHours) || parsedHours < 0 || parsedHours > 24) {
      setError('Hours must be a number between 0 and 24');
      return;
    }
    const currentHours = Number(days[index]?.hours || 0);
    const projectedTotal = totalHours - currentHours + parsedHours;
    if (projectedTotal > plannedHours) {
      setError(`Cannot save ${parsedHours.toFixed(2)} hours. Only ${remainingHours.toFixed(2)} project hour${remainingHours === 1 ? '' : 's'} remaining.`);
      return;
    }
    if (!canEditDay(day)) {
      return;
    }
    setSavingDayKeys((current) => [...current, day.dateStr]);
    try {
      if ((parsedHours > 0 || day.notes) && day.entryId) {
        await timesheetAPI.updateTimeEntry(timesheet.id, day.entryId, buildEntryPayload(day, parsedHours));
      } else if (parsedHours > 0 || day.notes) {
        await timesheetAPI.addTimeEntry(timesheet.id, buildEntryPayload(day, parsedHours));
      } else if (day.entryId) {
        await timesheetAPI.deleteTimeEntry(timesheet.id, day.entryId);
      }
      await loadTimesheetById(timesheet.id);
      await loadProjectSubmission();
      await loadAvailableTimesheets();
      setError('');
    } catch (err) {
      setError(apiErrorMessage(err, `Failed to save time for ${day.dateStr}`));
    } finally {
      setSavingDayKeys((current) => current.filter((dateStr) => dateStr !== day.dateStr));
    }
  };

  const handleSubmit = async (review) => {
    if (submitting) return;
    const { batch, dates } = review;
    setSubmitting(true);
    try {
      if (batch) await timesheetAPI.submitApprovalPeriods(selectedProjectId, dates);
      else await timesheetAPI.submitApprovalPeriod(selectedProjectId, dates[0]);
      await loadTimesheetById(timesheet.id);
      await loadProjectSubmission();
      await loadAvailableTimesheets();
      setSubmissionNotice(batch ? `${dates.length} periods submitted for approval. Other draft periods remain available.`
        : `This ${periodUnit} has been submitted for approval. Select another period to continue entering hours.`);
      setBatchDates([]);
      setBatchMode(false);
      setError('');
    } catch (err) {
      setError(apiErrorMessage(err, 'Failed to submit timesheet'));
    } finally {
      setSubmitting(false);
      setSubmissionReview(null);
      submissionTrigger.current?.focus();
    }
  };

  const goToNextPeriod = async () => {
    if (!nextPeriodDate || !canSelectNextPeriod) return;
    setApprovalDate(nextPeriodDate);
    if (!nextPeriodDate.startsWith(approvalMonth)) {
      const nextDate = new Date(`${nextPeriodDate}T12:00:00`);
      await loadTimesheetForMonth(nextDate.getFullYear(),nextDate.getMonth()+1);
    }
  };
  const openPeriodMonth = async date => {
    setApprovalDate(date);
    const target = new Date(`${date}T12:00:00`);
    await loadTimesheetForMonth(target.getFullYear(),target.getMonth()+1);
  };

  const handleApprove = async () => {
    try {
      await timesheetAPI.decideApprovalPeriod(projectSubmission.id, true, null, needsFallback ? fallbackReason.trim() : null);
      await loadTimesheetById(timesheet.id);
      await loadProjectSubmission();
    } catch (err) {
      setError('Failed to approve timesheet');
    }
  };

  const handleReject = async () => {
    setRejectReason('');
    setRejectDialogOpen(true);
  };

  const handleConfirmReject = async () => {
    const reason = rejectReason.trim();
    if (!reason) {
      setError('Rejection reason is required');
      return;
    }

    try {
      await timesheetAPI.decideApprovalPeriod(projectSubmission.id, false, reason, needsFallback ? fallbackReason.trim() : null);
      setRejectDialogOpen(false);
      setRejectReason('');
      await loadTimesheetById(timesheet.id);
      await loadProjectSubmission();
    } catch (err) {
      setError('Failed to reject timesheet');
    }
  };

  const handleExport = async () => {
    try {
      if (!projectSubmission?.pdfExportEligible) {
        setError('Project timesheet must be approved before PDF export.');
        return;
      }
      const response = await reportsAPI.exportApprovalPeriodPdf(projectSubmission.id);
      const safeName = timesheet.userName.replace(/[^A-Za-z0-9]+/g, '_').replace(/^_+|_+$/g, '') || 'employee';
      const projectCode = (selectedProject?.code || 'project').replace(/[^A-Za-z0-9]+/g, '_').replace(/^_+|_+$/g, '');
      downloadBlob(response.data, `${safeName},${projectCode},${projectSubmission.periodStart}-to-${projectSubmission.periodEnd}.pdf`);
    } catch (err) {
      setError(err.userMessage || 'Failed to export timesheet. Please try again.');
    }
  };

  const handleMonthNavigation = async (direction) => {
    const nextMonth = monthOptions[periodIndex + (direction === 'next' ? -1 : 1)];
    if (nextMonth) await loadTimesheetForMonth(nextMonth.year, nextMonth.month);
  };

  const handleMonthSelect = async (year, month) => {
    await loadTimesheetForMonth(year, month);
  };

  if (loading) {
    return <div className="page-container highlighted-workspace"><div className="loading-panel"><LoadingIndicator label="Loading timesheet..." /></div></div>;
  }

  if (!timesheet) {
    return <div className="page-container highlighted-workspace">
      <ValidationMessage message={error || 'Unable to load this timesheet.'} />
      <button className="button button-primary" onClick={() => {
        setLoading(true);
        if (activeTimesheetId) loadTimesheetById(activeTimesheetId);
        else loadTimesheetForMonth(selectedPeriod.year, selectedPeriod.month);
      }}>Retry</button>
    </div>;
  }

  const mondayFirst = projectSubmission?.frequency === 'WEEKLY';
  const leadingBlanks = days.length > 0 ? (getDay(days[0].date) + (mondayFirst ? 6 : 0)) % 7 : 0;

  return (
    <div className={`page-container timesheet-page${isReviewView ? ' timesheet-review' : ''}`}>
      <div className="header-bar accent-page-header timesheet-page-header">
        <div>
          <ScreenTitle title={isReviewView ? 'Timesheet Review' : 'Timesheet'} icon="clock" eyebrow="TIME & ATTENDANCE" />
          <p className="page-subtitle">
            {isReviewView
              ? `Review ${timesheet.userName || 'employee'}'s recorded hours and submission.`
              : 'Record hours and manage approvals by day, week or month.'}
          </p>
        </div>
        {!openCurrentMonth && <button className="button button-secondary" onClick={() => navigate(-1)}>Back</button>}
      </div>

      <ValidationMessage message={error} onDismiss={() => setError('')} />

      <section className="timesheet-workspace-card" aria-label="Timesheet workspace">
      <div className="month-select-row timesheet-filter-row" aria-label="Timesheet filters">
        <label htmlFor="timesheet-project-select">Project</label>
        <select
          id="timesheet-project-select"
          value={selectedProjectId}
          onChange={(event) => setSelectedProjectId(event.target.value)}
          disabled={projectOptions.length === 0 || savingDayKeys.length > 0 || hasUnsavedHours || submitting}
        >
          {projectOptions.length === 0 ? (
            <option value="">No assigned projects</option>
          ) : projectOptions.map((project) => (
            <option key={project.id} value={project.id}>{favorites.includes(String(project.id)) ? '\u2605 ' : ''}{project.code}{project.name ? ` - ${project.name}` : ''}</option>
          ))}
        </select>
      </div>

      {isOwner && isFutureMonth && <p className="login-note" role="status">This month is read-only. You can enter hours when this month begins.</p>}
      {isOwner && membershipNotice && <div className={`offboarding-notice notice-${membershipLabel.toLowerCase().replaceAll(" ", "-")}`} role="status" aria-live="polite"><span className="offboarding-notice-label">{selectedProject?.name || selectedProject?.code} - {membershipLabel}</span><strong>{membershipNotice}</strong><p>{projectOnHold ? "Hour entry is paused while this project is on hold. Your timesheet history remains available to view." : isOffboarded || projectEnded ? "Your timesheet history is available below for reference. You can no longer enter or change hours for this project." : "Your assignment dates have ended. Hours can only be recorded within your assigned dates and the permitted timesheet period."}</p></div>}
      {isOwner && assignmentsLoaded && !hasCurrentProject && projectOptions.length > 0 && openCurrentMonth && <div className="empty-state" role="status"><h2>No projects assigned</h2><p>You aren't currently assigned to any active projects. Contact your Project Admin if you believe this is incorrect.</p></div>}
      {isOwner && assignmentsLoaded && projectOptions.length === 0 ? (
        <div className="empty-state">
          <h2>No projects assigned</h2>
          <p>You aren't currently assigned to any active projects. Contact your Project Admin if you believe this is incorrect.</p>
        </div>
      ) : (
        <>

      <section className="timesheet-overview" aria-label="Hours overview">
        <div className="timesheet-summary-grid">
          <div className="summary-tile"><span>Hours this month</span><strong>{totalHours.toFixed(2)}<small> hrs</small></strong>
            {projectSubmission?.loggedHoursToDate != null && <small>{Number(projectSubmission.loggedHoursToDate).toFixed(2)} total to date</small>}
          </div>
          <div className="summary-tile remaining-hours-tile"><span>Hours remaining</span><strong>{remainingHours.toFixed(2)}</strong><small>{plannedHours.toFixed(2)} allocated</small></div>
          <div className="summary-tile"><span>Gross pay</span><strong>{currency(grossPay)}</strong></div>
          <div className="summary-tile approver-tile"><span>Approver</span><strong>{approvingManagerName}</strong></div>
          <div className="summary-tile"><span>Hourly rate</span><strong>{currency(numericBillRate)}</strong></div>
        </div>
      </section>

      <div className="card timesheet-calendar-card">
        {isOwner && projectSubmission?.frequency === 'WEEKLY' && (projectSubmission.periodStart.slice(0,7) !== approvalMonth || projectSubmission.periodEnd.slice(0,7) !== approvalMonth) &&
          <div className="cross-month-period-navigation"><span>This week spans two months. Its hours share one approval.</span>
            {[projectSubmission.periodStart,projectSubmission.periodEnd].filter(date => date.slice(0,7)!==approvalMonth).map(date =>
              <button type="button" className="button button-secondary" key={date} disabled={submitting || savingDayKeys.length>0}
                onClick={() => openPeriodMonth(date)}>Open {format(new Date(`${date}T12:00:00`),'MMMM')} Days</button>)}
          </div>}
        <div className="timesheet-card-heading">
          <h2>Hours calendar</h2>
          <details className="timesheet-tools">
            <summary>More actions</summary>
            <div className="timesheet-tools-content">
              <button className="button button-secondary" onClick={loadProjectSubmission} type="button" disabled={projectSubmissionLoading || !selectedProjectId}>Refresh status</button>
              <button className="button button-secondary" onClick={handleExport} type="button" disabled={projectSubmissionLoading || !projectSubmission?.id || !projectSubmission?.pdfExportEligible}>Export PDF</button>
      {selectedProjectId && approvalHistory.length > 0 && <details className="approval-history-panel" aria-label="Approval history">
        <summary>Approval history <span>{approvalHistory.length} events</span></summary>
        <ol>{approvalHistory.map((item,index)=><li key={index}><div><strong>{String(item.event).replaceAll('_',' ')}</strong><span>{item.actor_name || 'System'}</span></div><time>{item.created_at ? new Date(item.created_at).toLocaleString() : ''}</time>{item.comment&&<p>{item.comment}</p>}</li>)}</ol>
      </details>}
            </div>
          </details>
        </div>
        <div className="timesheet-toolbar calendar-month-toolbar">
          {openCurrentMonth && <button className="button button-secondary" onClick={() => handleMonthNavigation('previous')}
            disabled={!canGoPreviousMonth || savingDayKeys.length > 0 || hasUnsavedHours || submitting} type="button"><Icon name="arrow" className="previous-month-arrow" /> Previous Month</button>}
          <div className="calendar-month-selection">
        {showMonthScroller && (
          <>
            <label htmlFor="timesheet-month-select">Month</label>
            <select
              id="timesheet-month-select"
              disabled={savingDayKeys.length > 0 || hasUnsavedHours || submitting}
              value={`${timesheet.year}-${timesheet.month}`}
              onChange={(event) => {
                const [year, month] = event.target.value.split('-').map(Number);
                handleMonthSelect(year, month);
              }}
            >
            {monthOptions.map((monthOption) => {
              return (
                <option
                  key={`${monthOption.year}-${monthOption.month}`}
                  value={`${monthOption.year}-${monthOption.month}`}
                >
                  {monthOption.label}
                </option>
              );
            })}
            </select>
          </>
        )}
            {!showMonthScroller && <h3>{format(new Date(timesheet.year, timesheet.month - 1, 1), 'MMMM yyyy')}</h3>}
          </div>
          {openCurrentMonth && <button className="button button-secondary" onClick={() => handleMonthNavigation('next')}
            disabled={!canGoNextMonth || savingDayKeys.length > 0 || hasUnsavedHours || submitting} type="button">Next Month <Icon name="arrow" /></button>}
        </div>
        {projectSubmission?.frequency === 'WEEKLY' && <section className="period-workspace-navigation" aria-label="Period navigation">
          <div className="period-navigation-heading"><h3>Weeks in this calendar</h3><p>Choose a week or click an hours cell to start editing.</p></div>
          <div className="period-card-list period-card-list-week" role="group" aria-label="Weekly approval periods">
            {visiblePeriods.filter(period => period.frequency === 'WEEKLY').map(period => {
              const selected = period.periodStart === projectSubmission.periodStart;
              const ready = readyPeriods.some(item => item.selectionDate === period.selectionDate);
              return <div key={period.periodStart} className={['period-card', selected ? 'is-selected' : ''].join(' ')}>
                <button type="button" className="period-card-select" aria-label={'Select week starting ' + period.periodStart}
                  aria-pressed={selected} disabled={savingDayKeys.length > 0 || hasUnsavedHours || submitting}
                  onClick={() => { setApprovalDate(period.selectionDate); setSubmissionNotice(''); }}>
                  <strong>{periodLabel(period)}</strong>
                  <span className={'period-card-status status-' + period.status.toLowerCase()}>{periodStatus(period)}</span>
                  <span>{Number(period.totalHours).toFixed(2)} hrs{period.late ? period.submittedAt ? ' · Late' : ' · Past deadline' : ''}</span>
                  {selected && <span className="period-selected-label">Selected week</span>}
                </button>
                {batchMode && canBatchSubmit && ready && <label className="period-batch-select"><input type="checkbox"
                  aria-label={'Include ' + period.periodStart + ' in batch submission'} disabled={submitting || savingDayKeys.length > 0 || hasUnsavedHours}
                  checked={batchDates.includes(period.selectionDate)} onChange={event => toggleBatch(period, event.target.checked)} />Include in batch</label>}
              </div>;
            })}
          </div>
        </section>}
        {selectedProject?.pendingApprovalFrequency && <p className="timesheet-period-note">{selectedProject.pendingApprovalFrequency.toLowerCase()} approval starts {selectedProject.approvalFrequencyEffectiveOn}.</p>}
        <div className="calendar-selection-heading">
          <div><span>{projectSubmission?.frequency || 'PROJECT'} APPROVAL</span><strong>Selected {periodUnit}: {periodLabel(projectSubmission)}</strong></div>
          <p>{projectSubmission?.frequency === 'DAILY' ? 'Click a day or its hours to select the day for submission.' : 'Enter hours directly in the calendar. Edits save when you leave the cell.'}</p>
        </div>
        <div className={`calendar-grid timesheet-calendar-grid${showReadOnlyHours ? " approved-calendar" : ""}`}>
          {(mondayFirst ? ['Mon','Tue','Wed','Thu','Fri','Sat','Sun'] : ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']).map((weekday) => (
            <div key={weekday} className="calendar-weekday">{weekday}</div>
          ))}
          {Array.from({ length: leadingBlanks }).map((_, i) => (
            <div key={`blank-${i}`} className="calendar-day calendar-day-empty" />
          ))}
          {days.map((day, index) => {
            const isSavingDay = savingDayKeys.includes(day.dateStr);
            const originalHours = Number(day.originalHours || 0);
            const withinAssignment = (!selectedAssignment?.startDate || day.dateStr >= selectedAssignment.startDate) && (!selectedAssignment?.endDate || day.dateStr <= selectedAssignment.endDate);
            const period = dayPeriod(day);
            const isDayEditable = canEditDay(day) && withinAssignment && savingDayKeys.length === 0 && !submitting && (remainingHours > 0 || originalHours > 0);
            const readOnlyDay = showReadOnlyHours || (period && !period.editable && ['SUBMITTED','APPROVED','LOCKED','CHANGE_REQUESTED'].includes(period.status));
            const vacationLabel = day.vacationDay ? `${day.vacationDay.vacationType} - ${day.vacationDay.status}` : '';
            return (
              <div key={day.dateStr} className={getDayClassName(day)} title={dayLockReason(day) || undefined}>
                <div className="calendar-day-topline">
                  <button type="button" className="calendar-day-number calendar-period-select" aria-label={`Select approval period for ${day.dateStr}`}
                    disabled={savingDayKeys.length>0 || submitting}
                    onClick={() => setApprovalDate(day.dateStr)} aria-pressed={Boolean(period && period.periodStart === projectSubmission?.periodStart)}>
                    {format(day.date, 'd')}<span className="day-weekday-label">{format(day.date, 'EEE')}</span>
                  </button>
                  {day.vacationDay && <span className="day-status-pill">{day.vacationDay.status}</span>}
                  {period?.late && Number(day.hours)>0 && <span className="timesheet-late-badge" aria-label={`Late entry for ${day.dateStr}`}>Late</span>}
                </div>
                {batchMode && period?.frequency === 'DAILY' && canBatchSubmit && readyPeriods.some(item => item.periodStart === period?.periodStart) &&
                  <label className="calendar-batch-select"><input type="checkbox" aria-label={'Include ' + period.periodStart + ' in batch submission'}
                    checked={batchDates.includes(period.selectionDate)} disabled={submitting || savingDayKeys.length > 0 || hasUnsavedHours}
                    onChange={event => toggleBatch(period, event.target.checked)} />Include day</label>}
                {dayLockReason(day) && <small className="calendar-lock-reason">{dayLockReason(day)}</small>}
                {day.vacationDay && <div className="day-status-label">{vacationLabel}</div>}
                {readOnlyDay ? <button type="button" className="approved-day-hours" aria-label={`Hours for ${day.dateStr}`}
                  disabled={savingDayKeys.length > 0 || submitting} onClick={() => {
                    setApprovalDate(day.dateStr);
                    submissionPanel.current?.scrollIntoView?.({ block: 'start' });
                  }}>
                  {Number(day.hours) > 0 ? <><strong>{Number(day.hours).toFixed(2)}</strong><span>hrs</span></> : <span className="approved-day-empty">-</span>}
                </button> : <label className="day-hours-field">
                  <span>{isSavingDay ? <LoadingIndicator label="Saving" /> : 'Hours'}</span>
                  <div className="day-entry-controls">
                    <input
                      type="number"
                      min="0"
                      max={Math.min(24, originalHours + remainingHours)}
                      step="0.5"
                      className="calendar-day-input"
                      aria-label={`Hours for ${day.dateStr}`}
                      value={day.hours}
                      disabled={!isDayEditable}
                      onFocus={() => { setApprovalDate(day.dateStr); setSubmissionNotice(''); }}
                      onChange={(e) => handleHoursChange(index, e.target.value)}
                      onBlur={() => handleHoursSave(index)}
                    />
                    <button
                      className="clock-time-button"
                      type="button"
                      disabled={!isDayEditable}
                      onClick={() => openTimeDialog(index)}
                      title="Login / logout time"
                      aria-label={`Add login and logout time for ${day.dateStr}`}
                    >
                      <Icon name="clock" size={16}/>
                    </button>
                    <button type="button" className={`clock-time-button notes-button${day.notes ? ' has-notes' : ''}`}
                      aria-label={`Notes for ${day.dateStr}`} title="Daily task notes"
                      disabled={savingDayKeys.length > 0 || submitting}
                      onClick={(event) => { notesTrigger.current = event.currentTarget; setApprovalDate(day.dateStr); setNotesDay({ ...day, editable: Boolean(canEditDay(day)) }); setNotesDraft(day.notes); setNotesError(''); }}>
                      <Icon name="file" size={16} />
                    </button>
                  </div>
                </label>}
                {readOnlyDay && <button type="button" className={`clock-time-button notes-button${day.notes ? ' has-notes' : ''}`} aria-label={`Notes for ${day.dateStr}`}
                  onClick={(event) => { notesTrigger.current = event.currentTarget; setNotesDay({ ...day, editable: false }); setNotesDraft(day.notes); setNotesError(''); }}><Icon name="file" size={16} /></button>}
                {day.sessions?.length > 0 && (
                  <div className="time-session-list">
                    {day.sessions.map((session, sessionIndex) => (
                      <span key={`${day.dateStr}-${sessionIndex}`}>{session.loginTime} - {session.logoutTime}</span>
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>
        {projectSubmission?.timeEntries?.some(entry => !entry.entryDate.startsWith(approvalMonth)) &&
          <section className="cross-month-days" aria-label="Approval days outside this month">
            <h3>This week continues outside the displayed month</h3>
            <p>These entries are included in the selected approval period.</p>
            {projectSubmission.timeEntries.filter(entry => !entry.entryDate.startsWith(approvalMonth)).map(entry =>
              <div key={entry.entryDate}><strong>{entry.entryDate}</strong><span>{Number(entry.hours).toFixed(2)} hours</span>
                {entry.late && <span className="timesheet-late-badge">Late</span>}
                <p>{entry.notes || 'No daily notes'}{entry.sessions?.length>0 && <small>{entry.sessions.map(session => `${session.loginTime} – ${session.logoutTime}`).join(', ')}</small>}</p>
              </div>)}
          </section>}
        {projectSubmission && <section ref={submissionPanel} className="approval-period-toolbar submission-panel" aria-label="Selected approval period">
          <div className="submission-context">
            <span className="eyebrow">{projectSubmission.frequency} APPROVAL</span>
            <h3>{batchMode && batchDates.length ? batchDates.length + ' ' + periodUnit + (batchDates.length === 1 ? '' : 's') + ' selected' : 'Selected ' + periodUnit + ': ' + periodLabel(projectSubmission)}</h3>
            <p className="submission-exact-dates">{batchMode && batchDates.length
              ? readyPeriods.filter(period => batchDates.includes(period.selectionDate)).map(periodLabel).join(', ')
              : projectSubmission.periodStart + (projectSubmission.periodStart !== projectSubmission.periodEnd ? ' – ' + projectSubmission.periodEnd : '')}</p>
            {!batchMode && <div className="approval-period-state"><span className={'status-badge status-' + selectedStatus.toLowerCase()}>{periodStatus(projectSubmission)}</span>
              {projectSubmission.late && <span className="timesheet-late-badge">{projectSubmission.submittedAt ? 'Late' : 'Past deadline'}</span>}
            </div>}
            {batchMode ? <p>Select ready {periodUnit}s in this calendar month. Each is reviewed separately.</p>
              : <p>Due {projectSubmission.periodEnd} at 11:59 pm ({projectSubmission.employeeTimezone || user?.timezone || 'employee timezone'}).</p>}
            {!batchMode && projectSubmission.submittedAt && <p>Submitted {new Date(projectSubmission.submittedAt).toLocaleString()}</p>}
          </div>
          <div className="submission-total"><span>{batchMode && batchDates.length ? 'Selected hours' : 'Selected period hours'}</span>
            <strong>{(batchMode && batchDates.length ? readyPeriods.filter(period => batchDates.includes(period.selectionDate)).reduce((sum, period) => sum + Number(period.totalHours), 0) : selectedHours).toFixed(2)}<small> hrs</small></strong>
            <span role="status">{savingDayKeys.length ? 'Saving hours...' : hasUnsavedHours ? 'Unsaved hours' : saveNotice}</span>
          </div>
          <div className="submission-actions">
            {canBatchSubmit && projectSubmission.frequency !== 'MONTHLY' && readyPeriods.length > 0 && <button type="button" className="button button-secondary"
              aria-pressed={batchMode} disabled={submitting || savingDayKeys.length > 0 || hasUnsavedHours}
              onClick={() => { setBatchMode(value => !value); setBatchDates([]); }}>{batchMode ? 'Cancel multiple selection' : 'Select multiple ' + periodUnit + 's'}</button>}
            {batchMode ? <button type="button" className="button button-primary" disabled={submitting || savingDayKeys.length > 0 || hasUnsavedHours || projectSubmissionLoading || !batchDates.length}
              onClick={event => reviewSubmission(event, true)}>Submit {batchDates.length || 'selected'} {periodUnit}{batchDates.length === 1 ? '' : 's'}</button>
              : isEditable && <button type="button" className="button button-primary" onClick={event => reviewSubmission(event)}
                disabled={submitting || !canSubmitProjectTimesheet || savingDayKeys.length > 0 || projectSubmissionLoading}>
                {submitting ? 'Submitting...' : (selectedStatus === 'REJECTED' || correctionOpen ? 'Resubmit ' : 'Submit ') + periodLabel(projectSubmission)}
              </button>}
            {isOwner && projectSubmission.frequency !== 'MONTHLY' && ['SUBMITTED','APPROVED','LOCKED'].includes(selectedStatus) && <button type="button" className="button button-secondary"
              disabled={!canSelectNextPeriod || submitting || savingDayKeys.length > 0 || hasUnsavedHours} onClick={goToNextPeriod}>Go to Next {periodUnit === 'week' ? 'Week' : 'Day'}</button>}
          </div>
        </section>}
        {submissionNotice && <p className="period-submission-notice" role="status">{submissionNotice}</p>}
      {selectedApprovedAt && (
        <p className="login-note">Approved on {format(new Date(selectedApprovedAt), 'MMM dd, yyyy')} by {selectedApprovedByName || 'approver'}.</p>
      )}

      {selectedRejectionReason && (
        <section className="correction-panel" aria-label="Requested corrections">
          <div className="correction-heading">
            <span className="correction-icon" aria-hidden="true"><Icon name="file" size={20} /></span>
            <div><h3>Changes requested</h3>
              <p>{projectSubmission?.rejectedByName ? `Feedback from ${projectSubmission.rejectedByName}` : 'Reviewer feedback'}</p>
            </div>
            <span className="correction-status">Needs attention</span>
          </div>
          <div className="correction-feedback"><span>What to update</span><p>{selectedRejectionReason}</p></div>
          {isOwner && <p className="correction-next-step"><strong>Next step</strong> Update the hours or daily notes in the calendar, then resubmit the selected period.</p>}
        </section>
      )}

        {isOwner && !correctionOpen && ['DRAFT', 'REJECTED'].includes(projectSubmission?.status) &&
          <p className="timesheet-period-note" role="status">Past draft days remain editable within your assignment dates. Submissions after the deadline are marked late.</p>}
        {correctionOpen && <p className="timesheet-period-note" role="status">Reopened for correction. Editing stays available until you resubmit for approval.</p>}
        {isOwner && approvedOrLocked && !correctionOpen && projectSubmission
          && !['SUBMITTED', 'CHANGE_REQUESTED'].includes(selectedStatus) &&
          <section className="timesheet-opening-panel" aria-label="Timesheet opening">
            <div className="timesheet-opening-copy">
              <span className="timesheet-opening-eyebrow">TIMESHEET FROZEN</span>
              <h3>Approved hours are protected</h3>
              <p>To change approved hours, request an opening from your Project Admin.</p>
              {openingDeadline && <small>Opening requests close {format(openingDeadline, 'MMM d, yyyy')}.</small>}
              {openingRequestPending && <p className="timesheet-opening-status" role="status">Request pending Project Admin review.</p>}
              {projectSubmission?.openingStatus === 'DECLINED' && <p className="timesheet-opening-status">Request declined: {projectSubmission.openingDecisionComment}</p>}
              {!canRequestOpening && !openingRequestPending && openingDeadline && new Date() > new Date(openingDeadline.getFullYear(), openingDeadline.getMonth(), openingDeadline.getDate(), 23, 59, 59)
                && <p className="timesheet-opening-status">The request deadline has passed.</p>}
            </div>
            {canRequestOpening && !openingFormVisible && <button type="button" className="button button-secondary" onClick={() => setOpeningFormVisible(true)}>Request opening</button>}
          </section>}
        {canRequestOpening && openingFormVisible && <form className="timesheet-opening-form" onSubmit={async event => {
          event.preventDefault();
          if (!openingComment.trim()) { setError('Explain why you need this timesheet opened.'); return; }
          setOpeningBusy(true);
          try {
            await timesheetAPI.requestPeriodOpening(selectedProjectId, periodDate, openingComment.trim());
            await loadProjectSubmission();
            setOpeningFormVisible(false);
            setOpeningComment('');
            setError('');
          } catch (err) { setError(apiErrorMessage(err, 'Failed to request an opening')); }
          finally { setOpeningBusy(false); }
        }}>
          <label htmlFor="timesheet-opening-comment">Why do you need this timesheet opened?</label>
          <textarea id="timesheet-opening-comment" value={openingComment} maxLength={500} rows={3}
            onChange={event => setOpeningComment(event.target.value)} required placeholder="Describe the hours you need to add or change" />
          <div className="timesheet-opening-actions">
            <button type="submit" className="button button-primary" disabled={openingBusy || !openingComment.trim()}>{openingBusy ? 'Sending...' : 'Send request to Project Admin'}</button>
            <button type="button" className="button button-secondary" onClick={() => setOpeningFormVisible(false)}>Cancel</button>
          </div>
        </form>}
        {selectedStatus === 'SUBMITTED' && !isAdminReview && (
          <p className="login-note">This submitted project timesheet is read-only until a Project Manager approves or rejects it. Other draft periods remain editable in the calendar.</p>
        )}
        {isAdminReview && canApprove && (
          <p className="login-note">Review the hours and daily notes, then approve or request corrections by rejecting the timesheet.</p>
        )}
        {selectedStatus === 'CHANGE_REQUESTED' && (
          <p className="login-note">Changes are waiting for admin approval before this timesheet is final again.</p>
        )}
        {projectSubmissionLoading && selectedProjectId && (
          <p className="login-note">Loading project timesheet status...</p>
        )}
        {!projectSubmissionLoading && !projectSubmission && selectedProjectId && (
          <p className="login-note">Project timesheet status could not be loaded. Select Refresh status to try again.</p>
        )}
        {isOwner && projectSubmission?.periodStart > profileToday && <p className="login-note">This {periodUnit} opens on {projectSubmission.periodStart}. Select an earlier period to enter missed hours.</p>}
        {!projectSubmissionLoading && projectSubmission && projectSubmission.periodStart <= profileToday && !isEditable && !isReviewView && !approvedOrLocked && !['CHANGE_REQUESTED','SUBMITTED'].includes(selectedStatus) && (
          <p className="login-note">This project timesheet is {selectedStatus.toLowerCase()} and frozen for editing.</p>
        )}

      </div>


      </>
      )}

      <div className="action-bar">
        {canApprove && (
          <>
            {needsFallback && <label>Reason for Project Admin fallback
              <textarea value={fallbackReason} maxLength={500} onChange={event => setFallbackReason(event.target.value)}
                placeholder="Why is the Project Manager unavailable?" />
            </label>}
            <button
              className="button button-success"
              onClick={handleApprove}
              disabled={needsFallback && !fallbackReason.trim()}
            >
              Approve
            </button>
            {canReject && <button
              className="button button-danger"
              onClick={handleReject}
              disabled={needsFallback && !fallbackReason.trim()}
            >
              Reject
            </button>}
          </>
        )}

      </div>

      </section>
      {submissionReview && <dialog ref={submissionDialog} className="modal-card timesheet-submit-dialog" aria-labelledby="submission-review-title"
        onCancel={event => { event.preventDefault(); closeSubmissionReview(); }}>
        <div className="modal-header"><h2 id="submission-review-title">Review submission</h2></div>
        <p>{selectedProject?.code} &middot; {approvingManagerName}</p>
        <ul className="submission-review-list">{submissionReview.periods.map(period => <li key={period.periodStart}>
          <div><strong>{periodLabel(period)}</strong><span>{period.periodStart}{period.periodEnd !== period.periodStart && ' - ' + period.periodEnd}</span></div>
          <strong>{Number(period.totalHours).toFixed(2)} hrs</strong>{period.late && <span className="timesheet-late-badge">Late</span>}
        </li>)}</ul>
        <p className="submission-review-total">Total <strong>{submissionReview.periods.reduce((sum, period) => sum + Number(period.totalHours), 0).toFixed(2)} hrs</strong></p>
        <p>Submitted hours lock until review. Each period is approved separately.</p>
        <div className="action-bar compact-actions">
          <button type="button" className="button button-secondary" disabled={submitting} onClick={closeSubmissionReview}>Cancel</button>
          <button type="button" className="button button-primary" disabled={submitting} onClick={() => handleSubmit(submissionReview)}>{submitting ? 'Submitting...' : 'Submit for approval'}</button>
        </div>
      </dialog>}
      {notesDay && <dialog ref={notesDialog} className="modal-card daily-notes-dialog" aria-labelledby="daily-notes-title"
        onCancel={(event) => { event.preventDefault(); closeNotes(); }}>
        <div className="modal-header"><h2 id="daily-notes-title">Daily notes · {format(notesDay.date, 'MMM d, yyyy')}</h2></div>
        <label htmlFor="daily-notes">Tasks done that day (optional)</label>
        <textarea id="daily-notes" autoFocus rows={5} maxLength={500} value={notesDraft} readOnly={!notesDay.editable} disabled={notesSaving}
          placeholder={notesDay.editable ? 'What did you work on?' : 'No notes recorded for this day.'} onChange={(event) => setNotesDraft(event.target.value)} />
        {notesError && <p role="alert">{notesError}</p>}
        <div className="action-bar compact-actions">
          <button type="button" className="button button-secondary" disabled={notesSaving} onClick={closeNotes}>Close</button>
          {notesDay.editable && <button type="button" className="button button-primary" disabled={notesSaving} onClick={async () => {
            setNotesSaving(true); setNotesError('');
            try {
              const payload = buildEntryPayload({ ...notesDay, notes: notesDraft }, notesDay.originalHours);
              if (notesDay.entryId) await timesheetAPI.updateTimeEntry(timesheet.id, notesDay.entryId, payload);
              else await timesheetAPI.addTimeEntry(timesheet.id, payload);
              await loadTimesheetById(timesheet.id);
              await loadProjectSubmission();
              setNotesDay(null); notesTrigger.current?.focus();
            } catch (err) { setNotesError(apiErrorMessage(err, 'Could not save notes. Please try again.')); }
            finally { setNotesSaving(false); }
          }}>{notesSaving ? 'Saving…' : 'Save notes'}</button>}
        </div>
      </dialog>}

      {rejectDialogOpen && (
        <div className="modal-backdrop" role="presentation">
          <div className="modal-card" role="dialog" aria-modal="true" aria-labelledby="reject-timesheet-title">
            <div className="modal-header">
              <h2 id="reject-timesheet-title">Reject Timesheet</h2>
              <button className="modal-close" onClick={() => setRejectDialogOpen(false)} type="button" aria-label="Close">
                x
              </button>
            </div>
            <p className="modal-subtitle">Add a clear reason so the employee knows what to correct.</p>
            <textarea
              value={rejectReason}
              onChange={(event) => setRejectReason(event.target.value)}
              rows="4"
              aria-label="Reason for rejection"
              maxLength={500}
              placeholder="Reason for rejection"
              autoFocus
            />
            <div className="action-bar compact-actions">
              <button className="button button-secondary" onClick={() => setRejectDialogOpen(false)} type="button">
                Cancel
              </button>
              <button className="button button-danger" onClick={handleConfirmReject} type="button">
                Reject
              </button>
            </div>
          </div>
        </div>
      )}

      {timeDialogDay !== null && (
        <div className="modal-backdrop" role="presentation">
          <div className="modal-card" role="dialog" aria-modal="true" aria-labelledby="time-entry-title">
            <div className="modal-header">
              <h2 id="time-entry-title">Login / Logout Time</h2>
              <button className="modal-close" onClick={() => setTimeDialogDay(null)} type="button" aria-label="Close">
                x
              </button>
            </div>
            <div className="time-draft-list">
              {timeDrafts.map((session, index) => (
                <div className="time-draft-row" key={index}>
                  <input
                    type="time"
                    value={session.loginTime}
                    onChange={(event) => setTimeDrafts((rows) => rows.map((row, i) => i === index ? { ...row, loginTime: event.target.value } : row))}
                  />
                  <input
                    type="time"
                    value={session.logoutTime}
                    onChange={(event) => setTimeDrafts((rows) => rows.map((row, i) => i === index ? { ...row, logoutTime: event.target.value } : row))}
                  />
                  <button className="modal-close" type="button" onClick={() => setTimeDrafts((rows) => rows.filter((_, i) => i !== index))}>x</button>
                </div>
              ))}
            </div>
            <div className="action-bar compact-actions">
              <button className="button button-secondary" type="button" onClick={() => setTimeDrafts((rows) => [...rows, { loginTime: '', logoutTime: '' }])}>
                Add Another
              </button>
              <button className="button button-primary" type="button" onClick={saveTimeDialog}>
                Save Time
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
