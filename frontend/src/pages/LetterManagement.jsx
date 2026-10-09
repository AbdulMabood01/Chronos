import LetterSettings from './LetterSettings';
import ScreenTitle from '../components/ScreenTitle';
import { LoadingIndicator } from '../components/Hourglass';
import './CompanyManagement.css';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { useCompany } from '../CompanyContext';
import { companyLetterRequestAPI } from '../api';
import { formatDate } from '../utils/dates';
export default function LetterManagement() {
  const { user } = useAuth();
  const { currentCompany, companyRoles = [] } = useCompany();
  const [rows, setRows] = useState([]), [loading, setLoading] = useState(true), [error, setError] = useState('');
  const [revision, setRevision] = useState(0), [tab, setTab] = useState('requests');
  useEffect(() => {
    let active = true; setRows([]); setLoading(true); setError('');
    companyLetterRequestAPI(currentCompany.id).getPendingRequests()
      .then(response => { if (active) setRows((response.data || []).filter(row => String(row.companyId) === String(currentCompany.id))); })
      .catch(err => { if (active) setError(err.response?.data?.message || 'Unable to load letter requests.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [currentCompany.id, revision]);
  return <div className="page-container company-management-page">
    <div className="header-bar management-page-header">
      <ScreenTitle title="Letter management" icon="file" eyebrow="MANAGEMENT" description={`Review employment letters for ${currentCompany.name}.`} />
      <div className="management-actions">
        {!companyRoles.includes('COMPANY_ADMIN') && <Link className="button button-secondary" to="/requests">My letter requests</Link>}
        <button className="button button-secondary" disabled={loading} onClick={() => setRevision(value => value + 1)}>Reload requests</button>
      </div>
    </div>
    <nav className="management-section-tabs" aria-label="Letter management sections">
      <button type="button" className={`button ${tab === 'requests' ? 'button-primary' : 'button-secondary'}`} aria-pressed={tab === 'requests'} onClick={() => setTab('requests')}>Pending requests</button>
      <button type="button" className={`button ${tab === 'settings' ? 'button-primary' : 'button-secondary'}`} aria-pressed={tab === 'settings'} onClick={() => setTab('settings')}>Company &amp; HR details</button>
    </nav>
    {tab === 'settings' ? <LetterSettings key={currentCompany.id} /> : <section className="card management-section">
      <div className="management-section-heading"><div><h2>Pending letter requests</h2><p>Confirm employee details and preview each letter before approval.</p></div><span>{rows.length} pending</span></div>
      {error && <p className="error-message" role="alert">{error}</p>}
      {loading ? <LoadingIndicator label="Loading requests..." /> : error ? null : !rows.length ? <div className="management-empty"><h3>No pending letter requests</h3><p>New company requests will appear here for review.</p></div> : <div className="table-container"><table className="data-table">
        <thead><tr><th>Employee</th><th>Letter type</th><th>Submitted</th><th>Status</th><th>Action</th></tr></thead>
        <tbody>{rows.map(row => <tr key={row.id}><td><strong>{row.requestedFullName || row.userName}</strong></td><td>{row.requestType.replaceAll('_', ' ')}</td><td>{formatDate(row.submittedAt)}</td><td><span className="status-badge status-submitted">Awaiting review</span></td><td>{String(row.userId) === String(user.id) ? <span>Another Company Admin must review</span> : <Link className="button button-small button-primary" to={`/admin/letter-request/${row.id}`}>Review letter</Link>}</td></tr>)}</tbody>
      </table></div>}
    </section>}
  </div>;
}
