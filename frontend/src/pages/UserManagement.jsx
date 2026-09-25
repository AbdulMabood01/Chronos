import EmployeeOnboarding from '../components/EmployeeOnboarding';
import EmployeeImport from '../components/EmployeeImport';
import ScreenTitle, { RecordSearch } from '../components/ScreenTitle';
import React, { useEffect, useMemo, useState, useRef } from 'react';
import EmploymentDetails from '../components/EmploymentDetails';
import EmployeeProfile from '../components/EmployeeProfile';
import Icon from '../components/Icon';
import LeaveBalancePanel from '../components/LeaveBalancePanel';
import { useAuth } from '../AuthContext';
import { userAPI } from '../api';
import { LoadingIndicator } from '../components/Hourglass';
import '../styles.css';
import './UserManagement.css';

export default function UserManagement() {
  const { user } = useAuth();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [invitationBusy, setInvitationBusy] = useState(null);
  const [notice, setNotice] = useState('');
  const [search, setSearch] = useState('');
  const [addPanel, setAddPanel] = useState(null);
  const [profileUser, setProfileUser] = useState(null);
  const profileHeading = useRef(null);
  const [leaveOpen, setLeaveOpen] = useState(false);
  const [leaveSaving, setLeaveSaving] = useState(false);
  const [employmentSaving, setEmploymentSaving] = useState(false);
  useEffect(() => { if (profileUser) profileHeading.current?.focus(); }, [profileUser?.id]);
  const closeProfile = () => { if (!leaveSaving && !employmentSaving) { setProfileUser(null); setLeaveOpen(false); } };
  const saveProfileUser = updated => {
    setUsers(current => current.map(item => item.id === updated.id ? updated : item));
    setProfileUser(updated);
  };
  const visibleRecords = users.filter(record => [record.firstName,record.lastName,record.email,record.employeeId,record.role].join(' ').toLowerCase().includes(search.toLowerCase()));
  const canManageUsers = user?.role === 'ADMIN';
  const canManageRoles = user?.role === 'ADMIN';

  useEffect(() => {
    if (!canManageUsers) return;
    loadUsers();
  }, [canManageUsers]);

  const stats = useMemo(() => ({
    total: users.length,
    active: users.filter((item) => item.isActive).length,
    admins: users.filter((item) => item.role === 'PROJECT_ADMIN' || item.role === 'ADMIN').length,
  }), [users]);

  const loadUsers = async () => {
    setLoading(true);
    try {
      const response = await userAPI.getAllUsersAsAdmin();
      setUsers(response.data || []);
      setError('');
    } catch (err) {
      setError(err.response?.status === 403 ? 'Your account does not have access to User Management.'
        : err.response?.data?.message || 'Unable to load users. Please retry; if this continues, check the backend service.');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleInvitation = async (id, revoke = false) => {
    setInvitationBusy(id); setError(''); setNotice('');
    try {
      if (revoke) await userAPI.revokeInvitation(id);
      else await userAPI.sendInvitation(id);
      setNotice(revoke ? 'Invitation revoked.' : 'Invitation email sent. Any earlier link is now invalid.');
    } catch (err) { setError(err.response?.data?.message || 'Unable to update invitation.'); }
    finally { setInvitationBusy(null); }
  };

  const handleDeactivate = async (id) => {
    if (!window.confirm('Deactivate this user?')) return;
    try {
      await userAPI.deactivateUser(id);
      await loadUsers();
    } catch (err) {
      setError('Failed to deactivate user');
    }
  };

  const handleReactivate = async (id) => {
    try {
      await userAPI.reactivateUser(id);
      await loadUsers();
    } catch (err) {
      setError('Failed to reactivate user');
    }
  };

  const handleChangeRole = async (id, newRole) => {
    if (!window.confirm(`Change role to ${newRole.replace('_', ' ')}?`)) return;

    try {
      await userAPI.changeRole(id, newRole);
      await loadUsers();
    } catch (err) {
      setError('Failed to change role');
    }
  };

  if (!canManageUsers) {
    return (
      <div className="page-container">
        <div className="error-message">You do not have permission to access this page.</div>
      </div>
    );
  }

  if (loading) {
    return <div className="page-container"><div className="loading-panel"><LoadingIndicator label="Loading users..." /></div></div>;
  }

  if (profileUser) return (
    <div className="page-container user-management-page employee-workspace">
      <header className="employee-workspace-header">
        <div><span className="eyebrow">PEOPLE & EMPLOYMENT</span><h1 ref={profileHeading} tabIndex={-1}>{profileUser.firstName} {profileUser.lastName}</h1><p>Personal information, employment details, and leave in one place.</p></div>
        <button type="button" className="button button-secondary" disabled={leaveSaving || employmentSaving} onClick={closeProfile}>Back to users</button>
      </header>
      <div className="employee-workspace-columns">
        <EmployeeProfile user={profileUser} />
        <div className="employee-workspace-management">
          <EmploymentDetails key={profileUser.id} user={profileUser} editable onSaved={saveProfileUser} onSavingChange={setEmploymentSaving} />
          {profileUser.role !== 'ADMIN' && <section className="employee-section" aria-label="Leave allowance management">
            <div className="employee-section-heading"><div><span className="eyebrow">TIME OFF</span><h2>Leave allowance</h2><p>Manage annual entitlements and extra days.</p></div>
              <button type="button" className="button button-primary" aria-expanded={leaveOpen} aria-controls="profile-leave-allowance" disabled={leaveSaving} onClick={() => setLeaveOpen(!leaveOpen)}>{leaveOpen ? 'Hide allowance' : 'Leave allowance'}</button>
            </div>
            {leaveOpen && <div id="profile-leave-allowance"><LeaveBalancePanel userId={profileUser.id} editable onSavingChange={setLeaveSaving} /></div>}
          </section>}
        </div>
      </div>
    </div>
  );

  return (
    <div className="page-container admin-page user-management-page people-directory">
      <div className="header-bar">
        <div>
          <ScreenTitle title="People" icon="users" eyebrow="PEOPLE & ACCESS" />
          <p className="page-subtitle">Manage access, roles, and employment status.</p>
        </div>
        <div className="people-header-actions">
          <button className="button button-secondary" aria-expanded={addPanel === 'import'} onClick={() => setAddPanel(addPanel === 'import' ? null : 'import')}>Import employees</button>
          <button className="button button-primary" aria-expanded={addPanel === 'add'} onClick={() => setAddPanel(addPanel === 'add' ? null : 'add')}>Add employee</button>
        </div>
      </div>

      {addPanel === 'add' && <EmployeeOnboarding onCreated={async () => { setAddPanel(null); setNotice('Employee created. You can now send their invitation.'); await loadUsers(); }} />}
      {addPanel === 'import' && <EmployeeImport onImported={async count => { setAddPanel(null); setNotice(`${count} employee${count === 1 ? '' : 's'} imported. You can now send invitations from the directory.`); await loadUsers(); }} />}
      {notice && <p role="status">{notice}</p>}
      <div className="admin-stats user-stats">
        <div>
          <span>Total Users</span>
          <strong>{stats.total}</strong>
        </div>
        <div>
          <span>Active</span>
          <strong>{stats.active}</strong>
        </div>
        <div>
          <span>Admins</span>
          <strong>{stats.admins}</strong>
        </div>
      </div>

      <RecordSearch value={search} onChange={setSearch} placeholder="Search people by name, email or role" label="Search people by name, email or role" />
      {search && <p className="filter-count">{visibleRecords.length} matching records</p>}
      {error && <div className="error-message" role="alert">{error} <button type="button" className="button button-secondary" onClick={loadUsers}>Retry</button></div>}

      <div className="table-container admin-table">
        <table className="data-table">
          <thead>
            <tr>
              <th>Employee</th>
              <th>Role</th>
              <th>Status</th>
              <th>Profile</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {visibleRecords.length === 0 && <tr><td colSpan={5}><div className="empty-state">{search ? 'No people match your search.' : 'No employees yet. Add an employee or import a spreadsheet to get started.'}</div></td></tr>}
            {visibleRecords.map((targetUser) => (
              <tr key={targetUser.id}>
                <td>
                  <strong>{targetUser.firstName} {targetUser.lastName}</strong>
                  <p className="table-subtext">{targetUser.employeeId} · {targetUser.email}</p>
                </td>
                <td>
                  {canManageRoles ? (
                    <select
                      aria-label={`Role for ${targetUser.firstName} ${targetUser.lastName}`}
                      value={targetUser.role}
                      onChange={(event) => handleChangeRole(targetUser.id, event.target.value)}
                      disabled={targetUser.id === user.id}
                    >
                      <option value="EMPLOYEE">Employee</option>
                      <option value="PROJECT_ADMIN">Project Admin</option>
                      <option value="ADMIN">Admin</option>
                    </select>
                  ) : (
                    targetUser.role === 'ADMIN' ? 'Admin' : targetUser.role === 'PROJECT_ADMIN' ? 'Project Admin' : targetUser.role.replace('_', ' ')
                  )}
                </td>
                <td>
                  <span className={`status-badge ${targetUser.isActive ? 'status-approved' : 'status-rejected'}`}>
                    {targetUser.accountStatus || (targetUser.isActive ? 'Active' : 'Inactive')}
                  </span>
                </td>
                <td>
                  <button type="button" className="profile-action-button" onClick={() => setProfileUser(targetUser)} aria-label={`View and update ${targetUser.firstName} ${targetUser.lastName}'s profile`} title="View and update profile">
                    <Icon name="edit" size={15} />
                    <span>Profile</span>
                  </button>
                </td>
                <td>
                  <div className="action-buttons wrap-actions">
                    {targetUser.accountStatus === 'INVITED' && <>
                      <button className="button button-small" disabled={invitationBusy !== null} onClick={() => handleInvitation(targetUser.id)}>Send / resend invitation</button>
                      <button className="button button-small" disabled={invitationBusy !== null} onClick={() => handleInvitation(targetUser.id, true)}>Revoke invitation</button>
                    </>}
                    {targetUser.role !== 'ADMIN' && (targetUser.isActive ? (
                      <button className="button button-small button-danger" onClick={() => handleDeactivate(targetUser.id)}>
                        Deactivate
                      </button>
                    ) : (
                      <button className="button button-small button-success" onClick={() => handleReactivate(targetUser.id)}>
                        Reactivate
                      </button>
                    ))}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
