import { BrowserRouter as Router, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { AuthProvider, useAuth } from './AuthContext';
import { GlobalApiLoader, LoadingIndicator } from './components/Hourglass';
import ProfileCompletionPrompt from './components/ProfileCompletionPrompt';
import Login from './pages/Login';
import Activate from './pages/Activate';
import Dashboard from './pages/Dashboard';
import Announcements from './pages/Announcements';
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
import EmployeeReports from './pages/EmployeeReports';
import { FeedbackPage, PerformanceReviewsPage } from './pages/FeedbackReviews';
import Profile from './pages/Profile';
import { MissingTimesheetsPage, TeamLeaveCalendarPage } from './pages/TeamManagement';
import NotFound from './pages/NotFound';
import './styles.css';
import './workspace.css';
import Layout from './components/WorkspaceLayout';

const THEME_STORAGE_KEY = 'chronos-dark-background';

function ProtectedRoute({ children }) {
  const location = useLocation();
  const { user, loading } = useAuth();

  if (loading) {
    return <div className="page-container"><div className="loading-panel"><LoadingIndicator /></div></div>;
  }

  if (!user) {
    return <Navigate to="/login" />;
  }

  const operations = ['ADMIN', 'SUPER_ADMIN'].includes(user.role);
  const reviewer = operations || user.canReviewProjects;
  const projectManager = operations || user.canManageProjects;
  const path = location.pathname;
  if ((path === '/admin' || path.startsWith('/admin/')) && !reviewer
      || (['/projects', '/project-hours', '/missing-timesheets', '/team-leave-calendar'].some(p => path === p || path.startsWith(p + '/')) && !projectManager)
      || (path === '/time-reports' && !operations)
      || (['/settings', '/users', '/audit', '/employee-reports', '/reports'].some(p => path === p || path.startsWith(p + '/')) && user.role !== 'SUPER_ADMIN')) {
    return <Navigate to="/dashboard" replace />;
  }
  return children;
}

function AppContent({ darkBackground, onToggleBackground }) {
  const { loading, user } = useAuth();

  if (loading) {
    return <div className="page-container"><div className="loading-panel"><LoadingIndicator /></div></div>;
  }

  return (
    <Routes>
      <Route path="/announcements" element={<ProtectedRoute><Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}><Announcements /></Layout></ProtectedRoute>} />
      <Route path="/feedback" element={<ProtectedRoute><Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}><FeedbackPage /></Layout></ProtectedRoute>} />
      <Route path="/performance-reviews" element={<ProtectedRoute><Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}><PerformanceReviewsPage /></Layout></ProtectedRoute>} />
      <Route path="/workplace-reports" element={<ProtectedRoute><Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}><EmployeeReports /></Layout></ProtectedRoute>} />
      <Route path="/employee-reports" element={<ProtectedRoute><Navigate to="/reports" replace /></ProtectedRoute>} />
      <Route path="/reports" element={<ProtectedRoute><Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}><EmployeeReports management /></Layout></ProtectedRoute>} />
      <Route path="/login" element={<Login />} />
      <Route path="/activate" element={<Activate />} />
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
              {user?.role === 'SUPER_ADMIN' ? <Navigate to="/dashboard" replace /> : <TimesheetDetail openCurrentMonth showMonthScroller />}
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
              {user?.role === 'SUPER_ADMIN' ? <Navigate to="/dashboard" replace /> : <VacationRequests />}
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
      <Route path="/missing-timesheets" element={
        <ProtectedRoute><Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}><MissingTimesheetsPage /></Layout></ProtectedRoute>
      } />
      <Route path="/team-leave-calendar" element={
        <ProtectedRoute><Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}><TeamLeaveCalendarPage /></Layout></ProtectedRoute>
      } />
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
        path="/time-reports"
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
