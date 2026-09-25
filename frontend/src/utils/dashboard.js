import { differenceInCalendarDays, format, parseISO, startOfWeek } from 'date-fns';

export const displayNumber = value => value == null ? '—' : Number(value).toLocaleString(undefined, { maximumFractionDigits: 1 });
const activeProject = project => project.status === 'ACTIVE' && project.isActive !== false;
const rank = status => ({ AT_RISK: 2, ATTENTION_NEEDED: 1 }[status] || 0);
const projectLink = id => `/projects?projectId=${id}`;
const weekdays = (start, end) => {
  const days = differenceInCalendarDays(parseISO(end), parseISO(start)) + 1;
  if (!Number.isFinite(days) || days <= 0) return 0;
  let count = Math.floor(days / 7) * 5;
  const first = parseISO(start).getDay();
  for (let index = 0; index < days % 7; index++) if ((first + index) % 7 !== 0 && (first + index) % 7 !== 6) count++;
  return count;
};

// Estimate only from assignment budgets, never from a partial month's logged hours.
export function capacityExceptions(projects, now, completeScope) {
  const today = format(now, 'yyyy-MM-dd');
  const people = new Map();
  projects.filter(activeProject).forEach(project => (project.assignments || []).forEach(assignment => {
    if (!assignment.isActive || !assignment.startDate || !assignment.endDate || assignment.startDate > today || assignment.endDate < today) return;
    const person = people.get(assignment.userId) || { id: assignment.userId, name: assignment.userName, daily: 0, unknown: false };
    const days = weekdays(assignment.startDate, assignment.endDate);
    if (assignment.plannedHours == null || !days) person.unknown = true;
    else person.daily += Number(assignment.plannedHours) / days;
    people.set(assignment.userId, person);
  }));
  return [...people.values()].filter(person => person.daily > 8.0001 || (completeScope && !person.unknown && person.daily < 6.4))
    .sort((a, b) => b.daily - a.daily)
    .map(person => ({ id: `capacity-${person.id}`, title: person.name, icon: 'users', to: '/project-hours',
      urgent: person.daily > 8, detail: `${person.daily > 8 ? 'Overallocated' : 'Underutilized'} · ${displayNumber(person.daily / 8 * 100)}% planned allocation${completeScope ? '' : ' in visible projects'}` }));
}

export function buildDashboard(data, user = {}, now = new Date()) {
  const systemAdmin = user?.role === 'ADMIN';
  const manager = systemAdmin || user?.role === 'PROJECT_ADMIN' || user?.canManageProjects;
  const today = format(now, 'yyyy-MM-dd');
  const weekStart = format(startOfWeek(now, { weekStartsOn: 1 }), 'yyyy-MM-dd');
  const currentMonth = format(now, 'yyyy-MM');
  const sheets = data.sheets || [];
  const entries = sheets.flatMap(sheet => sheet.timeEntries || []);
  const assignedToday = assignment => assignment.isActive && (!assignment.startDate || assignment.startDate <= today) && (!assignment.endDate || assignment.endDate >= today);
  const active = (data.projects || []).filter(project => activeProject(project) && (manager || project.assignments?.some(assignment => assignment.userId === user?.id && assignedToday(assignment))));
  const health = data.health || [];
  const attention = [];
  const add = (id, title, detail, to, extra = {}) => attention.push({ id, title, detail, to, ...extra });
  const approvals = data.approvals || [];
  const capacity = manager ? capacityExceptions(data.projects || [], now, ['PROJECT_ADMIN', 'ADMIN'].includes(user?.role)) : [];

  if (manager) {
    health.filter(project => project.status !== 'HEALTHY').forEach(project => {
      // Approval queues have their own rows; keep delivery alerts concise and avoid duplicate actions.
      const signals = (project.signals || []).filter(signal => signal.code !== 'APPROVALS');
      if (signals.length) add(`health-${project.projectId}`, `${project.projectCode} · ${project.projectName}`, signals.slice(0, 2).map(signal => signal.message).join(' · ') + (signals.length > 2 ? ` · +${signals.length - 2} more` : ''), projectLink(project.projectId), { urgent: project.status === 'AT_RISK', icon: 'briefcase' });
      if (systemAdmin && project.pendingApprovals > 0) add(`approvals-${project.projectId}`, `${project.projectCode} · Timesheet approvals`, 'Waiting on project reviewers. Open Project Hours to review submission status.', '/project-hours', { count: project.pendingApprovals, icon: 'clock' });
    });
    if (capacity.length) add('capacity', 'Review resource allocation', `${capacity.length} ${capacity.length === 1 ? 'employee has' : 'employees have'} allocation outside 80–100% of daily capacity.`, '/project-hours', { count: capacity.length, icon: 'users' });
  } else {
    sheets.forEach(sheet => {
      const period = `${sheet.year}-${String(sheet.month).padStart(2, '0')}`;
      const rejected = sheet.status === 'REJECTED';
      const overdue = period < currentMonth && sheet.status === 'DRAFT' && Number(sheet.totalHours) > 0;
      if (rejected || overdue) add(`sheet-${sheet.id}`, rejected ? 'Timesheet needs correction' : 'Submit your timesheet', `${format(new Date(sheet.year, sheet.month - 1, 1), 'MMMM yyyy')} · ${sheet.rejectionReason || (rejected ? 'Review feedback and resubmit.' : 'Hours are still in draft for a completed month.')}`, `/timesheet/${sheet.id}`, { urgent: true, icon: 'clock' });
    });
  }
  approvals.forEach(item => add(`approval-${item.id}`, `${item.userName} · ${item.status === 'CHANGE_REQUESTED' ? 'Timesheet change' : 'Timesheet approval'}`, `${item.projectCode} · ${item.month}/${item.year} · ${displayNumber(item.totalHours)} hours`, `/timesheet/${item.timesheetId}?projectId=${item.projectId}`, { icon: 'clock' }));
  if (data.vacations?.length) add('vacations', 'Review time-off requests', 'Requests awaiting your decision.', '/admin', { count: data.vacations.length, icon: 'calendar' });
  if (data.letters?.length) add('letters', 'Review company letter requests', 'Check request details before approval.', '/admin', { count: data.letters.length, icon: 'file' });
  if (systemAdmin) {
    const drafts = (data.reviews || []).filter(review => !review.published_at);
    if (drafts.length) add('review-drafts', 'Complete performance reviews', 'Draft reviews waiting to be published.', '/performance-reviews', { count: drafts.length });
  } else if (!manager) {
    const recent = (data.reviews || []).filter(review => review.published_at)
      .sort((a, b) => (b.published_at || '').localeCompare(a.published_at || ''))[0];
    const age = recent ? differenceInCalendarDays(now, parseISO(recent.modified_at || recent.published_at)) : null;
    if (recent && age >= 0 && age <= 30) add('recent-review', `Read your Q${recent.quarter} ${recent.review_year} review`, 'Recently published or updated · review your goals and feedback.', '/performance-reviews');
  }

  const announcements = (data.announcements || []).filter(item => item.status === 'PUBLISHED' && (!item.publish_date || item.publish_date <= today) && (!item.expiration_date || item.expiration_date >= today));
  announcements.filter(item => item.acknowledgment_required && !item.acknowledged_at).forEach(item => add(`announcement-${item.id}`, `Acknowledge: ${item.title}`, 'Read the announcement and confirm acknowledgment.', `/announcements?id=${encodeURIComponent(item.id)}`, { urgent: item.priority === 'URGENT', icon: 'bell' }));

  // Only actionable unread events belong here; routine inbox updates stay in Notifications.
  const notificationGroups = new Map();
  (data.notifications || []).filter(item => !item.isRead).forEach(item => {
    const type = item.notificationType || '';
    const category = /FEEDBACK.*REQUEST/.test(type) ? 'feedback' : /PERFORMANCE|REVIEW/.test(type) ? 'review' : null;
    if (!category || category === 'review' && attention.some(action => action.id === 'recent-review')) return;
    const group = notificationGroups.get(category) || [];
    group.push(item);
    notificationGroups.set(category, group);
  });
  notificationGroups.forEach((items, category) => add(category, category === 'review' ? 'Review updates' : 'Feedback requested', items[0].title, category === 'review' ? '/performance-reviews' : '/feedback', { count: items.length }));
  attention.sort((a, b) => Number(Boolean(b.urgent)) - Number(Boolean(a.urgent)));
  const actionCount = attention.reduce((sum, item) => sum + (item.count || 1), 0);
  const actionSources = manager ? ['health', 'projects', 'announcements', 'notifications', ...(systemAdmin ? ['vacations', 'letters', 'reviews'] : ['approvals'])] : ['sheets', 'announcements', 'notifications', 'reviews', ...(user?.canReviewProjects ? ['approvals'] : [])];
  const actionsKnown = actionSources.every(key => data[key] != null);
  const pendingApprovals = systemAdmin ? health.reduce((sum, item) => sum + Number(item.pendingApprovals || 0), 0) : approvals.length;
  const metrics = manager ? [
    { label: 'Active Projects', value: data.projects ? active.length : null, detail: 'Currently in delivery', to: '/projects' },
    { label: systemAdmin ? 'Employees' : 'Team Members', value: systemAdmin ? data.employees?.filter(person => person.isActive !== false && person.role !== 'ADMIN').length : data.projects ? new Set(active.flatMap(project => (project.assignments || []).filter(assignedToday).map(item => item.userId))).size : null, detail: systemAdmin ? 'Active employees' : 'Assigned today', to: systemAdmin ? '/users' : '/project-hours' },
    { label: systemAdmin ? 'Pending Actions' : 'Pending Approvals', value: systemAdmin ? actionsKnown ? actionCount : null : data.approvals ? pendingApprovals : null, detail: systemAdmin ? 'Items needing attention' : 'Waiting on your review', to: systemAdmin ? '/dashboard#needs-attention' : '/admin' },
    { label: 'Projects Needing Attention', value: data.health ? health.filter(item => item.status !== 'HEALTHY').length : null, detail: 'Delivery or submission issues', to: '/projects' },
  ] : [
    { label: 'Hours This Week', value: data.sheets ? displayNumber(entries.filter(entry => entry.entryDate >= weekStart && entry.entryDate <= today).reduce((sum, entry) => sum + Number(entry.hours || 0), 0)) : null, detail: 'Monday through today', to: '/timesheets' },
    { label: 'Active Projects', value: data.projects ? active.length : null, detail: 'Your active assignments', to: '/timesheets' },
    { label: 'Pending Actions', value: actionsKnown ? actionCount : null, detail: 'Items needing attention', to: '/dashboard#needs-attention' },
    { label: 'Time Off Balance', value: data.balance?.configured ? `${displayNumber(data.balance.vacation?.remainingDays)} days` : data.balance ? 'Not set' : null, detail: `${now.getFullYear()} vacation remaining`, to: '/vacation' },
  ];
  const healthById = new Map(health.map(item => [item.projectId, item]));
  const projects = manager ? (data.projects || []).filter(project => activeProject(project) || rank(healthById.get(project.id)?.status) > 0).map(project => {
    const status = healthById.get(project.id);
    return { id: project.id, code: project.code, name: project.name, logged: status?.loggedHours, planned: status?.allocatedHours, health: status?.status, note: status?.signals?.[0]?.message || (status ? 'No issues detected' : 'Health unavailable'), to: projectLink(project.id) };
  }).sort((a, b) => rank(b.health) - rank(a.health) || a.code.localeCompare(b.code)) : active.map(project => {
    const assignment = project.assignments?.find(item => item.userId === user?.id);
    return { id: project.id, code: project.code, name: project.name, planned: assignment?.plannedHours, logged: data.sheets ? entries.filter(entry => entry.projectId === project.id && entry.entryDate <= today).reduce((sum, entry) => sum + Number(entry.hours || 0), 0) : null, note: assignment?.endDate ? `Assigned through ${format(parseISO(assignment.endDate), 'MMM d, yyyy')}` : 'Active assignment', to: `/timesheets?projectId=${project.id}` };
  });
  return { metrics, attention, actionCount, capacity, projects, announcements: announcements.filter(item => systemAdmin || (!item.acknowledgment_required || !item.acknowledged_at) && (!item.viewed_at || item.priority !== 'NORMAL')).sort((a, b) => (b.publish_date || '').localeCompare(a.publish_date || '')).slice(0, 3) };
}
