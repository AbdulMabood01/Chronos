import { Link } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { useCompany } from '../CompanyContext';
import { workspaceNavigation, workspaceRoleLabel } from '../workspaceAccess';
import Icon from '../components/Icon';
import './Companies.css';

export default function WorkspaceOverview() {
  const { user } = useAuth();
  const context = useCompany();
  const { currentCompany, companies, platformAdmin, projectPermissions = [] } = context;
  const navigation = workspaceNavigation(context);
  const links = [...navigation.management, ...navigation.primary.filter(([path])=>path !== '/dashboard')];
  return <div className="page-container companies-page">
    <header className="company-header"><div>
      <span className="company-eyebrow">{platformAdmin ? 'Platform administration' : currentCompany?.name}</span>
      <h1>{platformAdmin ? 'Platform overview' : 'Company overview'}</h1>
      <p>Welcome, {user?.firstName}. {platformAdmin ? 'Manage company workspaces and onboarding.' : `Your access in ${currentCompany?.name}: ${workspaceRoleLabel(context).toLowerCase()}.`}</p>
    </div></header>
    <div className="company-stats">
      {platformAdmin ? <><div><span>Companies</span><strong>{companies.length}</strong><small>Platform workspaces</small></div>
        <div><span>Administration</span><strong>Platform</strong><small>Company provisioning and plans</small></div></>
        : <><div><span>Workspace ID</span><strong>{currentCompany?.slug}</strong><small>{currentCompany?.name}</small></div>
          <div><span>Accessible projects</span><strong>{projectPermissions.length}</strong><small>In this company</small></div></>}
    </div>
    <section className="company-card"><h2>{platformAdmin ? 'Platform tools' : 'Your workspace tools'}</h2>
      <div className="company-selection-actions">{links.map(([path,label,icon])=><Link className="button button-secondary" to={path} key={path}><Icon name={icon} size={17}/>{label}</Link>)}</div>
    </section>
  </div>;
}
