import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { companyAPI, projectAPI } from '../api';
import Icon from '../components/Icon';
import './Companies.css';

const errorText = error => error?.response?.data?.message || error?.userMessage || 'Request failed';
const roleName = role => (role || 'Member').replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, letter => letter.toUpperCase());
const shortDate = value => value ? String(value).slice(0, 10) : '—';
const invitationStatus = item => item.accepted_at ? 'Accepted' : item.revoked_at ? 'Revoked'
  : new Date(item.expires_at) < new Date() ? 'Expired' : 'Pending';

function EmptyState({ icon, title, description }) {
  return <div className="company-empty">
    <span className="company-empty-icon"><Icon name={icon} size={24} /></span>
    <strong>{title}</strong>
    <p>{description}</p>
  </div>;
}

export function CompanyInvitation() {
  const { user, logout, refreshUser } = useAuth();
  const navigate = useNavigate();
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [token, setToken] = useState(() => new URLSearchParams(window.location.hash.slice(1)).get('token')
    || sessionStorage.getItem('chronos:company-invite') || '');
  const [preview, setPreview] = useState(null);
  const [previewError, setPreviewError] = useState('');
  const [pending, setPending] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (token) sessionStorage.setItem('chronos:company-invite', token);
    if (window.location.hash) window.history.replaceState(window.history.state, '', '/company-invite');
    if (!token) { setLoading(false); return; }
    let cancelled = false;
    companyAPI.previewInvitation(token).then(response => {
      if (!cancelled) {
        if (response.data.status !== 'PENDING') sessionStorage.removeItem('chronos:company-invite');
        setPreview(response.data);
      }
    }).catch(error => {
      if (!cancelled) {
        if (error.response?.status === 400) sessionStorage.removeItem('chronos:company-invite');
        setPreviewError(errorText(error));
      }
    }).finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [token]);

  const refreshPending = async () => {
    const response = await companyAPI.myPendingInvitations();
    setPending(response.data || []);
  };
  useEffect(() => {
    if (user) refreshPending().catch(error => setMessage(errorText(error)));
    else setPending([]);
  }, [user]);

  const accept = async invitationId => {
    setBusy(true);
    try {
      if (invitationId) await companyAPI.acceptInvitation(invitationId);
      else await companyAPI.accept(token);
      if (!invitationId) {
        sessionStorage.removeItem('chronos:company-invite');
        setToken(''); setPreview(null); setPreviewError('');
      }
      await refreshUser();
      setPending(current => current.filter(item => item.id !== (invitationId || preview?.id)));
      setMessage('Invitation accepted. Your company access is ready.');
      refreshPending().catch(() => {});
    } catch (error) { setMessage(errorText(error)); }
    finally { setBusy(false); }
  };
  const signedInWithDifferentEmail = user && preview?.email?.toLowerCase() !== user.email?.toLowerCase();
  return <div className="page-container company-invite-page">
    <div className="company-invite-card">
      <span className="company-eyebrow">Company onboarding</span>
      <h1>{preview ? `Join ${preview.companyName}` : 'Your company invitations'}</h1>
      {loading && <p role="status">Loading invitation…</p>}
      {preview && <div className="company-invite-summary">
        <strong>{preview.companyName}</strong>
        <span>{roleName(preview.role)}{preview.projectName ? ` · ${preview.projectName}` : ''}</span>
        <span>Sent to {preview.email}</span>
      </div>}
      {previewError && <p role="alert" className="company-invite-note">{previewError}. Ask your company administrator for a new invitation.</p>}
      {preview?.status !== 'PENDING' && preview && <p className="company-invite-note">This invitation is {preview.status.toLowerCase()}. Ask your company administrator for a new one if you still need access.</p>}
      {!user && <>
        <p>{preview ? 'Use the invited email address to continue.' : 'Sign in to review invitations sent to your email address.'}</p>
        <div className="company-invite-actions">
          <Link className="button button-primary" to="/login" state={{ invitedEmail: preview?.email, companyName: preview?.companyName }}>Sign in</Link>
          <Link className="button button-secondary" to="/register" state={{ invitedEmail: preview?.email, companyName: preview?.companyName }}>Create an account</Link>
        </div>
      </>}
      {user && preview?.status === 'PENDING' && <>
        {signedInWithDifferentEmail ? <div className="company-invite-note">
          This invitation was sent to {preview.email}. You are signed in as {user.email}.
          <button type="button" className="button button-secondary" onClick={() => { logout(); navigate('/login', { state: { invitedEmail: preview.email, companyName: preview.companyName } }); }}>Sign in with the invited email</button>
        </div> : <button type="button" className="button button-primary" disabled={busy} onClick={() => accept()}>Accept invitation</button>}
      </>}
      {user && pending.some(item => item.id !== preview?.id) && <div className="company-invite-pending">
        <h2>{preview ? 'Other pending invitations' : 'Pending invitations'}</h2>
        {pending.filter(item => item.id !== preview?.id).map(item => <div className="company-invite-pending-row" key={item.id}>
          <div><strong>{item.companyName}</strong><span>{roleName(item.role)}{item.projectName ? ` · ${item.projectName}` : ''}</span></div>
          <button type="button" className="button button-secondary" disabled={busy} onClick={() => accept(item.id)}>Accept</button>
        </div>)}
      </div>}
      {user && !preview && pending.length === 0 && !loading && !message && <p>You have no pending invitations for {user.email}.</p>}
      {message && <p className="company-feedback" role="status">{message}</p>}
      {user && <Link className="company-invite-back" to="/companies">Go to companies</Link>}
    </div>
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
  const [tab, setTab] = useState('people');
  const [showCreate, setShowCreate] = useState(false);
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
  const pendingInvitations = invitations.filter(item => invitationStatus(item) === 'Pending').length;

  const load = async () => {
    const companyResponse = await companyAPI.mine();
    const projectResponse = await projectAPI.getProjects().catch(() => ({ data: [] }));
    setCompanies(companyResponse.data || []);
    setProjects(projectResponse.data || []);
    setSelected(current => current || String(companyResponse.data?.[0]?.id || ''));
  };
  useEffect(() => { load().catch(error => setMessage(errorText(error))); }, []);
  useEffect(() => {
    if (!selected) { setMembers([]); setGrants([]); setInvitations([]); return; }
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

  const run = async (action, success = 'Saved.', onSuccess) => {
    setBusy(true); setMessage('');
    try {
      const result = await action();
      await load();
      if (selected) {
        const [roster, permissions, pending] = await Promise.allSettled([
          companyAPI.members(selected), companyAPI.moderatorGrants(selected), companyAPI.invitations(selected),
        ]);
        if (roster.status === 'fulfilled') setMembers(roster.value.data || []);
        if (permissions.status === 'fulfilled') setGrants(permissions.value.data || []);
        if (pending.status === 'fulfilled') setInvitations(pending.value.data || []);
        if (roleProjectId) {
          const roleResponse = await companyAPI.projectRoles(selected, roleProjectId);
          setProjectRoles(roleResponse.data || []);
        }
      }
      onSuccess?.(result);
      setMessage(success);
    } catch (error) { setMessage(errorText(error)); }
    finally { setBusy(false); }
  };

  const changeCompany = event => {
    setSelected(event.target.value);
    setRoleProjectId('');
    setInvite(current => ({ ...current, projectId: '' }));
    setGrant(current => ({ ...current, projectId: '', userId: '' }));
    setMessage('');
  };

  return <div className="page-container companies-page">
    <header className="company-header">
      <div>
        <span className="company-eyebrow">Workspace administration</span>
        <h1>Companies</h1>
        <p>Manage the people, project roles, and approval access in your company.</p>
      </div>
      <div className="company-header-actions">
        <Link className="button button-secondary" to="/company-invite">My invitations</Link>
        {user?.role === 'ADMIN' && <button type="button" className="button button-secondary" onClick={() => setShowCreate(value => !value)}>
          {showCreate ? 'Cancel' : '+ New company'}
        </button>}
        {active && <button type="button" className="button button-primary" onClick={() => setTab('invitations')}>
          <Icon name="mail" size={17} /> Invite a person
        </button>}
      </div>
    </header>

    {message && <div className="company-feedback" role="status">{message}</div>}

    {showCreate && <form className="company-card company-create" onSubmit={event => {
      event.preventDefault();
      run(() => companyAPI.create(newCompany.name, newCompany.slug), 'Company created.', result => {
        if (result?.data?.id) setSelected(String(result.data.id));
        setNewCompany({ name: '', slug: '' });
        setShowCreate(false);
      });
    }}>
      <div className="company-section-heading"><div><h2>Create a company</h2><p>Set up a separate workspace for a new client.</p></div></div>
      <div className="company-form-grid">
        <label className="company-field">Company name<input required maxLength="150" value={newCompany.name} onChange={event => setNewCompany({ ...newCompany, name: event.target.value })} placeholder="Acme Corporation" /></label>
        <label className="company-field">Slug<input required pattern="[a-z0-9][a-z0-9-]{1,78}" value={newCompany.slug} onChange={event => setNewCompany({ ...newCompany, slug: event.target.value })} placeholder="acme-corporation" /></label>
      </div>
      <button className="button button-primary" disabled={busy}>Create company</button>
    </form>}

    {companies.length > 0 && <section className="company-overview" aria-label="Company overview">
      <div className="company-overview-top">
        <div className="company-identity">
          <div className="company-avatar" aria-hidden="true">{active?.name?.trim().charAt(0).toUpperCase() || 'C'}</div>
          <div><span className="company-eyebrow">Current company</span><h2>{active?.name || 'Select a company'}</h2><span className="company-plan">{roleName(active?.plan_tier)} plan</span></div>
        </div>
        <label className="company-switcher">Switch company<select value={selected} onChange={changeCompany}>
          {companies.map(company => <option key={company.id} value={company.id}>{company.name}</option>)}
        </select></label>
      </div>
      {active && <div className="company-stats">
        <div><span>Team members</span><strong>{members.length}</strong><small>{active.team_limit != null ? `of ${active.team_limit} allowed` : 'In this company'}</small></div>
        <div><span>Projects</span><strong>{companyProjects.length}</strong><small>{active.project_limit != null ? `of ${active.project_limit} allowed` : 'In this company'}</small></div>
        <div><span>Pending invites</span><strong>{pendingInvitations}</strong><small>Waiting to join</small></div>
      </div>}
    </section>}

    {!active && !showCreate && <EmptyState icon="briefcase" title="No company yet" description="Once you are invited to a company, it will appear here." />}

    {active && <>
      <nav className="company-tabs" aria-label="Company sections">
        {[
          ['people', 'People', 'users'],
          ['invitations', 'Invitations', 'mail'],
          ['roles', 'Project roles', 'briefcase'],
          ...(companyAdmin ? [['moderators', 'Moderator access', 'check']] : []),
        ].map(([key, label, icon]) => <button key={key} type="button" className={tab === key ? 'active' : ''}
          aria-current={tab === key ? 'page' : undefined} onClick={() => setTab(key)}>
          <Icon name={icon} size={17} />{label}{key === 'invitations' && pendingInvitations > 0 && <span className="company-tab-count">{pendingInvitations}</span>}
        </button>)}
      </nav>

      {tab === 'people' && <section className="company-card">
        <div className="company-section-heading"><div><h2>People</h2><p>Everyone with access to {active.name}.</p></div><span className="company-count">{members.length} members</span></div>
        {members.length ? <div className="company-list">{members.map(member => <div className="company-person-row" key={member.user_id}>
          <span className="company-person-avatar" aria-hidden="true">{(member.first_name || member.email || '?').charAt(0).toUpperCase()}</span>
          <div className="company-person-info"><strong>{[member.first_name, member.last_name].filter(Boolean).join(' ') || member.email}</strong><span>{member.email}</span></div>
          <div className="company-pills">{(member.roles || []).length ? member.roles.map(role => <span className="company-pill" key={role}>{roleName(role)}</span>) : <span className="company-pill">Member</span>}</div>
        </div>)}</div> : <EmptyState icon="users" title="No members yet" description="Invite someone to get your team started." />}
      </section>}

      {tab === 'invitations' && <div className="company-content-grid">
        <form className="company-card company-side-form" onSubmit={event => { event.preventDefault(); run(() => companyAPI.invite(selected, {
          email: invite.email, role: invite.role, projectId: invite.projectId ? Number(invite.projectId) : null,
        }), 'Invitation sent.', () => setInvite(current => ({ ...current, email: '' }))); }}>
          <div className="company-section-heading"><div><h2>Invite a person</h2><p>They can use the same email across multiple companies.</p></div></div>
          <label className="company-field">Email address<input type="email" required value={invite.email} onChange={event => setInvite({ ...invite, email: event.target.value })} placeholder="name@company.com" /></label>
          <label className="company-field">Role<select value={invite.role} onChange={event => setInvite({ ...invite, role: event.target.value,
            projectId: ['COMPANY_ADMIN', 'MODERATOR'].includes(event.target.value) ? '' : invite.projectId })}>
            {companyAdmin && <option value="COMPANY_ADMIN">Company Admin</option>}<option value="PROJECT_ADMIN">Project Admin</option>
            <option value="PROJECT_MANAGER">Project Manager</option>{companyAdmin && <option value="MODERATOR">Moderator</option>}
            <option value="USER">User</option></select></label>
          {!['COMPANY_ADMIN', 'MODERATOR'].includes(invite.role) && <label className="company-field">Project<select value={invite.projectId}
            onChange={event => setInvite({ ...invite, projectId: event.target.value })}>
            <option value="">{invite.role === 'PROJECT_ADMIN' && companyAdmin ? 'Company scope' : 'Select project'}</option>
            {companyProjects.map(project => <option key={project.id} value={project.id}>{project.name}</option>)}
          </select></label>}
          <button className="button button-primary" disabled={busy || ((['USER', 'PROJECT_MANAGER'].includes(invite.role)
            || invite.role === 'PROJECT_ADMIN' && !companyAdmin) && !invite.projectId)}>Send invitation</button>
        </form>
        <section className="company-card">
          <div className="company-section-heading"><div><h2>Invitations</h2><p>Track invitations sent for this company.</p></div><span className="company-count">{invitations.length} total</span></div>
          {invitations.length ? <div className="company-list">{invitations.map(item => {
            const status = invitationStatus(item);
            const projectName = companyProjects.find(project => String(project.id) === String(item.project_id))?.name;
            return <div className="company-record-row" key={item.id}>
              <div className="company-record-main"><strong>{item.invitee_email}</strong><span>{roleName(item.role_key)}{projectName ? ` · ${projectName}` : ''}</span></div>
              <span className={`company-status company-status-${status.toLowerCase()}`}>{status}</span>
              {status === 'Pending' && <button type="button" className="company-text-button" disabled={busy} onClick={() => run(() => companyAPI.revokeInvitation(selected, item.id), 'Invitation revoked.')}>Revoke</button>}
            </div>;
          })}</div> : <EmptyState icon="mail" title="No invitations yet" description="Invitations you send will appear here." />}
        </section>
      </div>}

      {tab === 'roles' && <section className="company-card">
        <div className="company-section-heading"><div><h2>Project roles</h2><p>Review assignments and project ownership.</p></div></div>
        <label className="company-field company-project-filter">Project<select value={roleProjectId} onChange={event => setRoleProjectId(event.target.value)}>
          <option value="">Select project</option>{companyProjects.map(project => <option key={project.id} value={project.id}>{project.name}</option>)}
        </select></label>
        {!companyProjects.length ? <EmptyState icon="briefcase" title="No projects available" description="Projects in this company will appear here." />
          : !roleProjectId ? <EmptyState icon="briefcase" title="Choose a project" description="Select a project to see its assigned roles." />
            : projectRoles.length ? <div className="company-list">{projectRoles.map(item => <div className="company-record-row" key={`${item.user_id}-${item.role_key}`}>
              <div className="company-record-main"><strong>{item.email}</strong><span>{roleName(item.role_key)}{item.owner && ' · Owner'}</span></div>
              <div className="company-row-actions">
                {companyAdmin && item.role_key === 'PROJECT_ADMIN' && !item.owner && <button type="button" className="company-text-button" disabled={busy}
                  onClick={() => run(() => companyAPI.transferOwner(selected, roleProjectId, item.user_id), 'Project owner updated.')}>Make owner</button>}
                {!item.owner && <button type="button" className="company-text-button company-text-danger" disabled={busy}
                  onClick={() => run(() => companyAPI.removeRole(selected, item.role_key, item.user_id, roleProjectId), 'Role removed.')}>Remove role</button>}
              </div>
            </div>)}</div> : <EmptyState icon="users" title="No roles assigned" description="People assigned to this project will appear here." />}
      </section>}

      {tab === 'moderators' && companyAdmin && <div className="company-content-grid">
        <form className="company-card company-side-form" onSubmit={event => { event.preventDefault(); run(() => companyAPI.grantModerator(selected, {
          ...grant, projectId: Number(grant.projectId), userId: Number(grant.userId),
        }), 'Moderator access granted.'); }}>
          <div className="company-section-heading"><div><h2>Grant approval access</h2><p>Limit a moderator to specific approvals and dates.</p></div></div>
          <label className="company-field">Project<select required value={grant.projectId} onChange={event => setGrant({ ...grant, projectId: event.target.value })}>
            <option value="">Select project</option>{companyProjects.map(project => <option key={project.id} value={project.id}>{project.name}</option>)}
          </select></label>
          <label className="company-field">Moderator<select required value={grant.userId} onChange={event => setGrant({ ...grant, userId: event.target.value })}>
            <option value="">Select moderator</option>{members.filter(member => (member.roles || []).includes('MODERATOR'))
              .map(member => <option key={member.user_id} value={member.user_id}>{member.email}</option>)}
          </select></label>
          <div className="company-field"><span>Can approve</span><div className="company-check-row">
            <label><input type="checkbox" checked={grant.timesheets} onChange={event => setGrant({ ...grant, timesheets: event.target.checked })} /> Timesheets</label>
            <label><input type="checkbox" checked={grant.expenses} onChange={event => setGrant({ ...grant, expenses: event.target.checked })} /> Expenses</label>
          </div></div>
          <div className="company-form-grid">
            <label className="company-field">Start date<input type="date" required value={grant.startsOn} onChange={event => setGrant({ ...grant, startsOn: event.target.value })} /></label>
            <label className="company-field">End date<input type="date" required value={grant.endsOn} onChange={event => setGrant({ ...grant, endsOn: event.target.value })} /></label>
          </div>
          <button className="button button-primary" disabled={busy || (!grant.timesheets && !grant.expenses)}>Grant access</button>
        </form>
        <section className="company-card">
          <div className="company-section-heading"><div><h2>Moderator access</h2><p>Current and past approval grants.</p></div><span className="company-count">{grants.length} grants</span></div>
          {grants.length ? <div className="company-list">{grants.map(item => <div className="company-record-row" key={item.id}>
            <div className="company-record-main"><strong>{item.email}</strong><span>{item.project_name} · {shortDate(item.starts_on)} to {shortDate(item.ends_on)}</span>
              <div className="company-pills">{item.timesheets && <span className="company-pill">Timesheets</span>}{item.expenses && <span className="company-pill">Expenses</span>}</div>
            </div>
            {item.revoked_at ? <span className="company-status company-status-revoked">Revoked</span>
              : <button type="button" className="company-text-button company-text-danger" disabled={busy}
                onClick={() => run(() => companyAPI.revokeModerator(selected, item.id), 'Moderator access revoked.')}>Revoke</button>}
          </div>)}</div> : <EmptyState icon="check" title="No moderator access yet" description="Approval grants you create will appear here." />}
        </section>
      </div>}
    </>}
  </div>;
}
