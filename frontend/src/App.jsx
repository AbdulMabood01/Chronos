import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { AuthProvider, useAuth } from './AuthContext';
import { GlobalApiLoader, LoadingIndicator } from './components/Hourglass';
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
import './workspace.css';
import Layout from './components/WorkspaceLayout';

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
