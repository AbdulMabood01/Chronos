import React, { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { letterRequestAPI, notificationAPI, timesheetAPI, vacationAPI } from '../api';
import AdminDashboard from './AdminDashboard';
import { format } from 'date-fns';
import '../styles.css';
import Icon from '../components/Icon';
import './Dashboard.css';

export default function Dashboard() {
  const { user } = useAuth();
  const [timesheet, setTimesheet] = useState(null);
  const [vacations, setVacations] = useState([]);
  const [letterRequests, setLetterRequests] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    if (!user || user.role === 'SUPER_ADMIN') return;

    const loadHome = async () => {
      const now = new Date();
      try {
        const [timesheetRes, vacationRes, unreadRes, letterRes] = await Promise.all([
          timesheetAPI.getTimesheet(now.getFullYear(), now.getMonth() + 1),
          vacationAPI.getMyRequests(),
          notificationAPI.getUnreadCount(),
          letterRequestAPI.getMyRequests(),
        ]);
        setTimesheet(timesheetRes.data);
        setVacations(vacationRes.data || []);
        setUnreadCount(Number(unreadRes.data || 0));
        setLetterRequests(letterRes.data || []);
      } catch (err) {
        setError(true);
      } finally {
        setLoading(false);
      }
    };

    loadHome();
  }, [user]);

  const now = new Date();
  const monthName = format(now, 'MMMM yyyy');
  const monthProgress = Math.min(100, Math.round((now.getDate() / new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate()) * 100));
  const approvedVacationDays = useMemo(() => vacations
    .filter((request) => request.status === 'APPROVED' || request.status === 'LOCKED')
    .reduce((sum, request) => sum + (Number(request.hours || 0) / 8), 0), [vacations]);
  const activeVacation = vacations.find((request) => ['DRAFT', 'REJECTED'].includes(request.status));
  const submittedVacationCount = vacations.filter((request) => request.status === 'SUBMITTED').length;
  const readyLettersCount = letterRequests.filter((request) => request.status === 'APPROVED').length;

  if (user?.role === 'SUPER_ADMIN') {
    return <AdminDashboard />;
  }

  return (
    <div className="page-container employee-home">
      <div className="home-page-heading"><div><span className="eyebrow">YOUR WORKSPACE</span><h1>A little clarity for your day.</h1></div><span className="home-date">{format(now, 'EEEE, MMMM d')}</span></div>
      {error && <div className="error-message" role="alert">We couldn't load your workspace summary. Open Timesheets or Requests to view your records.</div>}
      <section className="employee-hero">
        <div className="hero-copy"><span className="hero-kicker"><span/>MAKE TIME FOR WHAT MATTERS</span>
          <h2>Welcome back,<br/>{user?.firstName || 'there'}.</h2>
          <p>Keep your hours, time off, and requests<br className="desktop-break"/> together. Get on with your best work.</p>
          <Link className="button hero-action" to="/timesheets">Open my timesheet <Icon name="arrow" size={18}/></Link>
        </div>
        <div className="hero-month"><div className="month-scene"><div className="month-orbit month-orbit-outer" aria-hidden="true"/><div className="month-orbit month-orbit-inner" aria-hidden="true"/><div className="month-ring" style={{ '--progress': monthProgress + '%' }}><div><small>MONTH ELAPSED</small><strong>{monthProgress}<span>%</span></strong></div></div></div><strong>{monthName}</strong><span>{new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate() - now.getDate()} days remaining</span></div>
      </section>
      <div className="section-heading"><h2>Your month at a glance</h2><span>{monthName}</span></div>
      <section className="employee-home-grid" aria-label="Workspace summary" aria-busy={loading}>
        <Link className="employee-command-card" to="/timesheets"><div className="metric-heading"><span className="metric-icon"><Icon name="clock"/></span><Icon name="arrow" size={17}/></div><span>Hours logged</span><strong>{loading || error ? '\u2014' : Number(timesheet?.totalHours || 0).toFixed(1)}<small> hrs</small></strong><p>{timesheet?.status ? timesheet.status.replaceAll('_', ' ').toLowerCase() : 'View your current timesheet'}</p></Link>
        <Link className="employee-command-card" to="/vacation"><div className="metric-heading"><span className="metric-icon"><Icon name="calendar"/></span><Icon name="arrow" size={17}/></div><span>Approved time off</span><strong>{loading || error ? '\u2014' : approvedVacationDays.toFixed(1)}<small> days</small></strong><p>Across all requests &middot; {submittedVacationCount} pending</p></Link>
        <Link className="employee-command-card" to="/notifications"><div className="metric-heading"><span className="metric-icon"><Icon name="bell"/></span><Icon name="arrow" size={17}/></div><span>Your inbox</span><strong>{loading || error ? '\u2014' : unreadCount}<small> unread</small></strong><p>Updates on your approvals</p></Link>
        <Link className="employee-command-card" to="/requests"><div className="metric-heading"><span className="metric-icon"><Icon name="file"/></span><Icon name="arrow" size={17}/></div><span>Approved letters</span><strong>{loading || error ? '\u2014' : readyLettersCount}<small> ready</small></strong><p>View and download your letters</p></Link>
      </section>
      <section className="home-lower-grid">
        <div className="home-action-panel"><span className="eyebrow">UP NEXT</span><h2>{activeVacation ? 'A little time away starts here.' : timesheet?.status === 'REJECTED' ? 'Your timesheet needs a second look.' : 'Keep your month up to date.'}</h2><p>{activeVacation ? 'Pick up your time-off request where you left it.' : timesheet?.status === 'REJECTED' ? 'Review the feedback, update your hours, and resubmit.' : 'A few minutes today makes the end of the month easier.'}</p><Link className="text-action" to={activeVacation ? '/vacation' : '/timesheets'}>{activeVacation ? 'Continue request' : 'Review my timesheet'}<Icon name="arrow" size={18}/></Link></div>
        <div className="home-shortcuts"><h2>What would you like to do?</h2>{[['/vacation', 'Plan some time off', 'Request vacation and track its approval.', 'calendar'], ['/requests', 'Request a company letter', 'Employment, travel, and vacation letters.', 'file'], ['/profile', 'Keep your profile current', 'Review your personal and contact details.', 'users']].map(([to,title,detail,icon]) => <Link key={to} to={to}><span className="shortcut-icon"><Icon name={icon}/></span><span><strong>{title}</strong><small>{detail}</small></span><Icon name="arrow" size={17}/></Link>)}</div>
      </section>
    </div>
  );
}
