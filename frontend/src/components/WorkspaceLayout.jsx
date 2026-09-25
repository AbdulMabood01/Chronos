import React, { useEffect, useRef, useState } from 'react';
import { Link, NavLink, useLocation } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { notificationAPI } from '../api';
import { BrandLogo } from './Hourglass';
import Icon from './Icon';
import EmailAlertPreferences from './EmailAlertPreferences';

export default function WorkspaceLayout({ children, darkBackground, onToggleBackground }) {
  const { user, logout } = useAuth();
  const location = useLocation();
  const [unread, setUnread] = useState(0);
  const [menuOpen, setMenuOpen] = useState(false);
  const menuButton = useRef(null);
  const reviewer = user?.canReviewProjects || ['PROJECT_ADMIN', 'ADMIN'].includes(user?.role);
  const projectManager = user?.canManageProjects || ['PROJECT_ADMIN', 'ADMIN'].includes(user?.role);
  const operations = ['PROJECT_ADMIN', 'ADMIN'].includes(user?.role);
  const systemAdmin = user?.role === 'ADMIN';
  const primary = [
    ['/dashboard', 'Overview', 'grid'],
    ['/announcements', 'Announcements', 'bell'],
    ...(!systemAdmin ? [['/timesheets', 'Timesheets', 'clock'], ['/vacation', 'Time off', 'calendar']] : []),
    ['/requests', 'Letters & requests', 'file'],
    ...(!systemAdmin ? [['/workplace-reports', 'Reports', 'file']] : []),
    ...(reviewer && !projectManager ? [['/admin', 'Approvals', 'check']] : []),
    ['/notifications', 'Inbox', 'bell'],
  ];
  const management = [
    ...(projectManager ? [['/admin', 'Approvals', 'check']] : []),
    ...(projectManager ? [['/missing-timesheets', 'Missing timesheets', 'clock'], ['/team-leave-calendar', 'Team leave calendar', 'calendar']] : []),
    ...(projectManager ? [['/projects', 'Projects', 'briefcase']] : []),
    ...(systemAdmin ? [['/reports', 'Reports', 'file']] : []),
    ...(operations ? [['/time-reports', 'Time & leave reports', 'chart']] : []),
    ...(projectManager && !operations ? [['/project-hours', 'Project hours', 'chart']] : []),
    ...(systemAdmin ? [['/users', 'People', 'users'], ['/audit', 'Audit log', 'file'], ['/settings', 'Settings', 'settings']] : []),
  ];
  const development = [['/feedback', 'Feedback', 'users'], ['/performance-reviews', 'Performance Reviews', 'file']];
  const current = [...primary, ...development, ...management].find(([path]) => location.pathname === path || location.pathname.startsWith(path + '/'));
  const title = current?.[1] || (location.pathname.startsWith('/timesheet/') ? 'Timesheet details' : 'My profile');
  useEffect(() => {
    let active = true;
    const refresh = () => notificationAPI.getUnreadCount().then(response => { if (active) setUnread(Number(response.data || 0)); }).catch(() => {});
    refresh();
    window.addEventListener('chronos:notifications-changed', refresh);
    setMenuOpen(false);
    return () => { active = false; window.removeEventListener('chronos:notifications-changed', refresh); };
  }, [location.pathname, user]);
  useEffect(() => {
    const close = event => { if (event.key === 'Escape' && menuOpen) { setMenuOpen(false); menuButton.current?.focus(); } };
    window.addEventListener('keydown', close);
    return () => window.removeEventListener('keydown', close);
  }, [menuOpen]);
  const navItems = items => items.map(([path, label, icon]) => (
    <NavLink key={path} to={path} className={({ isActive }) => `workspace-nav-link${isActive || (path === '/timesheets' && location.pathname.startsWith('/timesheet/')) ? ' active' : ''}`}>
      <Icon name={icon}/><span>{label}</span>{path === '/notifications' && unread > 0 && <span className="inbox-count">{unread > 99 ? '99+' : unread}<span className="sr-only"> unread</span></span>}
    </NavLink>
  ));
  return <div className={`workspace-shell${menuOpen ? ' menu-open' : ''}`}>
    <a href="#workspace-main" className="skip-link">Skip to content</a>
    <aside className="workspace-sidebar" id="workspace-navigation">
      <Link to="/dashboard" className="workspace-brand" aria-label="Maxwell Chronos home"><BrandLogo/><span>CHRONOS<span>EMPLOYEE WORKSPACE</span></span></Link>
      <nav aria-label="Main navigation" className="workspace-navigation">
        <p className="nav-section-label">Workspace</p>{navItems(primary)}
        <p className="nav-section-label management-label">Feedback &amp; Performance Reviews</p>{navItems(development)}
        {management.length > 0 && <><p className="nav-section-label management-label">Management</p>{navItems(management)}</>}
      </nav>
      <div className="sidebar-bottom">
        <Link to="/profile" className="workspace-account">
          <span className="workspace-avatar">{user?.profileImageUrl ? <img src={user.profileImageUrl} alt=""/> : `${user?.firstName?.[0] || 'U'}${user?.lastName?.[0] || ''}`}</span>
          <span><strong>{user?.firstName} {user?.lastName}</strong><small>{(user?.role || 'EMPLOYEE').toLowerCase().replaceAll('_', ' ')}</small></span>
          <Icon name="arrow" size={16}/>
        </Link>
        <button className="sidebar-signout" onClick={logout}><Icon name="logout" size={17}/>Sign out</button>
      </div>
    </aside>
    <div className="workspace-body">
      <header className="workspace-topbar">
        <div className="workspace-breadcrumb"><button ref={menuButton} className="icon-button mobile-menu-toggle" onClick={() => setMenuOpen(!menuOpen)} aria-label={menuOpen ? 'Close navigation' : 'Open navigation'} aria-expanded={menuOpen} aria-controls="workspace-navigation"><Icon name={menuOpen ? 'close' : 'menu'}/></button><span>Workspace</span><span className="breadcrumb-divider">/</span><strong>{title}</strong></div>
        <div className="workspace-tools"><time dateTime={new Date().toISOString().slice(0, 10)}>{new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', year: 'numeric' }).format(new Date())}</time>
          <button className="icon-button" onClick={onToggleBackground} aria-label={darkBackground ? 'Switch to light theme' : 'Switch to dark theme'} title={darkBackground ? 'Light theme' : 'Dark theme'}><Icon name={darkBackground ? 'sun' : 'moon'}/></button>
          <EmailAlertPreferences key={user?.id} />
          <Link to="/notifications" className="icon-button topbar-inbox" aria-label={unread ? `Inbox, ${unread} unread notifications` : 'Inbox'}><Icon name="bell"/>{unread > 0 && <i/>}</Link>
        </div>
      </header>
      <main className="main-content workspace-content" id="workspace-main" tabIndex={-1}>{children}</main>
      <footer className="workspace-footer"><span>Maxwell Network Inc.</span><span>Your time. Well managed.</span></footer>
    </div>
  </div>;
}
