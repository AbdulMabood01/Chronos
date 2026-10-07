import React, { useEffect, useRef, useState } from 'react';
import { Link, NavLink, useLocation } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { useCompany } from '../CompanyContext';
import { notificationAPI } from '../api';
import { BrandLogo } from './Hourglass';
import Icon from './Icon';
import EmailAlertPreferences from './EmailAlertPreferences';
import { workspaceNavigation, workspaceRoleLabel } from '../workspaceAccess';

const titleCase = value => value.replace(/\b[a-z]/g, letter => letter.toUpperCase());

export default function WorkspaceLayout({ children, darkBackground, onToggleBackground }) {
  const { user, logout } = useAuth();
  const companyContext = useCompany();
  const { companies, currentCompany, platformAdmin, switching, error: companyError, selectCompany, refreshCompanies } = companyContext;
  const location = useLocation();
  const [unread, setUnread] = useState(0);
  const [menuOpen, setMenuOpen] = useState(false);
  const menuButton = useRef(null);
  const { primary, management, personal } = workspaceNavigation(companyContext);
  const current = [...primary, ...personal, ...management].find(([path]) => location.pathname === path || location.pathname.startsWith(path + '/'));
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
      <Icon name={icon}/><span>{titleCase(label)}</span>{path === '/notifications' && unread > 0 && <span className="inbox-count">{unread > 99 ? '99+' : unread}<span className="sr-only"> unread</span></span>}
    </NavLink>
  ));
  return <div className={`workspace-shell${menuOpen ? ' menu-open' : ''}`}>
    <a href="#workspace-main" className="skip-link">Skip to content</a>
    <aside className="workspace-sidebar" id="workspace-navigation">
      <Link to="/dashboard" className="workspace-brand" aria-label="Maxwell Chronos home"><BrandLogo/><span>CHRONOS<span>{platformAdmin ? 'Platform Administration' : 'Company Workspace'}</span></span></Link>
      <nav aria-label="Main navigation" className="workspace-navigation">
        <p className="nav-section-label">{platformAdmin ? 'Platform' : 'Workspace'}</p>{navItems(primary)}
        {management.length > 0 && <><p className="nav-section-label management-label">Management</p>{navItems(management)}</>}
        <p className="nav-section-label management-label">Personal</p>{navItems(personal)}
      </nav>
      <div className="sidebar-bottom">
        <Link to="/profile" className="workspace-account">
          <span className="workspace-avatar">{user?.profileImageUrl ? <img src={user.profileImageUrl} alt=""/> : `${user?.firstName?.[0] || 'U'}${user?.lastName?.[0] || ''}`}</span>
          <span><strong>{user?.firstName} {user?.lastName}</strong><small>{titleCase(workspaceRoleLabel(companyContext))}</small></span>
          <Icon name="arrow" size={16}/>
        </Link>
        <button className="sidebar-signout" onClick={logout}><Icon name="logout" size={17}/>Sign Out</button>
      </div>
    </aside>
    <div className="workspace-body">
      <header className="workspace-topbar">
        <div className="workspace-breadcrumb"><button ref={menuButton} className="icon-button mobile-menu-toggle" onClick={() => setMenuOpen(!menuOpen)} aria-label={menuOpen ? 'Close navigation' : 'Open navigation'} aria-expanded={menuOpen} aria-controls="workspace-navigation"><Icon name={menuOpen ? 'close' : 'menu'}/></button><span>Workspace</span><span className="breadcrumb-divider">/</span><strong>{titleCase(title)}</strong></div>
        <div className="workspace-tools"><time dateTime={new Date().toISOString().slice(0, 10)}>{new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', year: 'numeric' }).format(new Date())}</time>
          <label className="workspace-company-selector">
            <select aria-label="Current company" value={currentCompany?.id || ''} disabled={switching || !companies.length}
              onChange={event => selectCompany(event.target.value).catch(() => {})}>
              <option value="">{platformAdmin ? 'Platform view' : 'Choose company'}</option>
              {companies.map(company => <option value={company.id} key={company.id}>{company.name}</option>)}
            </select>
          </label>
          <button className="icon-button" onClick={onToggleBackground} aria-label={darkBackground ? 'Switch to light theme' : 'Switch to dark theme'} title={darkBackground ? 'Light theme' : 'Dark theme'}><Icon name={darkBackground ? 'sun' : 'moon'}/></button>
          <EmailAlertPreferences key={user?.id} />
          <Link to="/notifications" className="icon-button topbar-inbox" aria-label={unread ? `Inbox, ${unread} unread notifications` : 'Inbox'}><Icon name="bell"/>{unread > 0 && <i/>}</Link>
        </div>
      </header>
      <main className="main-content workspace-content" id="workspace-main" tabIndex={-1}>
        {companyError && <div className="inline-alert" role="alert">{companyError} <button className="button button-secondary" onClick={refreshCompanies}>Refresh access</button></div>}
        {children}
      </main>
      <footer className="workspace-footer"><span>Maxwell Network Inc.</span><span>Your time. Well managed.</span></footer>
    </div>
  </div>;
}
