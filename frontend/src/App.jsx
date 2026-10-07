import { BrowserRouter as Router, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { Fragment, useEffect, useState } from 'react';
import { CompanyProvider, useCompany } from './CompanyContext';
import CompanySelection from './components/CompanySelection';
import { AuthProvider, useAuth } from './AuthContext';
import { GlobalApiLoader, LoadingIndicator } from './components/Hourglass';
import ProfileCompletionPrompt from './components/ProfileCompletionPrompt';
import Login from './pages/Login';
import RequestAccess from './pages/RequestAccess';
import Activate from './pages/Activate';
import { ForgotPassword, ResetPassword } from './pages/PasswordRecovery';
import Dashboard from './pages/WorkspaceOverview';
import { canOpenWorkspaceRoute } from './workspaceAccess';
import Announcements from './pages/Announcements';
import TimesheetDetail from './pages/TimesheetDetail';
import VacationRequests from './pages/CompanyLeave';
import EmployeeRequests from './pages/EmployeeRequests';
import LetterRequestReview from './pages/LetterRequestReview';
import AdminDashboard from './pages/AdminDashboard';
import Notifications from './pages/Notifications';
import AuditLog from './pages/AuditLog';
import SensitiveAccess from './pages/SensitiveAccess';
import LetterManagement from './pages/LetterManagement';
import {PlatformAccounts,PlatformAudit} from './pages/PlatformAdministration';
import ProjectManagement from './pages/ProjectManagement';
import Settings from './pages/Settings';
import Reports from './pages/Reports';
import EmployeeReports from './pages/EmployeeReports';
import Expenses from './pages/Expenses';
import Companies, { CompanyInvitation } from './pages/Companies';
import { FeedbackPage, PerformanceReviewsPage } from './pages/FeedbackReviews';
import Profile from './pages/Profile';
import { MissingTimesheetsPage, TeamLeaveCalendarPage } from './pages/TeamManagement';
import NotFound from './pages/NotFound';
import './styles.css';
import './workspace.css';
import Layout from './components/WorkspaceLayout';
import ConnectionAndSession from './components/ConnectionAndSession';

const THEME_STORAGE_KEY = 'chronos-dark-background';

function ProtectedRoute({ children }) {
  const location = useLocation();
  const { user, loading } = useAuth();
  const company = useCompany();

  if (loading) {
    return <div className="page-container"><div className="loading-panel"><LoadingIndicator /></div></div>;
  }

  if (!user) {
    return <Navigate to="/login" />;
  }

  if (company.loading || company.switching) {
    return <div className="page-container"><LoadingIndicator label="Loading workspace..." /></div>;
  }
  if ((!company.platformAdmin && !company.currentCompany) || (company.error && !company.companies.length)) {
    return <CompanySelection />;
  }

  if (!canOpenWorkspaceRoute(location.pathname, company)) {
    return <Navigate to="/dashboard" replace />;
  }
  return <Fragment key={`${user.id}:${company.currentCompany?.id || 'platform'}`}>{children}</Fragment>;
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
      <Route path="/employee-reports" element={<ProtectedRoute><Navigate to="/confidential-reports" replace /></ProtectedRoute>} />
      <Route path="/platform-accounts" element={<ProtectedRoute><Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}><PlatformAccounts /></Layout></ProtectedRoute>} />
      <Route path="/platform-audit" element={<ProtectedRoute><Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}><PlatformAudit /></Layout></ProtectedRoute>} />
      <Route path="/sensitive-access" element={<ProtectedRoute><Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}><SensitiveAccess /></Layout></ProtectedRoute>} />
      <Route path="/letter-management" element={<ProtectedRoute><Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}><LetterManagement /></Layout></ProtectedRoute>} />
      <Route path="/confidential-reports" element={<ProtectedRoute><Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}><EmployeeReports management /></Layout></ProtectedRoute>} />
      <Route path="/reports" element={<ProtectedRoute><Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}><Reports /></Layout></ProtectedRoute>} />
      <Route path="/leave-management" element={<ProtectedRoute><Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}><VacationRequests management /></Layout></ProtectedRoute>} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Navigate to="/request-access" replace />} />
      <Route path="/request-access" element={<RequestAccess />} />
      <Route path="/company-invite" element={user ? <Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}><CompanyInvitation /></Layout> : <CompanyInvitation />} />
      <Route path="/activate" element={<Activate />} />
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/reset-password" element={<ResetPassword />} />
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
      <Route path="/expenses" element={<ProtectedRoute><Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}><Expenses /></Layout></ProtectedRoute>} />
      <Route path="/platform-settings" element={<ProtectedRoute><Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}><Settings platform /></Layout></ProtectedRoute>} />
      <Route path="/companies" element={<ProtectedRoute><Layout darkBackground={darkBackground} onToggleBackground={onToggleBackground}><Companies /></Layout></ProtectedRoute>} />
      <Route path="/project-hours" element={<Navigate to="/projects" replace />} />
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
              {user?.role === 'ADMIN' ? <Navigate to="/dashboard" replace /> : <VacationRequests />}
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
              <Navigate to="/companies" replace />
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
        <CompanyProvider>
        <GlobalApiLoader />
        <ConnectionAndSession />
        <AppContent darkBackground={darkBackground} onToggleBackground={toggleBackground} />
        <ProfileCompletionPrompt />
        </CompanyProvider>
      </AuthProvider>
    </Router>
  );
}
