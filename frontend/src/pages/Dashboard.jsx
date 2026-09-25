import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { format } from 'date-fns';
import { useAuth } from '../AuthContext';
import { announcementAPI, feedbackReviewsAPI, letterRequestAPI, notificationAPI, projectAPI, timesheetAPI, userAPI, vacationAPI } from '../api';
import Icon from '../components/Icon';
import { LoadingIndicator } from '../components/Hourglass';
import { buildDashboard } from '../utils/dashboard';
import './Dashboard.css';
import ActionRow from '../components/DashboardActionRow';
import EmployeeDashboard from './EmployeeDashboard';
import TimeSculpture from '../components/TimeSculpture';
import './ManagementDashboard.css';

export default function Dashboard() {
  const { user } = useAuth();
  const systemAdmin = user?.role === 'ADMIN';
  const manager = systemAdmin || user?.role === 'PROJECT_ADMIN' || user?.canManageProjects;
  const reviewer = manager || user?.canReviewProjects;
  const [state, setState] = useState({ data: {}, errors: [], loading: true });
  const [revision, setRevision] = useState(0);
  const [expanded, setExpanded] = useState(false);

  useEffect(() => {
    if (!user) return;
    let active = true;
    const now = new Date();
    const requests = {
      announcements: () => announcementAPI.list(),
      notifications: () => notificationAPI.getUnreadNotifications(),
      ...(!manager || systemAdmin ? { reviews: () => feedbackReviewsAPI.reviews() } : {}),
      ...(manager ? {
        projects: () => projectAPI.getProjects(),
        health: () => projectAPI.getHealth(),
      } : {
        projects: () => projectAPI.getAssignedProjects(),
        sheets: () => timesheetAPI.getMyTimesheets(),
        balance: () => userAPI.getLeaveBalance(user.id, now.getFullYear()),
      }),
      ...(reviewer && !systemAdmin ? { approvals: () => timesheetAPI.getPendingProjectSubmissions() } : {}),
      ...(systemAdmin ? {
        employees: () => userAPI.getAllUsers(),
        vacations: () => vacationAPI.getPendingRequests(),
        letters: () => letterRequestAPI.getPendingRequests(),
      } : {}),
    };
    const load = async () => {
      setState({ data: {}, errors: [], loading: true });
      const keys = Object.keys(requests);
      const results = await Promise.allSettled(keys.map(key => Promise.resolve().then(requests[key])));
      if (!active) return;
      const data = {}, errors = [];
      results.forEach((result, index) => {
        const key = keys[index];
        if (result.status === 'fulfilled' && (key === 'balance' ? result.value?.data && !Array.isArray(result.value.data) : Array.isArray(result.value?.data))) data[key] = result.value.data;
        else errors.push(key);
      });
      setState({ data, errors, loading: false });
    };
    load();
    const refresh = () => setRevision(value => value + 1);
    window.addEventListener('focus', refresh);
    return () => { active = false; window.removeEventListener('focus', refresh); };
  }, [user?.id, user?.role, manager, reviewer, revision]);

  const model = buildDashboard(state.data, user, new Date());
  const attention = expanded ? model.attention : model.attention.slice(0, 3);
  const news = model.announcements.slice(0, 2);
  const unavailable = state.loading ? 'Loading…' : 'Unavailable';

  if (!manager) return <EmployeeDashboard user={user} model={model} state={state} onRefresh={() => setRevision(value => value + 1)} />;

  return <div className={`page-container focus-dashboard employee-day management-day ${systemAdmin ? 'organization-day' : 'project-day'}`}>
    <header className="day-topline">
      <span><span className="day-live-dot" />{systemAdmin ? 'THE BIG PICTURE' : 'YOUR TEAM, IN SYNC'}</span><time dateTime={format(new Date(), 'yyyy-MM-dd')}>{format(new Date(), 'EEEE, MMMM d')}</time>
    </header>
    <section className="day-welcome" aria-label="Welcome">
      <div className="day-welcome-copy"><p className="day-greeting">Good to see you, {user?.firstName || 'there'}.</p><h1>{systemAdmin ? 'See the bigger picture.' : 'Great teams.'}<br /><em>{systemAdmin ? 'Make the next move.' : 'Good momentum.'}</em></h1><p className="day-intro">{systemAdmin ? 'A clear view of your people, projects, and the decisions that move them forward.' : 'Clear the little blockers. Give your team room to do their best work.'}</p>
        <div className="day-welcome-actions"><Link className="button day-primary" to={systemAdmin ? '/projects' : '/admin'}>{systemAdmin ? 'Explore projects' : 'Review approvals'}<Icon name="arrow" size={16} /></Link><Link className="day-secondary" to={systemAdmin ? '/users' : '/project-hours'}>{systemAdmin ? 'Your people' : 'Plan with your team'}<Icon name="arrow" size={15} /></Link></div>
      </div><TimeSculpture variant={systemAdmin ? 'compass' : 'team'} />
    </section>
    <section className="day-pulse management-pulse" aria-label="Workspace summary" aria-busy={state.loading}>
      {model.metrics.map(metric => <Link key={metric.label} to={metric.to} onClick={metric.to.includes('#') ? () => document.getElementById('needs-attention')?.scrollIntoView({ block: 'start' }) : undefined}>
        <strong>{state.loading || metric.value == null ? '—' : metric.value}</strong><span>{metric.label}<small>{state.loading || metric.value == null ? unavailable : metric.detail}</small></span><Icon name="arrow" size={14} />
      </Link>)}
    </section>
    {state.errors.length > 0 && <p className="dashboard-error" role="alert">Some information is unavailable ({state.errors.join(', ')}). Counts may be incomplete. <button type="button" onClick={() => setRevision(value => value + 1)}>Retry</button></p>}
    {/* Temporarily disabled: Needs Attention. Restore when work resumes.
    <section className="day-attention" id="needs-attention" aria-labelledby="dashboard-attention-title">
      <div className="day-section-title"><h2 id="dashboard-attention-title">Needs Attention {!state.loading && model.attention.length > 0 && <span>{model.actionCount}</span>}</h2><button type="button" className="management-refresh" disabled={state.loading} onClick={() => setRevision(value => value + 1)}>Refresh updates</button></div>
      {state.loading ? <div className="dashboard-loading"><LoadingIndicator label="Loading your dashboard…" /></div> : <>
        {attention.length ? <ul className="dashboard-rows">{attention.map(item => <ActionRow key={item.id} item={item} />)}</ul> : <p className="dashboard-clear"><Icon name="check" size={18} />{state.errors.length ? 'Refresh to confirm whether anything needs attention.' : 'You’re all caught up. No pending actions right now.'}</p>}
        {model.attention.length > 3 && <button type="button" className="dashboard-more" aria-expanded={expanded} onClick={() => setExpanded(value => !value)}>{expanded ? 'Show less' : `See ${model.attention.length - 3} more`}</button>}
      </>}
    </section>
    */}
    <div className="management-lower-grid">
    {!state.loading && manager && !systemAdmin && model.capacity.length > 0 && <section className="dashboard-section" aria-labelledby="dashboard-capacity-title">
      <div className="dashboard-section-heading"><div><h2 id="dashboard-capacity-title">Team Capacity</h2><p>Estimated allocation today · 8-hour weekdays, excluding leave and holidays.</p></div><Link to="/project-hours" className="text-action">Review capacity <Icon name="arrow" size={16} /></Link></div>
      <ul className="dashboard-rows">{model.capacity.slice(0, 3).map(item => <ActionRow key={item.id} item={item} />)}</ul>
      {model.capacity.length > 3 && <Link className="management-more-projects" to="/project-hours">Review {model.capacity.length - 3} more employees <Icon name="arrow" size={14} /></Link>}
    </section>}
    {!state.loading && news.length > 0 && <section className="dashboard-section day-news" aria-label="Company announcements">
      <div className="dashboard-section-heading"><div><h2>Announcements</h2><p>{systemAdmin ? 'Recent company updates.' : 'Important and unread updates.'}</p></div><Link className="text-action" to={systemAdmin ? '/announcements?manage=true' : '/announcements'}>{systemAdmin ? 'Manage announcements' : 'All announcements'} <Icon name="arrow" size={16} /></Link></div>
      <ul>{news.map(item => <li key={item.id}><Link to={`/announcements?id=${encodeURIComponent(item.id)}`}><span className="day-news-kicker">{item.priority === 'URGENT' ? 'Important update' : 'Around Maxwell'}{!item.viewed_at && <i title="Unread" />}</span><strong>{item.title}</strong><span className="day-news-read">Take a look <Icon name="arrow" size={15} /></span></Link></li>)}</ul>
    </section>}
    </div>
    {systemAdmin && !state.loading && !news.length && <Link className="management-more-projects" to="/announcements?manage=true">Manage announcements <Icon name="arrow" size={14} /></Link>}
  </div>;
}
