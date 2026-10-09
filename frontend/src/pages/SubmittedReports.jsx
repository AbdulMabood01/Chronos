import { useEffect, useRef, useState } from 'react';
import { companyEmployeeReportsAPI,companyGovernanceAPI } from '../api';
import { useCompany } from '../CompanyContext';
import { categories, ReportProgress } from './EmployeeReports';
import Icon from '../components/Icon';

const label = value => value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
const date = value => value ? new Date(value).toLocaleString() : '—';

export default function SubmittedReports({ SubmissionForm }) {
  const {currentCompany,companyCapabilities}=useCompany(); const companyId=currentCompany?.id;const employeeReportsAPI=companyEmployeeReportsAPI(companyId);
  const [refresh, setRefresh] = useState(0);
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [selected, setSelected] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [view, setView] = useState('submitted');
  const [latestReportId, setLatestReportId] = useState('');
  const [formVersion, setFormVersion] = useState(0);
  const heading = useRef(null);
  const returnId = useRef(null);
  useEffect(() => {
    let active = true, pending = false;
    setLoading(true); setError('');
    async function load() {
      if (pending) return;
      pending = true;
      try {
        const response = selectedId ? await employeeReportsAPI.myDetail(selectedId) : await employeeReportsAPI.mine({ page });
        if (active) { selectedId ? setSelected(response.data) : setRows(response.data); setError(''); }
      } catch (e) {
        if (active) {
          setError(e.response?.data?.message || 'Unable to load reports. Please retry.');
          if ([401, 403, 404].includes(e.response?.status)) { setSelected(null); setRows([]); }
        }
      } finally { pending = false; if (active) setLoading(false); }
    }
    load();
    return () => { active = false; };
  }, [companyId,selectedId, page, refresh]);
  useEffect(() => {
    if (selectedId) heading.current?.focus();
    else if (returnId.current) document.getElementById('view-report-' + returnId.current)?.focus();
  }, [selectedId, loading]);
  return <>
    <div hidden={!!selectedId}>
      <nav className="concern-view-switch" aria-label="Report views">
        <button type="button" className={view === 'submitted' ? 'is-active' : ''} aria-current={view === 'submitted' ? 'page' : undefined} onClick={() => setView('submitted')}>Submitted reports</button>
        <button type="button" className={view === 'create' ? 'is-active' : ''} aria-current={view === 'create' ? 'page' : undefined} onClick={() => setView('create')}>Create report</button>
      </nav>
      <div hidden={view !== 'create'} className="report-compose-layout"><SubmissionForm key={formVersion} onSubmitted={(receipt, anonymous) => { setPage(0); setRefresh(v => v + 1); if (anonymous) { setLatestReportId(''); } else { setLatestReportId(receipt.reportId); setFormVersion(v => v + 1); setView('submitted'); } }} />
        <aside className="report-compose-aside"><span className="report-kicker">BEFORE YOU BEGIN</span><h2>A clear account helps HR respond</h2><p>Describe what happened in your own words. Include dates, people, and supporting files when you have them.</p><div><Icon name="check" size={18}/><span>Only authorized designated confidential handlers can review submissions.</span></div><div><Icon name="file" size={18}/><span>You can follow identified reports from this page.</span></div><div><Icon name="users" size={18}/><span>Anonymous reports cannot be linked to your account.</span></div></aside>
      </div>
      <section className="concern-card report-list-panel" hidden={view !== 'submitted'} aria-label="Submitted Reports">
        <div className="concern-heading"><div><span className="report-kicker">YOUR CASES</span><h2>Submitted Reports</h2><p className="concern-help">Identified reports are kept here so you can follow their progress.</p></div><button disabled={loading} onClick={() => setRefresh(v => v + 1)}><Icon name="clock" size={16}/> Refresh reports</button></div>
        {latestReportId && <div className="concern-submission-notice" role="status">Report submitted. Save your Report ID for reference: <strong className="report-reference">{latestReportId}</strong></div>}
        {!selectedId && error && <p role="alert" className="concern-error">{error}</p>}
        {loading && !selectedId ? <p role="status">Loading reports…</p> : !rows.length ? <div className="report-empty"><Icon name="file" size={28}/><h3>{error ? 'Reports could not be loaded.' : 'No submitted reports yet.'}</h3><p>{error ? 'Use Refresh reports to try again.' : 'Reports submitted with your name will appear here. You can start one whenever you are ready.'}</p>{!error && <button className="button button-primary" onClick={() => setView('create')}>Create a report <Icon name="arrow" size={16}/></button>}</div> : <div className="report-case-list">{rows.map(row => <article className="report-case" key={row.id}>
          <div className="report-case-main"><div className="report-case-top"><span className="report-category">{categories[row.category]}</span><span className={`concern-status report-status-${String(row.status).toLowerCase()}`}>{label(row.status)}</span></div><h3>{row.subject}</h3><div className="report-case-meta"><span>Submitted {date(row.submitted_at)}</span><span>Updated {date(row.updated_at || row.submitted_at)}</span></div><code className="concern-id">{row.id}</code></div>
          <button className="report-case-action" id={'view-report-' + row.id} aria-label={'View ' + row.subject} onClick={() => { returnId.current = row.id; setSelected(null); setSelectedId(row.id); }}>View report <Icon name="arrow" size={16}/></button>
        </article>)}</div>}
        <div className="concern-pagination"><button disabled={!page || loading} onClick={() => setPage(p => p - 1)}>Previous</button><span>Page {page + 1}</span><button disabled={rows.length < 50 || loading} onClick={() => setPage(p => p + 1)}>Next</button></div>
      </section>
    </div>
    {selectedId && <section className="concern-card concern-details" aria-label="Report details">
      <div className="concern-heading"><h2 tabIndex={-1} ref={heading}>{selected?.subject || 'Report details'}</h2><button onClick={() => { setSelectedId(null); setSelected(null); }}>Back to Submitted Reports</button></div>
      <button disabled={loading} onClick={() => setRefresh(v => v + 1)}>Refresh report</button><p className="concern-help">Only updates shared with you by HR are shown.</p>
      {error && <div role="alert" className="concern-error">{error}<button onClick={() => setRefresh(v => v + 1)}>Retry</button></div>}
      {loading && <p role="status">Loading report…</p>}
      {selected && <>
        <p className="report-reference">Report ID: {selected.id}</p><p>{categories[selected.category]} · Submitted {date(selected.submitted_at)}</p>
        <ReportProgress status={selected.status} />
        <h3>Report details</h3><p className="concern-text">{selected.description}</p>
        {Object.entries({ incident_at: 'Incident date / time (local to incident)', location: 'Location', people_involved: 'People involved', witnesses: 'Witnesses' }).map(([key, text]) => selected[key] && <div key={key}><h4>{text}</h4><p className="concern-text">{selected[key]}</p></div>)}
        <h3>Status timeline & resolution history</h3>
        <ol className="concern-history concern-timeline">{selected.history.map(item => <li key={item.id}><div className="concern-heading"><strong>{label(item.status)}</strong><time className="concern-help" dateTime={item.created_at}>{date(item.created_at)}</time></div><p className="concern-text"><strong>Actions Taken: </strong>{item.actions_taken || 'No actions shared for this update.'}</p><p className="concern-text"><strong>Resolution Details: </strong>{item.resolution || 'No resolution shared for this update.'}</p></li>)}</ol>
      </>}
    </section>}
  </>;
}
