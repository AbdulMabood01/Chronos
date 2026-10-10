import {TERMS_VERSION} from './Legal';
import PlatformCompanyDirectory from '../components/PlatformCompanyDirectory';
import { useEffect, useState, useRef } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { useCompany } from '../CompanyContext';
import { companyAPI, projectAPI } from '../api';
import Icon from '../components/Icon';
import ScreenTitle from '../components/ScreenTitle';
import './Companies.css';
import './PlatformAdministration.css';
import CompanyEmploymentDetails from '../components/CompanyEmploymentDetails';
import CompanyMemberProfile from '../components/CompanyMemberProfile';
import {PlatformCompanyTools} from './PlatformAdministration';

const errorText = error => error?.response?.data?.message || error?.userMessage || 'Request failed';
const roleName = role => (role || 'Member').replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, letter => letter.toUpperCase());
const shortDate = value => value ? String(value).slice(0, 10) : '—';
const invitationStatus = item => item.accepted_at ? 'Accepted' : item.revoked_at ? 'Revoked'
  : new Date(item.expires_at) < new Date() ? 'Expired' : 'Pending';

const companyRoleOptions=[['COMPANY_ADMIN','Company admin'],['PROJECT_ADMIN','Company project admin']];
const companyRoleLabel=role=>companyRoleOptions.find(([key])=>key===role)?.[1]||roleName(role);

function MemberConfirmation({action,company,busy,error,onConfirm,onCancel}){
  const ref=useRef(null);
  useEffect(()=>{const dialog=ref.current;if(dialog.showModal)dialog.showModal();else dialog.setAttribute('open','');
    return()=>{dialog.close?.();};},[]);
  const removal=action.status==='REMOVED';
  const title=action.role ? `Remove ${companyRoleLabel(action.role)} role` : removal?'Remove company membership':'Reactivate company membership';
  return <dialog ref={ref} className="company-member-dialog" aria-labelledby="member-action-title" onCancel={event=>{event.preventDefault();if(!busy)onCancel();}}>
    <h2 id="member-action-title">{title}</h2><p>{action.member.email} in {company.name}</p>
    <p>{action.role ? 'This removes the selected company role. Other roles remain.' : removal ?
      'Company roles and project access will be revoked. Historical records and the shared login account will be kept. Other companies are unaffected.' :
      'Membership will be restored. Previous company roles and project access stay revoked; assign the required roles again.'}</p>
    {error&&<p role="alert" className="error-message">{error}</p>}
    <div className="company-selection-actions"><button className="button button-secondary" autoFocus disabled={busy} onClick={onCancel}>Cancel</button>
      <button className="button button-primary" disabled={busy} onClick={onConfirm}>{busy?'Saving...':action.role?'Remove role':removal?'Remove access':'Reactivate access'}</button></div>
  </dialog>;
}

function EmptyState({ icon, title, description }) {
  return <div className="company-empty">
    <span className="company-empty-icon"><Icon name={icon} size={24} /></span>
    <strong>{title}</strong>
    <p>{description}</p>
  </div>;
}

function EmploymentDialog({member,onClose,onSaved,profile=false}) {
  const dialog=useRef(null);
  useEffect(()=>{const previous=document.activeElement;dialog.current?.showModal();return()=>{dialog.current?.close();previous?.focus();};},[]);
  return <dialog ref={dialog} className="company-employment-dialog" aria-labelledby="employment-dialog-title" onCancel={event=>{event.preventDefault();onClose();}}>
    <header><div><h2 id="employment-dialog-title">{profile ? 'View Profile' : 'Employment details'}</h2><p>{[member?.first_name,member?.last_name].filter(Boolean).join(' ')||member?.email}</p></div><button type="button" className="button button-secondary" onClick={onClose}>{profile ? 'Close profile' : 'Close employment details'}</button></header>
    {profile && <section className="company-member-profile-summary" aria-label="Member profile"><p><strong>Email</strong><span>{member?.email}</span></p><p><strong>Membership</strong><span>{member?.status}</span></p></section>}
    {profile ? <><CompanyMemberProfile userId={member?.user_id} onSaved={onSaved}/><CompanyEmploymentDetails userId={member?.user_id} editable onSaved={onSaved}/></> : <CompanyEmploymentDetails userId={member?.user_id} editable onSaved={onSaved}/>}
  </dialog>;
}
export function CompanyInvitation() {
  const [acceptedTerms,setAcceptedTerms]=useState(false);
  const { user, login, logout, refreshUser } = useAuth();
  const navigate = useNavigate();
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [token, setToken] = useState(() => new URLSearchParams(window.location.hash.slice(1)).get('token')
    || sessionStorage.getItem('chronos:company-invite') || '');
  const [preview, setPreview] = useState(null);
  const [previewError, setPreviewError] = useState('');
  const [pending, setPending] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [account, setAccount] = useState({ firstName: '', lastName: '', password: '' });

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
      window.dispatchEvent(new Event('chronos:company-memberships-changed'));
      setPending(current => current.filter(item => item.id !== (invitationId || preview?.id)));
      setMessage('Invitation accepted. Your company access is ready.');
      refreshPending().catch(() => {});
    } catch (error) { setMessage(errorText(error)); }
    finally { setBusy(false); }
  };
  const claim = async event => {
    event.preventDefault();if(!acceptedTerms){setMessage('Accept the Terms of Use before continuing.');return;} setBusy(true); setMessage('');
    try {
      await companyAPI.claimInvitation({termsVersion:TERMS_VERSION, token, ...account });
      sessionStorage.removeItem('chronos:company-invite');
      setToken(''); setPreview(null);
      try {
        await login(preview.email, account.password);
        window.dispatchEvent(new Event('chronos:company-memberships-changed'));
        navigate('/dashboard');
      } catch {
        setMessage('Account created and invitation accepted. Sign in with your new password.');
      }
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
          {preview?.status === 'PENDING' && <button type="button" className="button button-secondary" onClick={() => setShowCreate(value => !value)}>Create an account</button>}
        </div>
        {showCreate && preview?.status === 'PENDING' && <form className="company-claim-form" onSubmit={claim}>
          <h2>Create your account and join</h2>
          <p>This invitation verifies {preview.email}. Choose a password to join {preview.companyName} as {roleName(preview.role)}.</p>
          <label className="company-field">First name<input required maxLength={100} autoComplete="given-name" value={account.firstName} onChange={event => setAccount({ ...account, firstName: event.target.value })} /></label>
          <label className="company-field">Last name<input required maxLength={100} autoComplete="family-name" value={account.lastName} onChange={event => setAccount({ ...account, lastName: event.target.value })} /></label>
          <label className="company-field">Password<input required type="password" minLength={12} maxLength={72} autoComplete="new-password" value={account.password} onChange={event => setAccount({ ...account, password: event.target.value })} /></label>
          <small>At least 12 characters, including uppercase, lowercase and a number.</small>
          <label><input type="checkbox" required checked={acceptedTerms} onChange={e=>setAcceptedTerms(e.target.checked)}/> I agree to the <Link to="/legal/terms">Terms of Use</Link>.</label><p><Link to="/legal/privacy">Privacy Policy</Link></p>
          <button className="button button-primary" disabled={busy}>{busy ? 'Creating account...' : 'Create account and join'}</button>
        </form>}
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
  const navigate = useNavigate();
  const location = useLocation();
  const { companies, currentCompany, selectCompany, refreshCompanies, error: companyError,
    companyCapabilities, companyRoles, platformCapabilities, platformAdmin } = useCompany();
  const [projects, setProjects] = useState([]);
  const [members, setMembers] = useState([]);
  const [grants, setGrants] = useState([]);
  const [invitations, setInvitations] = useState([]);
  const [accessRequests, setAccessRequests] = useState([]);
  const selected = currentCompany ? String(currentCompany.id) : '';
  const [tab, setTab] = useState('people');
  const [showCreate, setShowCreate] = useState(false);
  const [invite, setInvite] = useState({ email: '', role: 'USER', projectId: '' });
  const [grant, setGrant] = useState({ projectId: '', userId: '', timesheets: true, expenses: false, startsOn: '', endsOn: '' });
  const [newCompany, setNewCompany] = useState({ name: '', slug: '', adminEmail: '' });
  const [employmentUser,setEmploymentUser]=useState(null);
  const [profileDialog,setProfileDialog]=useState(false);
  const [peopleSearch,setPeopleSearch]=useState('');
  const [peopleStatus,setPeopleStatus]=useState('ACTIVE');
  const [roleDrafts,setRoleDrafts]=useState({});
  const [memberAction,setMemberAction]=useState(null);
  const [roleProjectId, setRoleProjectId] = useState('');
  const [projectRoles, setProjectRoles] = useState([]);
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  const active = companies.find(company => String(company.id) === String(selected));
  const companyProjects = projects.filter(project => String(project.companyId) === String(selected));
  const companyAdmin = companyCapabilities?.canManageCompanyPeople === true;
  const canViewRoster = !platformAdmin && (companyAdmin || companyRoles?.includes('PROJECT_ADMIN'));
  const activeAdmins=members.filter(member=>member.status==='ACTIVE'&&member.account_available===true&&member.platform_account!==true&&(member.roles||[]).includes('COMPANY_ADMIN')).length;
  const isLastAdmin=member=>activeAdmins===1&&member.status==='ACTIVE'&&member.account_available===true&&member.platform_account!==true&&(member.roles||[]).includes('COMPANY_ADMIN');
  const shownMembers=members.filter(member=>(companyAdmin||member.status==='ACTIVE')&&(peopleStatus==='ALL'||member.status===peopleStatus))
    .filter(member=>!peopleSearch.trim()||['email','first_name','last_name','employee_id','job_title'].some(field=>String(member[field]||'').toLowerCase().includes(peopleSearch.trim().toLowerCase())));
  const pendingInvitations = invitations.filter(item => invitationStatus(item) === 'Pending').length;

  const load = async () => {
    await refreshCompanies();
    const projectResponse = !platformAdmin && companyCapabilities?.canViewProjects
      ? await projectAPI.getProjects().catch(() => ({ data: [] })) : { data: [] };
    setProjects(projectResponse.data || []);
  };
  useEffect(() => { load().catch(error => setMessage(errorText(error))); }, []);
  useEffect(() => {
    let cancelled = false;
    setMembers([]); setGrants([]); setInvitations([]); setAccessRequests([]);
    setRoleProjectId(''); setProjectRoles([]);
    setInvite({ email: '', role: 'USER', projectId: '' });
    setGrant({ projectId: '', userId: '', timesheets: true, expenses: false, startsOn: '', endsOn: '' });
    if (!selected || !canViewRoster) return;
    Promise.allSettled([companyAPI.members(selected), companyAPI.moderatorGrants(selected),
      companyAPI.invitations(selected), companyAPI.accessRequests(selected)]).then(results => {
      if (cancelled) return;
      const data = index => results[index].status === 'fulfilled' ? results[index].value.data || [] : [];
      setMembers(data(0)); setGrants(data(1)); setInvitations(data(2)); setAccessRequests(data(3));
    });
    return () => { cancelled = true; };
  }, [selected, canViewRoster,companyAdmin]);
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
      if (selected && canViewRoster) {
        const [roster, permissions, pending, requests] = await Promise.allSettled([
          companyAPI.members(selected), companyAPI.moderatorGrants(selected), companyAPI.invitations(selected), companyAPI.accessRequests(selected),
        ]);
        if (roster.status === 'fulfilled') setMembers(roster.value.data || []);
        if (permissions.status === 'fulfilled') setGrants(permissions.value.data || []);
        if (pending.status === 'fulfilled') setInvitations(pending.value.data || []);
        if (requests.status === 'fulfilled') setAccessRequests(requests.value.data || []);
        if (roleProjectId) {
          const roleResponse = await companyAPI.projectRoles(selected, roleProjectId);
          setProjectRoles(roleResponse.data || []);
        }
      }
      await onSuccess?.(result);
      setMessage(success);
    } catch (error) { setMessage(errorText(error)); }
    finally { setBusy(false); }
  };

  const changeCompany = event => {
    selectCompany(event.target.value).catch(() => {});
    setRoleProjectId('');
    setInvite(current => ({ ...current, email: '', projectId: '', accessRequestId: null }));
    setGrant(current => ({ ...current, projectId: '', userId: '' }));
    setEmploymentUser(null);setMemberAction(null);setRoleDrafts({});setPeopleSearch('');setPeopleStatus('ACTIVE');
    setMessage('');
  };

  const inviteRequester = request => {
    setInvite({ email: request.email, role: 'USER', projectId: '', accessRequestId: request.id });
    setTab('invitations');
    setMessage(`Choose a project and role for ${request.email}, then send the invitation.`);
  };

  return <div className={`page-container companies-page${!platformAdmin ? ' people-access-page' : ' platform-page highlighted-workspace'}`}>
    <header className="company-header">
      {platformAdmin ? <ScreenTitle title="Companies" icon="briefcase" eyebrow="PLATFORM ADMINISTRATION" description="Manage company workspaces, complimentary plans and initial admin invitations." /> : <ScreenTitle title={companyAdmin ? 'People & access' : 'Workspace'} icon="users" eyebrow="MANAGEMENT" description="Manage your team, invitations, and access in one place." />}
      <div className="company-header-actions">
        <Link className="button button-secondary" to="/company-invite">My invitations</Link>
        {platformCapabilities?.canCreateCompanies === true && <button type="button" className="button button-secondary" onClick={() => setShowCreate(value => !value)}>
          {showCreate ? 'Cancel' : '+ New company'}
        </button>}
        {active && canViewRoster && <button type="button" className="button button-primary" onClick={() => setTab('invitations')}>
          <Icon name="mail" size={17} /> Invite a person
        </button>}
      </div>
    </header>

    {message && <div className="company-feedback" role="status">{message}</div>}
    {location.state?.createdCompanyId === currentCompany?.id && <div className="company-feedback" role="status">Company created. The initial company admin invitation is queued for delivery.</div>}
    {companyError && <p className="error-message" role="alert">{companyError}</p>}

    {showCreate && platformCapabilities?.canCreateCompanies === true && <form className="company-card company-create" onSubmit={event => {
      event.preventDefault();
      run(() => companyAPI.create(newCompany.name.trim(), newCompany.slug.trim().toLowerCase(), newCompany.adminEmail.trim()), 'Company created. The initial company admin invitation is queued for delivery.', async result => {
        if (result?.data?.id) await selectCompany(result.data.id);
        if (result?.data?.id) navigate('/companies', { replace:true, state:{ createdCompanyId:result.data.id } });
        setNewCompany({ name: '', slug: '', adminEmail: '' });
        setShowCreate(false);
        setTab('invitations');
      });
    }}>
      <div className="company-section-heading"><div><h2>Create a company</h2><p>Create a workspace and invite its first company admin.</p></div></div>
      <div className="company-form-grid">
        <label className="company-field">Company name<input required maxLength="150" value={newCompany.name} onChange={event => setNewCompany({ ...newCompany, name: event.target.value })} placeholder="Acme Corporation" /></label>
        <label className="company-field">Workspace ID<input required minLength={2} maxLength={79} pattern="[a-z0-9][a-z0-9\-]{1,78}" title="Use 2 to 79 lowercase letters, numbers or hyphens, starting with a letter or number." value={newCompany.slug} onChange={event => setNewCompany({ ...newCompany, slug: event.target.value.toLowerCase() })} placeholder="acme-corporation" /><small>Unique ID using letters, numbers and hyphens. Share it with people requesting access.</small></label>
        <label className="company-field">Initial company admin email<input aria-label="Initial company admin email" required type="email" maxLength={255} autoComplete="email" value={newCompany.adminEmail} onChange={event => setNewCompany({ ...newCompany, adminEmail: event.target.value })} placeholder="admin@acme.com" /><small>They will receive an invitation to choose their password and manage this company.</small></label>
      </div>
      <button className="button button-primary" disabled={busy}>{busy ? 'Creating company and queuing invitation...' : 'Create company and invite admin'}</button>
    </form>}

    {platformAdmin && <PlatformCompanyDirectory />}
    {companies.length > 0 && <section className="company-overview" aria-label="Company overview">
      <div className="company-overview-top">
        <div className="company-identity">
          <div className="company-avatar" aria-hidden="true">{active?.name?.trim().charAt(0).toUpperCase() || 'C'}</div>
          <div><span className="company-eyebrow">Current company</span><h2>{active?.name || 'Select a company'}</h2><span className="company-plan">{roleName(active?.plan_tier)} plan</span>
            {companyAdmin && <span className="company-workspace-id">Workspace ID: <strong>{active?.slug}</strong></span>}</div>
        </div>
        {platformAdmin&&<label className="company-switcher">Switch company<select value={selected} onChange={changeCompany}>
          <option value="">Select company</option>
          {companies.map(company => <option key={company.id} value={company.id}>{company.name}</option>)}
        </select></label>}
      </div>
      {active && canViewRoster && <div className="company-stats">
        <div><span>Team members</span><strong>{members.filter(member=>member.status==='ACTIVE').length}</strong><small>Active in this company</small></div>
        <div><span>Open projects</span><strong>{companyProjects.filter(p=>!['COMPLETED','ARCHIVED'].includes(p.status)).length}</strong><small>{active.project_limit != null ? `of ${active.project_limit} allowed` : 'In this company'}</small></div>
        <div><span>Pending invites</span><strong>{pendingInvitations}</strong><small>Waiting to join</small></div>
      </div>}
    </section>}

    {platformAdmin && active && <PlatformCompanyTools/>}
    {!active && !showCreate && <EmptyState icon="briefcase" title="No company yet" description="Once you are invited to a company, it will appear here." />}

    {active && canViewRoster && <>
      <nav className="company-tabs" aria-label="Company sections">
        {[
          ['people', 'People', 'users'],
          ['invitations', 'Invitations', 'mail'],
          ['roles', 'Project roles', 'briefcase'],
          
        ].map(([key, label, icon]) => <button key={key} type="button" className={tab === key ? 'active' : ''}
          aria-current={tab === key ? 'page' : undefined} onClick={() => setTab(key)}>
          <Icon name={icon} size={17} />{label}{key === 'invitations' && pendingInvitations > 0 && <span className="company-tab-count">{pendingInvitations}</span>}
        </button>)}
      </nav>

      {employmentUser&&companyAdmin&&<EmploymentDialog profile={profileDialog} member={members.find(member=>member.user_id===employmentUser)} onClose={()=>setEmploymentUser(null)} onSaved={load}/>}
      {tab === 'people' && <section className="company-card">
        <div className="company-section-heading"><div><h2>People</h2><p>Membership and company roles in {active.name}.</p></div><span className="company-count">{shownMembers.length} members</span></div>
        <div className="company-people-tools"><label className="company-field">Search people<input maxLength={100} value={peopleSearch} onChange={event=>setPeopleSearch(event.target.value)} placeholder="Name, email, employee ID or job title"/></label>
          <label className="company-field">Membership status<select aria-label="Membership status" value={peopleStatus} onChange={event=>setPeopleStatus(event.target.value)}>
            <option value="ACTIVE">Active</option>{companyAdmin&&<><option value="REMOVED">Removed</option><option value="PENDING">Pending</option><option value="ALL">All memberships</option></>}
          </select></label></div>
        {shownMembers.length ? <div className="company-list">{shownMembers.map(member => <div className="company-person-row" key={member.user_id} role="group" aria-label={`Member ${member.email}`}>
          <span className="company-person-avatar" aria-hidden="true">{(member.first_name || member.email || '?').charAt(0).toUpperCase()}</span>
          <div className="company-person-info"><strong>{[member.first_name, member.last_name].filter(Boolean).join(' ') || member.email}</strong><span>{member.email}</span>
            <span>{[member.employee_id,member.job_title].filter(Boolean).join(' · ')}</span></div>
          <div className="company-pills"><span className={`company-status company-status-${member.status.toLowerCase()}`}>{roleName(member.status)}</span>
            {(member.roles || []).length ? member.roles.map(role => <span className="company-pill" key={role}>{companyRoleLabel(role)}</span>) : <span className="company-pill">Member</span>}
            {companyAdmin&&member.account_available===false&&<span className="company-pill">Account unavailable</span>}</div>
          {companyAdmin&&<div className="company-profile-actions"><button className="company-text-button" onClick={()=>{setProfileDialog(true);setEmploymentUser(member.user_id);}}>View Profile</button><button className="company-text-button" onClick={()=>{setProfileDialog(false);setEmploymentUser(member.user_id);}}>Employment details</button></div>}
          {companyAdmin&&<details className="company-member-actions"><summary>Manage access</summary><div className="company-member-controls">
            {member.status==='ACTIVE'&&<>
              <label className="company-field">Company role<select aria-label={`Company role for ${member.email}`} value={roleDrafts[member.user_id]||''} disabled={busy||!member.account_available||member.platform_account} onChange={event=>setRoleDrafts({...roleDrafts,[member.user_id]:event.target.value})}>
                <option value="">Choose a role</option>{companyRoleOptions.filter(([role])=>!(member.roles||[]).includes(role)).map(([role,label])=><option key={role} value={role}>{label}</option>)}
              </select></label>
              <button className="company-text-button" disabled={busy||!roleDrafts[member.user_id]||!Number.isInteger(member.membership_version)} onClick={()=>run(()=>companyAPI.assignCompanyRole(selected,member.user_id,roleDrafts[member.user_id],member.membership_version),'Company role assigned.',()=>setRoleDrafts({...roleDrafts,[member.user_id]:''}))}>Assign role</button>
              {(member.roles||[]).filter(role=>companyRoleOptions.some(([key])=>key===role)).map(role=><button key={role} className="company-text-button company-text-danger" disabled={busy||!Number.isInteger(member.membership_version)||(role==='COMPANY_ADMIN'&&isLastAdmin(member))}
                title={role==='COMPANY_ADMIN'&&isLastAdmin(member)?'Appoint another active Company Admin first.':undefined}
                onClick={()=>{setMessage('');setMemberAction({member,role});}}>Remove {companyRoleLabel(role)} role</button>)}
              <button className="company-text-button" disabled={busy} onClick={()=>run(()=>companyAPI.recoverMemberPassword(selected,member.user_id),'Password recovery requested for the registered account email.')}>Send password recovery</button>
              <button className="company-text-button company-text-danger" disabled={busy||isLastAdmin(member)||!Number.isInteger(member.membership_version)} title={isLastAdmin(member)?'Appoint another active Company Admin first.':undefined}
                onClick={()=>{setMessage('');setMemberAction({member,status:'REMOVED'});}}>Remove membership</button>
            </>}
            {member.status==='REMOVED'&&<button className="company-text-button" disabled={busy||!Number.isInteger(member.membership_version)||!member.account_available||member.platform_account}
              onClick={()=>{setMessage('');setMemberAction({member,status:'ACTIVE'});}}>Reactivate membership</button>}
          </div></details>}
        </div>)}</div> : <EmptyState icon="users" title={members.length?'No matching members':'No members yet'} description={members.length?'Try another search or membership status.':'Invite someone to get your team started.'} />}
        {memberAction&&<MemberConfirmation action={memberAction} company={active} busy={busy} error={message} onCancel={()=>{setMemberAction(null);setMessage('');}}
          onConfirm={()=>run(()=>memberAction.role?companyAPI.removeCompanyRole(selected,memberAction.member.user_id,memberAction.role,memberAction.member.membership_version)
            :companyAPI.setMemberStatus(selected,memberAction.member.user_id,memberAction.status,memberAction.member.membership_version),
            memberAction.role?'Company role removed.':memberAction.status==='REMOVED'?'Company membership removed.':'Membership reactivated. Assign company and project roles as needed.',()=>setMemberAction(null))}/>}
      </section>}

      {tab === 'invitations' && <div className="company-content-grid">
        <form className="company-card company-side-form" onSubmit={event => { event.preventDefault(); run(() => companyAPI.invite(selected, {
          email: invite.email, role: invite.role, projectId: !companyAdmin && invite.projectId ? Number(invite.projectId) : null,
          accessRequestId: invite.accessRequestId || null,
        }), 'Invitation sent.', () => setInvite(current => ({ ...current, email: '', accessRequestId: null }))); }}>
          <div className="company-section-heading"><div><h2>Invite a person</h2><p>Invite someone to the company now; assign projects and project roles afterward. New people create an account from the invitation link.</p></div></div>
          {invite.accessRequestId && <p className="company-request-selected">Responding to an access request. The email stays linked to this request.</p>}
          <label className="company-field">Email address<input type="email" required value={invite.email} readOnly={!!invite.accessRequestId} onChange={event => setInvite({ ...invite, email: event.target.value })} placeholder="name@company.com" /></label>
          <label className="company-field">Role<select value={invite.role} onChange={event => setInvite({ ...invite, role: event.target.value,
            projectId: ['COMPANY_ADMIN', 'MODERATOR'].includes(event.target.value) ? '' : invite.projectId })}>
            {companyAdmin && <><option value="COMPANY_ADMIN">Company Admin</option><option value="PROJECT_ADMIN">Project Admin</option></>}
            {!companyAdmin && <option value="PROJECT_MANAGER">Project Manager</option>}
            <option value="USER">User</option></select></label>
          {!companyAdmin && !['COMPANY_ADMIN', 'MODERATOR'].includes(invite.role) && <label className="company-field">Project<select value={invite.projectId}
            onChange={event => setInvite({ ...invite, projectId: event.target.value })}>
            <option value="">{invite.role === 'PROJECT_ADMIN' && companyAdmin ? 'Company scope' : companyAdmin ? 'No project — assign later' : 'Select project'}</option>
            {companyProjects.map(project => <option key={project.id} value={project.id}>{project.name}</option>)}
          </select></label>}
          <button className="button button-primary" disabled={busy || (!companyAdmin && !invite.projectId)}>Send invitation</button>
        </form>
        <section className="company-card">
          <div className="company-section-heading"><div><h2>Invitations</h2><p>Track invitations sent for this company.</p></div><span className="company-count">{invitations.length} total</span></div>
          {invitations.length ? <div className="company-list">{invitations.map(item => {
            const status = invitationStatus(item);
            const projectName = companyProjects.find(project => String(project.id) === String(item.project_id))?.name;
            return <div className="company-record-row" key={item.id}>
              <div className="company-record-main"><strong>{item.invitee_email}</strong><span>{roleName(item.role_key)}{projectName ? ` · ${projectName}` : ''}</span><span>Delivery: {item.delivery_status || 'Legacy'}</span></div>
              <span className={`company-status company-status-${status.toLowerCase()}`}>{status}</span>
              {status === 'Pending' && <button type="button" className="company-text-button" disabled={busy} onClick={() => run(() => companyAPI.revokeInvitation(selected, item.id), 'Invitation revoked.')}>Revoke</button>}
              {['Pending','Expired'].includes(status)&&<button type="button" className="company-text-button" disabled={busy} onClick={()=>run(()=>companyAPI.resendInvitation(selected,item.id),'Invitation queued again.')}>Resend</button>}
            </div>;
          })}</div> : <EmptyState icon="mail" title="No invitations yet" description="Invitations you send will appear here." />}
        </section>
        {companyAdmin && <section className="company-card company-request-queue">
          <div className="company-section-heading"><div><h2>Access requests</h2><p>Requests do not grant access. Review the person, then choose their project and role before sending an invitation.</p></div><span className="company-count">{accessRequests.length} pending</span></div>
          {accessRequests.length ? <div className="company-list">{accessRequests.map(request => <div className="company-record-row" key={request.id}>
            <div className="company-record-main"><strong>{request.first_name} {request.last_name}</strong><span>{request.email} · Requested {shortDate(request.requested_at)}</span></div>
            <div className="company-row-actions"><button type="button" className="company-text-button" onClick={() => inviteRequester(request)}>Prepare invite</button>
              <button type="button" className="company-text-button company-text-danger" disabled={busy} onClick={() => run(() => companyAPI.dismissAccessRequest(selected, request.id), 'Request dismissed.')}>Dismiss</button></div>
          </div>)}</div> : <EmptyState icon="mail" title="No access requests" description={`Share the workspace ID ${active.slug} with people who need to request access.`} />}
        </section>}
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
