import React, { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { letterRequestAPI, notificationAPI, timesheetAPI, vacationAPI } from '../api';
import AdminDashboard from './AdminDashboard';
import { format } from 'date-fns';
import { LoadingIndicator } from '../components/Hourglass';
import '../styles.css';

export default function Dashboard() {
  const { user } = useAuth();
  const [timesheet, setTimesheet] = useState(null);
  const [vacations, setVacations] = useState([]);
  const [letterRequests, setLetterRequests] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(true);

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
        console.error('Failed to load employee dashboard:', err);
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
      <section className="employee-hero">
        <div>
          <span className="eyebrow">Today is {format(now, 'EEEE, MMM d')}</span>
          <h1>Welcome back, {user?.firstName}</h1>
          <p className="page-subtitle">
            {loading ? <LoadingIndicator label="Loading your month..." /> : `Your ${monthName} workspace is ready.`}
          </p>
        </div>
        <div className="month-progress-card">
          <span>Month Progress</span>
          <strong>{monthProgress}%</strong>
          <div className="progress-track">
            <div style={{ width: `${monthProgress}%` }} />
          </div>
        </div>
      </section>

      <section className="employee-home-grid">
        <Link className="employee-command-card primary-command" to="/timesheets">
          <span>Timesheet</span>
          <strong>{timesheet?.status ? timesheet.status.replace('_', ' ') : 'Open Month'}</strong>
          <p>{Number(timesheet?.totalHours || 0).toFixed(2)} hours logged for {monthName}</p>
        </Link>

        <Link className="employee-command-card" to="/vacation">
          <span>Vacation</span>
          <strong>{approvedVacationDays.toFixed(1)} approved days</strong>
          <p>{submittedVacationCount} request{submittedVacationCount === 1 ? '' : 's'} waiting for approval.</p>
        </Link>

        <Link className="employee-command-card" to="/notifications">
          <span>Inbox</span>
          <strong>{unreadCount} unread</strong>
          <p>Review approvals, rejections, and requested changes.</p>
        </Link>

        <Link className="employee-command-card" to="/requests">
          <span>Requests</span>
          <strong>{readyLettersCount} ready</strong>
          <p>Submit employment, travel, and vacation letters for approval.</p>
        </Link>
      </section>

      <section className="home-focus-strip">
        <div>
          <span>Next Best Action</span>
          <strong>
            {activeVacation
              ? 'Finish your vacation request'
              : timesheet?.status === 'DRAFT'
                ? 'Update this month\'s hours'
                : timesheet?.status === 'REJECTED'
                  ? 'Fix and resubmit your timesheet'
                  : 'Check your current timesheet'}
          </strong>
        </div>
        <Link className="button button-primary" to={activeVacation ? '/vacation' : '/timesheets'}>
          Open
        </Link>
      </section>
    </div>
  );
}
