import { BrowserRouter as Router, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { AuthProvider, useAuth } from './AuthContext';
import { notificationAPI } from './api';
import { BrandLogo, GlobalApiLoader, LoadingIndicator } from './components/Hourglass';
import ProfileCompletionPrompt from './components/ProfileCompletionPrompt';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import TimesheetDetail from './pages/TimesheetDetail';
import VacationRequests from './pages/VacationRequests';
import EmployeeRequests from './pages/EmployeeRequests';
import LetterRequestReview from './pages/LetterRequestReview';
import AdminDashboard from './pages/AdminDashboard';
import Notifications from './pages/Notifications';
import AuditLog from './pages/AuditLog';
import UserManagement from './pages/UserManagement';
import ProjectManagement from './pages/ProjectManagement';
import ProjectHoursDashboard from './pages/ProjectHoursDashboard';
import Settings from './pages/Settings';
import Reports from './pages/Reports';
import Profile from './pages/Profile';
import NotFound from './pages/NotFound';
import './styles.css';

const THEME_STORAGE_KEY = 'chronos-dark-background';

function ProtectedRoute({ children }) {
  const { user, loading } = useAuth();

  if (loading) {
    return <div className="page-container"><div className="loading-panel"><LoadingIndicator /></div></div>;
  }

  if (!user) {
    return <Navigate to="/login" />;
  }

  return children;
}

function Layout({ children, darkBackground, onToggleBackground }) {
  const { user, logout } = useAuth();
  const location = useLocation();
  const [hasUnreadNotifications, setHasUnreadNotifications] = useState(false);
  const isProjectReviewer = ['PROJECT_MANAGER', 'ADMIN', 'SUPER_ADMIN'].includes(user?.role);
  const canManageAdmin = user?.role === 'SUPER_ADMIN';
  const canManageOps = ['ADMIN', 'SUPER_ADMIN'].includes(user?.role);

  useEffect(() => {
    if (!user) return;

    const refreshUnreadCount = async () => {
      try {
        if (location.pathname === '/notifications') {
          await notificationAPI.markAllAsRead();
          setHasUnreadNotifications(false);
          return;
        }

        const response = await notificationAPI.getUnreadCount();
        setHasUnreadNotifications(Number(response.data || 0) > 0);
      } catch (err) {
        console.error('Failed to load notification count:', err);
      }
    };

    refreshUnreadCount();
  }, [location.pathname, user]);

  return (
    <div className="app-container">
      <nav className="navbar">
        <div className="navbar-brand">
          <BrandLogo />
        </div>
        {user && (
          <div className="navbar-menu">
            <a href="/dashboard">Dashboard</a>
            <a href="/timesheets">Timesheets</a>
            <a href="/vacation">Vacation</a>
            <div className="navbar-dropdown">
              <a href="/requests" className="navbar-dropdown-trigger">Requests</a>
              <div className="navbar-dropdown-menu">
                <a href="/requests?type=EMPLOYMENT_VERIFICATION">Employment verification letter</a>
                <a href="/requests?type=TRAVEL">Travel letter</a>
                <a href="/requests?type=VACATION">Vacation letter</a>
              </div>
            </div>
            <a href="/notifications" className="notification-nav-link">
              Notifications
              {hasUnreadNotifications && <span className="notification-dot" aria-label="Unread notifications" />}
            </a>
            {isProjectReviewer && <a href="/admin">Approvals</a>}
            {(isProjectReviewer || canManageOps || canManageAdmin) && (
              <div className="navbar-dropdown">
                <a href={canManageOps ? '/projects' : '/project-hours'} className="navbar-dropdown-trigger">Manage</a>
                <div className="navbar-dropdown-menu">
                  {canManageOps && <a href="/projects">Projects</a>}
                  {isProjectReviewer && !canManageOps && <a href="/project-hours">Project Hours</a>}
                  {canManageAdmin && <a href="/users">Users</a>}
                  {canManageOps && <a href="/reports">Reports</a>}
                  {canManageAdmin && <a href="/audit">Audit Log</a>}
                </div>
              </div>
            )}
            <div className="navbar-user">
              {canManageOps && (
                <a className="navbar-icon-link" href="/settings" aria-label="Settings" title="Settings">{'\u2699'}</a>
              )}
              <button
                aria-label={darkBackground ? 'Switch to light background' : 'Switch to dark background'}
                className="theme-toggle-button"
                onClick={onToggleBackground}
                title={darkBackground ? 'Light background' : 'Dark background'}
                type="button"
              >
                {darkBackground ? 'Light' : 'Dark'}
              </button>
              <div className="navbar-dropdown account-dropdown">
                <button className="navbar-avatar-button navbar-dropdown-trigger" type="button" aria-label="Open profile menu">
                  <span className="navbar-avatar" aria-hidden="true">
                    {user.profileImageUrl ? (
                      <img src={user.profileImageUrl} alt="" />
                    ) : (
                      `${(user.firstName || 'U').charAt(0)}${(user.lastName || '').charAt(0)}`
                    )}
                  </span>
                </button>
                <div className="navbar-dropdown-menu">
                  <span className="navbar-account-name">{user.firstName} {user.lastName}</span>
                  <a href="/profile">Profile</a>
                  <button onClick={logout} type="button">Logout</button>
                </div>
              </div>
            </div>
          </div>
        )}
      </nav>
      <main className="main-content">
        {children}
      </main>
    </div>
  );
}

function AppContent({ darkBackground, onToggleBackground }) {
  const { loading, user } = useAuth();

  if (loading) {
    return <div className="page-container"><div className="loading-panel"><LoadingIndicator /></div></div>;
  }

  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/" element={user ? <Navigate to="/dashboard" /> : <Navigate to="/login" />} />
      <Route
        path="/dashboard"
        element={
          <ProtectedRoute>
            <Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}>
              <Dashboard />
            </Layout>
          </ProtectedRoute>
        }
      />
      <Route
        path="/projects"
        element={
          <ProtectedRoute>
            <Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}>
              <ProjectManagement />
            </Layout>
          </ProtectedRoute>
        }
      />
      <Route
        path="/project-hours"
        element={
          <ProtectedRoute>
            <Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}>
              <ProjectHoursDashboard />
            </Layout>
          </ProtectedRoute>
        }
      />
      <Route
        path="/timesheets"
        element={
          <ProtectedRoute>
            <Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}>
              <TimesheetDetail openCurrentMonth showMonthScroller />
            </Layout>
          </ProtectedRoute>
        }
      />
      <Route
        path="/timesheet/:id"
        element={
          <ProtectedRoute>
            <Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}>
              <TimesheetDetail />
            </Layout>
          </ProtectedRoute>
        }
      />
      <Route
        path="/vacation"
        element={
          <ProtectedRoute>
            <Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}>
              <VacationRequests />
            </Layout>
          </ProtectedRoute>
        }
      />
      <Route
        path="/requests"
        element={
          <ProtectedRoute>
            <Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}>
              <EmployeeRequests />
            </Layout>
          </ProtectedRoute>
        }
      />
      <Route
        path="/admin"
        element={
          <ProtectedRoute>
            <Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}>
              <AdminDashboard />
            </Layout>
          </ProtectedRoute>
        }
      />
      <Route
        path="/admin/letter-request/:id"
        element={
          <ProtectedRoute>
            <Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}>
              <LetterRequestReview />
            </Layout>
          </ProtectedRoute>
        }
      />
      <Route
        path="/notifications"
        element={
          <ProtectedRoute>
            <Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}>
              <Notifications />
            </Layout>
          </ProtectedRoute>
        }
      />
      <Route
        path="/profile"
        element={
          <ProtectedRoute>
            <Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}>
              <Profile />
            </Layout>
          </ProtectedRoute>
        }
      />
      <Route
        path="/users"
        element={
          <ProtectedRoute>
            <Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}>
              <UserManagement />
            </Layout>
          </ProtectedRoute>
        }
      />
      <Route
        path="/reports"
        element={
          <ProtectedRoute>
            <Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}>
              <Reports />
            </Layout>
          </ProtectedRoute>
        }
      />
      <Route
        path="/audit"
        element={
          <ProtectedRoute>
            <Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}>
              <AuditLog />
            </Layout>
          </ProtectedRoute>
        }
      />
      <Route
        path="/settings"
        element={
          <ProtectedRoute>
            <Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}>
              <Settings />
            </Layout>
          </ProtectedRoute>
        }
      />
      <Route path="*" element={<NotFound />} />
    </Routes>
  );
}

export default function App() {
  const [darkBackground, setDarkBackground] = useState(() => localStorage.getItem(THEME_STORAGE_KEY) === 'true');

  useEffect(() => {
    document.body.classList.toggle('theme-dark', darkBackground);
    localStorage.setItem(THEME_STORAGE_KEY, String(darkBackground));
  }, [darkBackground]);

  const toggleBackground = () => {
    setDarkBackground((current) => !current);
  };

  return (
    <Router>
      <AuthProvider>
        <GlobalApiLoader />
        <AppContent darkBackground={darkBackground} onToggleBackground={toggleBackground} />
        <ProfileCompletionPrompt />
      </AuthProvider>
    </Router>
  );
}
