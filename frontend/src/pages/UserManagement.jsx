import ScreenTitle, { RecordSearch } from '../components/ScreenTitle';
import React, { useEffect, useMemo, useState } from 'react';
import { useAuth } from '../AuthContext';
import { userAPI } from '../api';
import { LoadingIndicator } from '../components/Hourglass';
import '../styles.css';

export default function UserManagement() {
  const { user } = useAuth();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const visibleRecords = users.filter(record => [record.firstName,record.lastName,record.email,record.employeeId,record.role].join(' ').toLowerCase().includes(search.toLowerCase()));
  const canManageUsers = user?.role === 'SUPER_ADMIN';
  const canManageRoles = user?.role === 'SUPER_ADMIN';

  useEffect(() => {
    if (!canManageUsers) return;
    loadUsers();
  }, [canManageUsers]);

  const stats = useMemo(() => ({
    total: users.length,
    active: users.filter((item) => item.isActive).length,
    admins: users.filter((item) => item.role === 'ADMIN' || item.role === 'SUPER_ADMIN').length,
  }), [users]);

  const loadUsers = async () => {
    try {
      const response = await userAPI.getAllUsersAsAdmin();
      setUsers(response.data || []);
      setError('');
    } catch (err) {
      setError('Failed to load users');
      console.error(err);
    } finally {
      setLoading(false);
    }
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

  return (
    <div className="page-container admin-page">
      <div className="header-bar">
        <div>
          <ScreenTitle title="User Management" icon="users" eyebrow="PEOPLE & ACCESS" />
          <p className="page-subtitle">Manage access, roles, and employment status.</p>
        </div>
      </div>

      <RecordSearch value={search} onChange={setSearch} placeholder="Search people by name, email or role" label="Search people by name, email or role" />
      {search && <p className="filter-count">{visibleRecords.length} matching records</p>}
      {error && <div className="error-message" role="alert">{error}</div>}

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

      <div className="table-container admin-table">
        <table className="data-table">
          <thead>
            <tr>
              <th>Employee</th>
              <th>Role</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {visibleRecords.map((targetUser) => (
              <tr key={targetUser.id}>
                <td>
                  <strong>{targetUser.firstName} {targetUser.lastName}</strong>
                  <p className="table-subtext">{targetUser.employeeId} · {targetUser.email}</p>
                </td>
                <td>
                  {canManageRoles ? (
                    <select
                      value={targetUser.role}
                      onChange={(event) => handleChangeRole(targetUser.id, event.target.value)}
                      disabled={targetUser.id === user.id}
                    >
                      <option value="EMPLOYEE">Employee</option>
                      <option value="ADMIN">Admin</option>
                      <option value="SUPER_ADMIN">Super Admin</option>
                    </select>
                  ) : (
                    targetUser.role.replace('_', ' ')
                  )}
                </td>
                <td>
                  <span className={`status-badge ${targetUser.isActive ? 'status-approved' : 'status-rejected'}`}>
                    {targetUser.isActive ? 'Active' : 'Inactive'}
                  </span>
                </td>
                <td>
                  <div className="action-buttons wrap-actions">
                    {targetUser.isActive ? (
                      <button className="button button-small button-danger" onClick={() => handleDeactivate(targetUser.id)}>
                        Deactivate
                      </button>
                    ) : (
                      <button className="button button-small button-success" onClick={() => handleReactivate(targetUser.id)}>
                        Reactivate
                      </button>
                    )}
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
