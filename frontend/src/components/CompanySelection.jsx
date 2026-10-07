import { Link } from 'react-router-dom';
import { useCompany } from '../CompanyContext';
import { useAuth } from '../AuthContext';

export default function CompanySelection() {
  const { companies, loading, switching, error, selectCompany, refreshCompanies } = useCompany();
  const { logout } = useAuth();
  return <main className="page-container company-selection-page">
    <section className="company-selection-card">
      <span className="eyebrow">CHRONOS WORKSPACES</span>
      <h1>{companies.length ? 'Choose your company' : 'Your company access'}</h1>
      <p>{companies.length ? 'Select the workspace you want to use.' : 'You do not have an active company membership yet. Accept an invitation or request company access to continue.'}</p>
      {error && <p role="alert" className="error-message">{error}</p>}
      {loading || switching ? <p role="status">Loading workspace...</p> : <div className="company-selection-list">
        {companies.map(company => <button className="button button-secondary" key={company.id}
          onClick={() => selectCompany(company.id).catch(() => {})}>{company.name}<small>{company.slug}</small></button>)}
      </div>}
      <div className="company-selection-actions">
        <button className="button button-secondary" disabled={loading || switching} onClick={refreshCompanies}>Refresh access</button>
        <Link className="button button-secondary" to="/company-invite">My invitations</Link>
        <Link className="account-link" to="/request-access">Request company access</Link>
        <button className="button button-secondary" onClick={logout}>Sign out</button>
      </div>
    </section>
  </main>;
}
