import { useEffect, useState } from 'react';
import { useAuth } from '../AuthContext';
import { companyAPI, projectAPI } from '../api';

const errorText = error => error?.response?.data?.message || error?.userMessage || 'Request failed';

export function CompanyInvitation() {
  const { user } = useAuth();
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const token = new URLSearchParams(window.location.hash.slice(1)).get('token') || sessionStorage.getItem('chronos:company-invite');
  useEffect(() => { if (token) sessionStorage.setItem('chronos:company-invite', token); }, [token]);
  const accept = async () => {
    if (!token) { setMessage('This invitation link is invalid.'); return; }
    setBusy(true);
    try {
      await companyAPI.accept(token);
      sessionStorage.removeItem('chronos:company-invite');
      window.history.replaceState(null, '', '/company-invite');
      setMessage('Invitation accepted. Your company access is ready.');
    } catch (error) { setMessage(errorText(error)); }
    finally { setBusy(false); }
  };
  return <div className="page-container"><h1>Company invitation</h1>
    <p>Sign in with the email address that received this invitation.</p>
    {!user ? <a href="/login">Sign in or create an account</a>
      : <button className="button button-primary" disabled={busy || !token} onClick={accept}>Join company</button>}
    {message && <p role="status">{message}</p>}
  </div>;
}

export default function Companies() {
  const { user } = useAuth();
  const [companies, setCompanies] = useState([]);
  const [projects, setProjects] = useState([]);
  const [members, setMembers] = useState([]);
  const [grants, setGrants] = useState([]);
  const [invitations, setInvitations] = useState([]);
  const [selected, setSelected] = useState('');
  const [invite, setInvite] = useState({ email: '', role: 'USER', projectId: '' });
  const [grant, setGrant] = useState({ projectId: '', userId: '', timesheets: true, expenses: false, startsOn: '', endsOn: '' });
  const [newCompany, setNewCompany] = useState({ name: '', slug: '' });
  const [roleProjectId, setRoleProjectId] = useState('');
  const [projectRoles, setProjectRoles] = useState([]);
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const active = companies.find(company => String(company.id) === String(selected));
  const companyProjects = projects.filter(project => String(project.companyId) === String(selected));
  const companyAdmin = user?.role === 'ADMIN' || members.some(member => member.user_id === user?.id
    && (member.roles || []).includes('COMPANY_ADMIN'));
  const load = async () => {
    const companyResponse = await companyAPI.mine();
    const projectResponse = await projectAPI.getProjects().catch(() => ({ data: [] }));
    setCompanies(companyResponse.data || []);
    setProjects(projectResponse.data || []);
    setSelected(current => current || String(companyResponse.data?.[0]?.id || ''));
  };
  useEffect(() => { load().catch(error => setMessage(errorText(error))); }, []);
  useEffect(() => {
    if (!selected) return;
    companyAPI.members(selected).then(response => setMembers(response.data || []))
      .catch(() => setMembers([]));
    companyAPI.moderatorGrants(selected).then(response => setGrants(response.data || []))
      .catch(() => setGrants([]));
    companyAPI.invitations(selected).then(response => setInvitations(response.data || []))
      .catch(() => setInvitations([]));
  }, [selected]);
  useEffect(() => {
    if (!selected || !roleProjectId) { setProjectRoles([]); return; }
    companyAPI.projectRoles(selected, roleProjectId).then(response => setProjectRoles(response.data || []))
      .catch(() => setProjectRoles([]));
  }, [selected, roleProjectId]);
  const run = async action => {
    setBusy(true); setMessage('');
    try { await action(); await load(); if (selected) {
      const [roster, permissions, pending] = await Promise.allSettled([companyAPI.members(selected), companyAPI.moderatorGrants(selected), companyAPI.invitations(selected)]);
      if (roster.status === 'fulfilled') setMembers(roster.value.data || []);
      if (permissions.status === 'fulfilled') setGrants(permissions.value.data || []);
      if (pending.status === 'fulfilled') setInvitations(pending.value.data || []);
      if (roleProjectId) {
        const roleResponse = await companyAPI.projectRoles(selected, roleProjectId);
        setProjectRoles(roleResponse.data || []);
      }
    } setMessage('Saved.'); }
    catch (error) { setMessage(errorText(error)); }
    finally { setBusy(false); }
  };
  return <div className="page-container">
    <h1>Companies and roles</h1>
    <p>People can join more than one company with the same account. Invitations are tied to their email address.</p>
    {user?.role === 'ADMIN' && <form onSubmit={event => { event.preventDefault(); run(() => companyAPI.create(newCompany.name, newCompany.slug)); }}>
      <h2>Onboard a company</h2>
      <label>Company name <input required maxLength="150" value={newCompany.name} onChange={event => setNewCompany({ ...newCompany, name: event.target.value })} /></label>
      <label>Slug <input required pattern="[a-z0-9][a-z0-9-]{1,78}" value={newCompany.slug} onChange={event => setNewCompany({ ...newCompany, slug: event.target.value })} /></label>
      <button className="button button-primary" disabled={busy}>Create company</button>
    </form>}
    <label>Company <select value={selected} onChange={event => setSelected(event.target.value)}>
      {companies.map(company => <option key={company.id} value={company.id}>{company.name}</option>)}
    </select></label>
    {active && <><p>Plan: {active.plan_tier} · Project limit: {active.project_limit} · Team limit: {active.team_limit}</p>
      <h2>Members</h2>
      <ul>{members.map(member => <li key={member.user_id}>{member.first_name} {member.last_name} ({member.email}) · {(member.roles || []).join(', ') || 'Member'}</li>)}</ul>
      <h2>Project roles</h2>
      <label>Project <select value={roleProjectId} onChange={event => setRoleProjectId(event.target.value)}>
        <option value="">Select project</option>{companyProjects.map(project => <option key={project.id} value={project.id}>{project.name}</option>)}
      </select></label>
      {roleProjectId && <ul>{projectRoles.map(item => <li key={`${item.user_id}-${item.role_key}`}>
        {item.email} · {item.role_key}{item.owner && ' · Owner'}
        {!item.owner && <button type="button" className="button button-secondary" disabled={busy}
          onClick={() => run(() => companyAPI.removeRole(selected, item.role_key, item.user_id, roleProjectId))}>Remove role</button>}
        {companyAdmin && item.role_key === 'PROJECT_ADMIN' && !item.owner && <button type="button" className="button button-secondary" disabled={busy}
          onClick={() => run(() => companyAPI.transferOwner(selected, roleProjectId, item.user_id))}>Make owner</button>}
      </li>)}</ul>}
      <form onSubmit={event => { event.preventDefault(); run(() => companyAPI.invite(selected, {
        email: invite.email, role: invite.role, projectId: invite.projectId ? Number(invite.projectId) : null,
      })); }}>
        <h2>Invite a person</h2>
        <label>Email <input type="email" required value={invite.email} onChange={event => setInvite({ ...invite, email: event.target.value })} /></label>
        <label>Role <select value={invite.role} onChange={event => setInvite({ ...invite, role: event.target.value,
          projectId: ['COMPANY_ADMIN', 'MODERATOR'].includes(event.target.value) ? '' : invite.projectId })}>
          {companyAdmin && <option value="COMPANY_ADMIN">Company Admin</option>}<option value="PROJECT_ADMIN">Project Admin</option>
          <option value="PROJECT_MANAGER">Project Manager</option>{companyAdmin && <option value="MODERATOR">Moderator</option>}
          <option value="USER">User</option></select></label>
        {!['COMPANY_ADMIN', 'MODERATOR'].includes(invite.role) && <label>Project <select value={invite.projectId}
          onChange={event => setInvite({ ...invite, projectId: event.target.value })}>
          <option value="">{invite.role === 'PROJECT_ADMIN' && companyAdmin ? 'Company scope' : 'Select project'}</option>
          {companyProjects.map(project => <option key={project.id} value={project.id}>{project.name}</option>)}
        </select></label>}
        <button className="button button-primary" disabled={busy || ((['USER', 'PROJECT_MANAGER'].includes(invite.role)
          || invite.role === 'PROJECT_ADMIN' && !companyAdmin) && !invite.projectId)}>Send invitation</button>
      </form>
      {invitations.length > 0 && <><h2>Invitations</h2><ul>{invitations.map(item => <li key={item.id}>
        {item.invitee_email} · {item.role_key} · {item.accepted_at ? 'Accepted' : item.revoked_at ? 'Revoked' : new Date(item.expires_at) < new Date() ? 'Expired' : 'Pending'}
        {!item.accepted_at && !item.revoked_at && new Date(item.expires_at) >= new Date() &&
          <button type="button" className="button button-secondary" disabled={busy} onClick={() => run(() => companyAPI.revokeInvitation(selected, item.id))}>Revoke</button>}
      </li>)}</ul></>}
      {companyAdmin && <form onSubmit={event => { event.preventDefault(); run(() => companyAPI.grantModerator(selected, {
        ...grant, projectId: Number(grant.projectId), userId: Number(grant.userId),
      })); }}>
        <h2>Moderator approval access</h2>
        <label>Project <select required value={grant.projectId} onChange={event => setGrant({ ...grant, projectId: event.target.value })}>
          <option value="">Select project</option>{companyProjects.map(project => <option key={project.id} value={project.id}>{project.name}</option>)}
        </select></label>
        <label>Moderator <select required value={grant.userId} onChange={event => setGrant({ ...grant, userId: event.target.value })}>
          <option value="">Select moderator</option>{members.filter(member => (member.roles || []).includes('MODERATOR'))
            .map(member => <option key={member.user_id} value={member.user_id}>{member.email}</option>)}
        </select></label>
        <label><input type="checkbox" checked={grant.timesheets} onChange={event => setGrant({ ...grant, timesheets: event.target.checked })} /> Timesheets</label>
        <label><input type="checkbox" checked={grant.expenses} onChange={event => setGrant({ ...grant, expenses: event.target.checked })} /> Expenses</label>
        <label>Start <input type="date" required value={grant.startsOn} onChange={event => setGrant({ ...grant, startsOn: event.target.value })} /></label>
        <label>End <input type="date" required value={grant.endsOn} onChange={event => setGrant({ ...grant, endsOn: event.target.value })} /></label>
        <button className="button button-primary" disabled={busy || (!grant.timesheets && !grant.expenses)}>Grant access</button>
      </form>}
      {companyAdmin && grants.length > 0 && <ul>{grants.map(item => <li key={item.id}>
        {item.email} · {item.project_name} · {item.timesheets ? 'Timesheets ' : ''}{item.expenses ? 'Expenses' : ''}
        {' · '}{String(item.starts_on).slice(0, 10)} to {String(item.ends_on).slice(0, 10)}
        {item.revoked_at ? ' · Revoked' : <button type="button" className="button button-secondary" disabled={busy}
          onClick={() => run(() => companyAPI.revokeModerator(selected, item.id))}>Revoke</button>}
      </li>)}</ul>}
    </>}
    {message && <p role="status">{message}</p>}
  </div>;
}
