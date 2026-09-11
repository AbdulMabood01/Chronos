import ScreenTitle from '../components/ScreenTitle';
import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { timesheetAPI, reportsAPI, projectAPI } from '../api';
import {
  addMonths,
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

export default function TimesheetDetail({ openCurrentMonth = false, showMonthScroller = false, lockPastMonths = false }) {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const { user } = useAuth();
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
  const [assignedProjects, setAssignedProjects] = useState([]);
  const [selectedProjectId, setSelectedProjectId] = useState('');
  const [projectSubmissionState, setProjectSubmission] = useState(null);
  const projectSubmissionRequest = useRef(0);
  const projectSubmission = String(projectSubmissionState?.timesheetId) === String(timesheet?.id)
    && String(projectSubmissionState?.projectId) === String(selectedProjectId)
    ? projectSubmissionState : null;
  const [projectSubmissionLoading, setProjectSubmissionLoading] = useState(false);
  const [timeDialogDay, setTimeDialogDay] = useState(null);
  const [timeDrafts, setTimeDrafts] = useState([]);
  const requestedProjectId = useMemo(() => new URLSearchParams(location.search).get('projectId') || '', [location.search]);

  useEffect(() => {
    setActiveTimesheetId(id || null);
    setEditMode(false);
  }, [id]);

  const monthOptions = useMemo(() => buildMonthOptions(currentMonthStart), [currentMonthStart]);
  const selectedMonthStart = startOfMonth(new Date(selectedPeriod.year, selectedPeriod.month - 1, 1));
  const canGoPreviousMonth = !isSameMonth(selectedMonthStart, MIN_TIMESHEET_MONTH) && !isBefore(selectedMonthStart, MIN_TIMESHEET_MONTH);
  const canGoNextMonth = isBefore(selectedMonthStart, currentMonthStart);

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
      const sortedTimesheets = (response.data || []).sort((a, b) => {
        if (a.year !== b.year) return b.year - a.year;
        return b.month - a.month;
      });
      setAvailableTimesheets(sortedTimesheets);
      return sortedTimesheets;
    } catch (err) {
      console.error('Error loading timesheet list:', err);
      return [];
    }
  }, [showMonthScroller]);

  const applyTimesheet = useCallback((ts) => {
    setTimesheet(ts);
    setSelectedPeriod({ year: ts.year, month: ts.month });
    setError('');
  }, []);

  const loadTimesheetById = useCallback(async (timesheetId) => {
    try {
      const response = await timesheetAPI.getTimesheetById(timesheetId);
      applyTimesheet(response.data);
    } catch (err) {
      setError('Failed to load timesheet');
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, [applyTimesheet]);

  const loadTimesheetForMonth = useCallback(async (year, month) => {
    const targetMonthStart = startOfMonth(new Date(year, month - 1, 1));
    if (isBefore(targetMonthStart, MIN_TIMESHEET_MONTH) || isAfter(targetMonthStart, currentMonthStart)) {
      return;
    }

    try {
      setEditMode(false);
      const response = await timesheetAPI.getTimesheet(year, month);
      setActiveTimesheetId(response.data.id);
      applyTimesheet(response.data);
      await loadAvailableTimesheets();
    } catch (err) {
      setError('Failed to load timesheet for that month');
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, [applyTimesheet, currentMonthStart, loadAvailableTimesheets]);

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
        const response = await projectAPI.getAssignedProjects(selectedPeriod.year, selectedPeriod.month);
        setAssignedProjects(response.data || []);
      } catch (err) {
        setAssignedProjects([]);
      }
    };

    if (user) {
      loadAssignedProjects();
    }
  }, [selectedPeriod.month, selectedPeriod.year, user]);

  useEffect(() => {
    if (timesheet) {
      buildCalendar(timesheet);
    }
  }, [buildCalendar, timesheet]);

  const timesheetMonthStart = timesheet ? new Date(timesheet.year, timesheet.month - 1, 1) : null;
  const isPastMonth = timesheetMonthStart ? isBefore(timesheetMonthStart, currentMonthStart) : false;
  const isReadOnlyPastMonth = lockPastMonths && isPastMonth;
  const isReviewerRole = user?.canReviewProjects || ['ADMIN', 'SUPER_ADMIN'].includes(user?.role);
  const isAdminReview = isReviewerRole && timesheet?.userId !== user?.id;
  const isOwner = timesheet?.userId === user?.id;
  const timesheetProjectOptions = useMemo(() => {
    const projects = new Map();
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
    if (isOwner) assignedProjects.forEach(project => projects.set(String(project.id), project));
    return Array.from(projects.values()).sort((a, b) => String(a.code).localeCompare(String(b.code)));
  }, [assignedProjects, isOwner, timesheetProjectOptions]);
  const selectedProject = projectOptions.find((project) => String(project.id) === String(selectedProjectId));
  const autoEditableStatuses = ['DRAFT', 'REJECTED'];
  const editButtonStatuses = [];
  const isEditable = timesheet
    && isOwner
    && user?.role !== 'SUPER_ADMIN'
    && !isAdminReview
    && !isReadOnlyPastMonth
    && projectSubmission
    && (autoEditableStatuses.includes(projectSubmission.status) || (editMode && editButtonStatuses.includes(projectSubmission.status)));
  const canStartEdit = false;
  const canApprove = (user?.role === 'SUPER_ADMIN' || projectSubmission?.routedApproverId === user?.id)
    && !isReadOnlyPastMonth && ['SUBMITTED', 'CHANGE_REQUESTED'].includes(projectSubmission?.status) && !isOwner;
  const canReject = canApprove;
  const canReopen = user?.role === 'SUPER_ADMIN' && !isReadOnlyPastMonth && (timesheet?.status === 'APPROVED' || timesheet?.status === 'LOCKED');
  const totalHours = useMemo(() => days.reduce((sum, day) => sum + (parseFloat(day.hours) || 0), 0), [days]);
  const selectedAssignment = (selectedProject?.assignments || []).find((assignment) => String(assignment.userId) === String(timesheet?.userId));
  const plannedHours = Number(projectSubmission?.plannedHours ?? selectedAssignment?.plannedHours ?? 0);
  const remainingHours = Math.max(plannedHours - totalHours, 0);
  const numericBillRate = Number(projectSubmission?.billRate ?? selectedAssignment?.billRate ?? 0);
  const grossPay = totalHours * numericBillRate;
  const selectedStatus = projectSubmission?.status || 'DRAFT';
  const approvingManagerName = projectSubmission?.routedApproverName
    || (String(projectSubmission?.userId || timesheet?.userId || '') === String(selectedProject?.projectManagerId || '')
      ? selectedProject?.projectManagerHoursApproverName
      : selectedProject?.projectManagerName)
    || '-';
  const selectedRejectionReason = projectSubmission?.rejectionReason;
  const selectedApprovedAt = projectSubmission?.approvedAt;
  const selectedApprovedByName = projectSubmission?.approvedByName;
  const canSubmitProjectTimesheet = isEditable && totalHours > 0 && totalHours <= plannedHours;

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
      setProjectSubmissionLoading(false);
      return null;
    }
    try {
      setProjectSubmission(null);
      setProjectSubmissionLoading(true);
      const response = await timesheetAPI.getProjectSubmission(timesheet.id, selectedProjectId);
      if (request === projectSubmissionRequest.current) setProjectSubmission(response.data);
      return response.data;
    } catch (err) {
      if (request === projectSubmissionRequest.current) {
        setProjectSubmission(null);
        setError('Failed to load project timesheet status. Use Refresh status to try again.');
      }
      return null;
    } finally {
      if (request === projectSubmissionRequest.current) setProjectSubmissionLoading(false);
    }
  }, [selectedProjectId, timesheet]);

  useEffect(() => {
    loadProjectSubmission();
    const refreshOnFocus = () => loadProjectSubmission();
    window.addEventListener('focus', refreshOnFocus);
    return () => {
      ++projectSubmissionRequest.current;
      window.removeEventListener('focus', refreshOnFocus);
    };
  }, [loadProjectSubmission]);

  const getDayClassName = (day) => {
    const classes = ['calendar-day'];
    if (isWeekend(day.date)) classes.push('calendar-day-weekend');
    if (day.vacationDay) classes.push(`calendar-day-status-${day.vacationDay.status.toLowerCase()}`);
    if (day.isApprovedVacation) classes.push('calendar-day-vacation');
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
  };

  const buildEntryPayload = (day, hours) => ({
    entryDate: day.dateStr,
    hours: String(hours),
    notes: '',
    projectId: Number(selectedProjectId || 0) || null,
    sessions: day.sessions || [],
  });

  const handleHoursSave = async (index) => {
    const day = days[index];
    if (!timesheet || !isEditable || !selectedProjectId || day.isApprovedVacation || savingDayKeys.includes(day.dateStr)) {
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
      if (hours > 0) {
        if (day.entryId) {
          await timesheetAPI.updateTimeEntry(timesheet.id, day.entryId, buildEntryPayload({ ...day, sessions: [] }, hours));
        } else {
          await timesheetAPI.addTimeEntry(timesheet.id, buildEntryPayload(day, hours));
        }
      } else if (day.entryId) {
        await timesheetAPI.deleteTimeEntry(timesheet.id, day.entryId);
      }
      await loadTimesheetById(timesheet.id);
      await loadProjectSubmission();
      await loadAvailableTimesheets();
      setError('');
    } catch (err) {
      setDays((prev) => prev.map((d, i) => (i === index ? { ...d, hours: d.originalHours } : d)));
      setError(day.isApprovedVacation ? 'Approved vacation days must stay at 0 hours' : `Failed to save hours for ${day.dateStr}. Check project code and time entries.`);
    } finally {
      setSavingDayKeys((current) => current.filter((dateStr) => dateStr !== day.dateStr));
    }
  };

  const openTimeDialog = (index) => {
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
    if (!isEditable || parsedHours <= 0) {
      return;
    }
    setSavingDayKeys((current) => [...current, day.dateStr]);
    try {
      if (day.entryId) {
        await timesheetAPI.updateTimeEntry(timesheet.id, day.entryId, buildEntryPayload(day, parsedHours));
      } else {
        await timesheetAPI.addTimeEntry(timesheet.id, buildEntryPayload(day, parsedHours));
      }
      await loadTimesheetById(timesheet.id);
      await loadProjectSubmission();
      await loadAvailableTimesheets();
      setError('');
    } catch (err) {
      setError(`Failed to save time for ${day.dateStr}`);
    } finally {
      setSavingDayKeys((current) => current.filter((dateStr) => dateStr !== day.dateStr));
    }
  };

  const handleSubmit = async () => {
    try {
      await timesheetAPI.submitProjectTimesheet(timesheet.id, selectedProjectId);
      await loadTimesheetById(timesheet.id);
      await loadProjectSubmission();
      await loadAvailableTimesheets();
      setError('');
    } catch (err) {
      setError('Failed to submit timesheet');
    }
  };

  const handleApprove = async () => {
    try {
      await timesheetAPI.approveProjectSubmission(projectSubmission.id);
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
      await timesheetAPI.rejectProjectSubmission(projectSubmission.id, reason);
      setRejectDialogOpen(false);
      setRejectReason('');
      await loadTimesheetById(timesheet.id);
      await loadProjectSubmission();
    } catch (err) {
      setError('Failed to reject timesheet');
    }
  };

  const handleReopen = async () => {
    const reason = prompt('Enter reason for reopening:');
    if (!reason) return;

    try {
      await timesheetAPI.reopenTimesheet(timesheet.id, reason);
      await loadTimesheetById(timesheet.id);
      await loadProjectSubmission();
    } catch (err) {
      setError('Failed to reopen timesheet');
    }
  };

  const handleExport = async () => {
    try {
      if (!projectSubmission?.pdfExportEligible) {
        setError('Project timesheet must be approved before PDF export.');
        return;
      }
      const response = await reportsAPI.exportProjectTimesheetPdf(projectSubmission.id);
      const safeName = timesheet.userName.replace(/[^A-Za-z0-9]+/g, '_').replace(/^_+|_+$/g, '') || 'employee';
      const projectCode = (selectedProject?.code || 'project').replace(/[^A-Za-z0-9]+/g, '_').replace(/^_+|_+$/g, '');
      downloadBlob(response.data, `${safeName},${projectCode},${format(new Date(timesheet.year, timesheet.month - 1, 1), 'MMMM')},${timesheet.year}.pdf`);
    } catch (err) {
      let message = err.response?.data?.message;
      if (err.response?.data instanceof Blob) {
        try {
          message = JSON.parse(await err.response.data.text()).message;
        } catch { /* A proxy may return an HTML error instead of JSON. */ }
      }
      setError(message || 'Failed to export timesheet. Please try again.');
    }
  };

  const handleMonthNavigation = async (direction) => {
    const nextMonth = direction === 'next' ? addMonths(selectedMonthStart, 1) : subMonths(selectedMonthStart, 1);
    await loadTimesheetForMonth(nextMonth.getFullYear(), nextMonth.getMonth() + 1);
  };

  const handleMonthSelect = async (year, month) => {
    await loadTimesheetForMonth(year, month);
  };

  if (loading) {
    return <div className="page-container"><div className="loading-panel"><LoadingIndicator label="Loading timesheet..." /></div></div>;
  }

  if (!timesheet) {
    return <div className="page-container"><p>Timesheet not found</p></div>;
  }

  const leadingBlanks = days.length > 0 ? getDay(days[0].date) : 0;

  return (
    <div className="page-container">
      <div className="header-bar timesheet-page-header">
        <div>
          <ScreenTitle title="Timesheet" icon="clock" eyebrow="TIME & ATTENDANCE" />
          <p className="page-subtitle">
            {selectedProject ? `${selectedProject.code} - ${selectedProject.name || 'Project'}` : format(new Date(timesheet.year, timesheet.month - 1, 1), 'MMMM yyyy')}
          </p>
        </div>
        {!openCurrentMonth && <button className="button button-secondary" onClick={() => navigate(-1)}>Back</button>}
      </div>

      {error && <div className="error-message">{error}</div>}

      <div className="month-select-row timesheet-filter-row" aria-label="Timesheet filters">
        {showMonthScroller && (
          <>
            <label htmlFor="timesheet-month-select">Month</label>
            <select
              id="timesheet-month-select"
              value={`${timesheet.year}-${timesheet.month}`}
              onChange={(event) => {
                const [year, month] = event.target.value.split('-').map(Number);
                handleMonthSelect(year, month);
              }}
            >
            {monthOptions.map((monthOption) => {
              const savedTimesheet = availableTimesheets.find((ts) => ts.year === monthOption.year && ts.month === monthOption.month);
              return (
                <option
                  key={`${monthOption.year}-${monthOption.month}`}
                  value={`${monthOption.year}-${monthOption.month}`}
                >
                  {monthOption.label}{savedTimesheet ? ` - ${savedTimesheet.status.replace('_', ' ')}` : ''}
                </option>
              );
            })}
            </select>
          </>
        )}
        <label htmlFor="timesheet-project-select">Project</label>
        <select
          id="timesheet-project-select"
          value={selectedProjectId}
          onChange={(event) => setSelectedProjectId(event.target.value)}
          disabled={projectOptions.length === 0}
        >
          {projectOptions.length === 0 ? (
            <option value="">No assigned projects</option>
          ) : projectOptions.map((project) => (
            <option key={project.id} value={project.id}>{project.code}{project.name ? ` - ${project.name}` : ''}</option>
          ))}
        </select>
      </div>

      {isOwner && projectOptions.length === 0 ? (
        <div className="empty-state">
          <p>No project has been assigned to you yet.</p>
        </div>
      ) : (
        <>

      <div className="timesheet-summary-grid">
        <div className="summary-tile">
          <span>Status</span>
          <strong className={`status-badge status-${selectedStatus.toLowerCase()}`}>{selectedStatus}</strong>
        </div>
        <div className="summary-tile approver-summary-tile">
          <span>Approving Manager</span>
          <strong>{approvingManagerName}</strong>
        </div>
        <div className="summary-tile">
          <span>Project Bill Rate</span>
          <strong>{currency(numericBillRate)}</strong>
        </div>
        <div className="summary-tile">
          <span>Project Hours</span>
          <strong>{totalHours.toFixed(2)} / {plannedHours.toFixed(2)}</strong>
        </div>
        <div className="summary-tile remaining-hours-tile">
          <span>Hours Remaining</span>
          <strong>{remainingHours.toFixed(2)}</strong>
        </div>
        <div className="summary-tile gross-pay-tile">
          <span>Gross Pay</span>
          <strong>{currency(grossPay)}</strong>
        </div>
      </div>

      {selectedApprovedAt && (
        <p className="login-note">Approved on {format(new Date(selectedApprovedAt), 'MMM dd, yyyy')} by {selectedApprovedByName || 'approver'}.</p>
      )}

      {selectedRejectionReason && (
        <div className="error-message">
          <strong>Rejection Reason:</strong> {selectedRejectionReason}
        </div>
      )}

      <div className="card timesheet-calendar-card">
        <div className="timesheet-card-heading">
          <div>
            <h2>{selectedProject ? selectedProject.code : 'No Project'} - {format(new Date(timesheet.year, timesheet.month - 1, 1), 'MMMM yyyy')}</h2>
            <div className="calendar-legend">
              <span><i className="legend-dot legend-workday" /> Workday</span>
              <span><i className="legend-dot legend-weekend" /> Weekend</span>
              <span><i className="legend-dot legend-vacation" /> Approved vacation</span>
              <span><i className="legend-dot legend-pending" /> Other request</span>
            </div>
          </div>
          <div className="compact-actions">
            <button className="button button-secondary" onClick={loadProjectSubmission} type="button" disabled={projectSubmissionLoading || !selectedProjectId}>
              Refresh status
            </button>
            <button className="button button-secondary" onClick={handleExport} type="button" disabled={projectSubmissionLoading || !projectSubmission?.id || !projectSubmission?.pdfExportEligible}>
              Export PDF
            </button>
          </div>
        </div>
        {openCurrentMonth && (
          <div className="timesheet-toolbar calendar-month-toolbar">
            <button
              className="button button-secondary"
              onClick={() => handleMonthNavigation('previous')}
              disabled={!canGoPreviousMonth}
              type="button"
            >
              Previous Month
            </button>
            <div className="month-title">
              <span>{format(new Date(timesheet.year, timesheet.month - 1, 1), 'MMMM')}</span>
              <strong>{timesheet.year}</strong>
            </div>
            <button
              className="button button-secondary"
              onClick={() => handleMonthNavigation('next')}
              disabled={!canGoNextMonth}
              type="button"
            >
              Next Month
            </button>
          </div>
        )}
        {isReadOnlyPastMonth && (
          <p className="login-note">Previous month timesheets are read-only. Export is still available.</p>
        )}
        {selectedStatus === 'SUBMITTED' && !isAdminReview && (
          <p className="login-note">This submitted project timesheet is read-only until a Project Manager approves or rejects it.</p>
        )}
        {isAdminReview && canApprove && (
          <p className="login-note">Admin review is read-only. Approve or reject after checking the submitted hours.</p>
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
        {!projectSubmissionLoading && projectSubmission && !isEditable && !isReadOnlyPastMonth && selectedStatus !== 'CHANGE_REQUESTED' && (
          <p className="login-note">This project timesheet is {selectedStatus.toLowerCase()} and frozen for editing.</p>
        )}
        <div className="calendar-grid timesheet-calendar-grid">
          {['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map((weekday) => (
            <div key={weekday} className="calendar-weekday">{weekday}</div>
          ))}
          {Array.from({ length: leadingBlanks }).map((_, i) => (
            <div key={`blank-${i}`} className="calendar-day calendar-day-empty" />
          ))}
          {days.map((day, index) => {
            const isSavingDay = savingDayKeys.includes(day.dateStr);
            const originalHours = Number(day.originalHours || 0);
            const isDayEditable = isEditable && !day.isApprovedVacation && !isSavingDay && (remainingHours > 0 || originalHours > 0);
            const vacationLabel = day.vacationDay ? `${day.vacationDay.vacationType} - ${day.vacationDay.status}` : '';
            return (
              <div key={day.dateStr} className={getDayClassName(day)}>
                <div className="calendar-day-topline">
                  <span className="calendar-day-number">{format(day.date, 'd')}<span className="day-weekday-label">{format(day.date, 'EEE')}</span></span>
                  {day.vacationDay && <span className="day-status-pill">{day.vacationDay.status}</span>}
                </div>
                {day.vacationDay && <div className="day-status-label">{vacationLabel}</div>}
                <label className="day-hours-field">
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
                  </div>
                </label>
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
      </div>

      </>
      )}

      <div className="action-bar">
        {isEditable && ['DRAFT', 'REJECTED'].includes(selectedStatus) && (
          <button
            className="button button-primary"
            onClick={handleSubmit}
            disabled={!canSubmitProjectTimesheet}
          >
            {selectedStatus === 'REJECTED' ? 'Resubmit for Approval' : 'Submit for Approval'}
          </button>
        )}

        {canApprove && (
          <>
            <button
              className="button button-success"
              onClick={handleApprove}
            >
              Approve
            </button>
            <button
              className="button button-danger"
              onClick={handleReject}
            >
              Reject
            </button>
          </>
        )}

        {canReopen && (
          <button
            className="button button-secondary"
            onClick={handleReopen}
          >
            Reopen
          </button>
        )}
      </div>

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
