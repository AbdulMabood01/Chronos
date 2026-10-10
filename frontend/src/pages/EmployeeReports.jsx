import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../AuthContext';
import { companyEmployeeReportsAPI,companyGovernanceAPI } from '../api';
import { useCompany } from '../CompanyContext';
import './EmployeeReports.css';
import SubmittedReports from './SubmittedReports';
import Icon from '../components/Icon';


export const categories = {
  SEXUAL_HARASSMENT: 'Sexual Harassment', WORKPLACE_HARASSMENT: 'Workplace Harassment or Bullying',
  DISCRIMINATION: 'Discrimination', SAFETY_CONCERN: 'Safety Concern',
  WORKPLACE_MISCONDUCT: 'Workplace Misconduct', OTHER_INCIDENT: 'Other Incident',
};
const statuses = ['SUBMITTED', 'UNDER_REVIEW', 'INVESTIGATION', 'RESOLVED', 'CLOSED'];
const label = value => value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
const errorMessage = error => error.response?.data?.message || 'Unable to complete the request. Please try again.';
const emptyForm = { category: '', subject: '', description: '', incidentAt: '', location: '', peopleInvolved: '', witnesses: '', anonymous: false, privacyAcknowledged: false };
const dateTime = value => value ? new Date(value).toLocaleString() : '—';

export function ReportProgress({ status }) {
  const current = statuses.indexOf(status);
  return <footer className="concern-progress" aria-label="Report progress">
    <div className="concern-progress-heading"><strong>Report progress</strong><span>{label(status)}</span></div>
    <progress aria-label="Report review progress" max={statuses.length} value={current + 1} />
    <ol className="concern-progress-steps">{statuses.map((step, index) => <li key={step} className={index <= current ? 'is-reached' : ''} aria-current={index === current ? 'step' : undefined}><span aria-hidden="true">{index < current ? '✓' : index + 1}</span>{label(step)}</li>)}</ol>
  </footer>;
}

function SubmissionForm({ onSubmitted }) {
  const {currentCompany,companyCapabilities}=useCompany(); const companyId=currentCompany?.id;const employeeReportsAPI=companyEmployeeReportsAPI(companyId);
  const [configuration,setConfiguration]=useState(null);
  useEffect(()=>{let active=true;companyGovernanceAPI(companyId).configuration().then(r=>{if(active)setConfiguration(r.data);}).catch(()=>{});return()=>{active=false;};},[companyId]);
  const [form, setForm] = useState({...emptyForm,excludedUserIds:[]});

  const [files, setFiles] = useState([]);
  const [receipt, setReceipt] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const change = event => setForm(current => ({ ...current, [event.target.name]: event.target.type === 'checkbox' ? event.target.checked : event.target.value }));
  async function submit(event) {
    event.preventDefault(); setError('');
    if (files.length > 5 || files.some(f => !f.size || f.size > 10 * 1024 * 1024) || files.reduce((sum, f) => sum + f.size, 0) > 20 * 1024 * 1024) {
      setError('Attach up to 5 nonempty files, at most 10 MB each and 20 MB total.'); return;
    }
    setBusy(true);
    try {
      const body = new FormData();
      body.append('report', new Blob([JSON.stringify({ ...form, incidentAt: form.incidentAt || null })], { type: 'application/json' }));
      files.forEach(file => body.append('attachments', file));
      const response = await employeeReportsAPI.submit(body);
      setReceipt({ ...response.data, anonymous: form.anonymous }); setForm({...emptyForm,excludedUserIds:[]}); setFiles([]); onSubmitted(response.data, form.anonymous);
    } catch (e) { setError(errorMessage(e)); if([403,404].includes(e.response?.status)){setSelected(null);setRows([]);} } finally { setBusy(false); }
  }
  if (receipt) return <section className="concern-card" role="status"><h2>Report submitted confidentially</h2><p>Your report is retained for eligible confidential handlers to review.</p><p>Save your Report ID for reference when contacting HR. This ID does not grant access to the report.</p><strong className="report-reference">{receipt.reportId}</strong><p>Status: Submitted</p><button type="button" className="button button-primary" onClick={() => setReceipt(null)}>Submit another report</button><ReportProgress status={receipt.status} /><small>{receipt.anonymous ? "Anonymous reports are not linked to your account and do not appear in Submitted Reports. Contact HR with your Report ID for updates." : "Follow your report in Submitted Reports."}</small></section>;
  return <form className="concern-card concern-form" onSubmit={submit}>
    {configuration && !configuration.handlerConfigured && <p role="status">No eligible Company Admin or confidential handler is available. Your report will be retained privately until one is available.</p>}<div className="report-section-intro"><h2>Create report</h2><p>Describe your concern. Company Admins and designated confidential handlers can review it.</p></div>
    {error && <div role="alert" className="concern-error">{error}</div>}
    <fieldset disabled={busy}>
      <div className="report-form-section report-essentials">
      <label>Report category<select name="category" required value={form.category} onChange={change}><option value="">Select a category</option>{Object.entries(categories).map(([key, text]) => <option key={key} value={key}>{text}</option>)}</select></label>
      <label>Report title / subject<input name="subject" required maxLength={200} value={form.subject} onChange={change} /></label>
      <label>Incident description<textarea name="description" required maxLength={20000} rows={7} value={form.description} onChange={change} /></label>
      </div>
      <details className="report-optional-details"><summary>Additional details &amp; attachments (optional)</summary><div className="report-form-section">
      <div className="concern-columns"><label>Date and time of incident (optional)<input type="datetime-local" name="incidentAt" value={form.incidentAt} onChange={change} /><small>Use the local time at the incident location.</small></label>
      <label>Location (optional)<input name="location" maxLength={500} value={form.location} onChange={change} /></label></div>
      <label>People involved (optional)<textarea name="peopleInvolved" maxLength={5000} value={form.peopleInvolved} onChange={change} /></label>
      <label>Witnesses (optional)<textarea name="witnesses" maxLength={5000} value={form.witnesses} onChange={change} /></label>
      <label>Supporting attachments (optional)<input type="file" multiple accept={form.anonymous ? '.png,.jpg,.jpeg,.gif,.pdf,.txt' : '.png,.jpg,.jpeg,.gif,.webp,.pdf,.doc,.docx,.txt,.odt'} onChange={e => setFiles(Array.from(e.target.files))} /><small>{form.anonymous ? 'Still PNG/JPG/GIF, PDF (up to 30 pages), or plain UTF-8 text. Export Word/ODT documents as PDF first.' : 'Images, PDF, Word, text, or ODT.'} Up to 5 files; 10 MB each, 20 MB total.</small></label>
      {files.length > 0 && <ul>{files.map((file, index) => <li key={index}>{file.name} ({Math.ceil(file.size / 1024)} KB)</li>)}</ul>}
      </div>
      </details><div className="report-form-section report-form-section-last">
      <p className="concern-help">Identified reports appear in Submitted Reports. Anonymous reports cannot be linked to your account for tracking.</p><label className="concern-check"><input type="checkbox" name="anonymous" checked={form.anonymous} onChange={e => { change(e); setForm(current => ({ ...current, privacyAcknowledged: false })); }} />Report Anonymously</label>
      <div className="concern-privacy"><p>{form.anonymous ? "Your identity is hidden from case handlers. Check your text and files for identifying information. Anonymous reports do not appear in your submitted reports." : "Your name, email, report details, and attachments are retained and available to Company Admins and designated confidential handlers."}</p><details className="report-privacy-details"><summary>Privacy and information retention details</summary>
        <p>{form.anonymous ? 'Your report will not store your account ID, name, or email. A private case-specific exclusion token prevents you from handling your own report. Your identity is hidden from case handlers. You still sign in to submit. The report ID, submission time, incident details, and processed attachments are retained. Original filenames are replaced. Images are re-encoded and PDFs are flattened to remove hidden metadata, authors, comments, links, and embedded files. Original uploaded bytes are not stored for new anonymous reports.' : 'Your name and email will be available to HR with this report. The report ID, submission time, incident details, and original attachments are retained.'}</p>
        <p>Review your text and visible attachment contents for names or identifying details before submitting. Automatic metadata removal cannot hide information visible in a screenshot or document. Technical operators with database or infrastructure access may access stored data; authentication or server logs may allow timing or network correlation. This is not a guarantee of untraceability.</p>
        <p>Report contents are excluded from the general audit feed and notifications. HR review actions are retained in a confidential report history.</p>
        </details><label className="concern-check"><input required type="checkbox" name="privacyAcknowledged" checked={form.privacyAcknowledged} onChange={change} />I understand what information is retained.</label>
      </div>
      </div>
      <footer className="concern-submit-bar">
        <button className="button button-primary" type="submit">{busy ? 'Submitting…' : 'Submit confidential report'}</button>
      </footer>
    </fieldset>
  </form>;
}

function Management() {
  const {currentCompany,companyCapabilities}=useCompany(); const companyId=currentCompany?.id;const employeeReportsAPI=companyEmployeeReportsAPI(companyId);
  const [filters, setFilters] = useState({ reportId: '', category: '', status: '', from: '', to: '', anonymous: '' });
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState([]);
  const [selected, setSelected] = useState(null);
  const [review, setReview] = useState({ status: '', note: '', actionsTaken: '', resolution: '', shareWithEmployee: false });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [refresh, setRefresh] = useState(0);
  const [listError, setListError] = useState('');
  const [filtersOpen, setFiltersOpen] = useState(false);
  const detailRef = useRef(null);
  useEffect(() => {
    let active = true, pending = false;
    setLoading(true); setListError('');
    const load = async () => {
      if (pending) return;
      pending = true;
      try {
        const response = await employeeReportsAPI.list({ ...Object.fromEntries(Object.entries(filters).filter(([,v]) => v !== '')), page });
        if (active) { setRows(response.data); setListError(''); }
      } catch (e) { if (active) { setRows([]); setListError(errorMessage(e)); } }
      finally { pending = false; if (active) setLoading(false); }
    };
    load();
    return () => { active = false; };
  }, [companyId,filters, page, refresh]);
  useEffect(() => {
    if (selected) { detailRef.current?.scrollIntoView?.({ block: 'start', behavior: 'auto' }); detailRef.current?.focus(); }
  }, [selected?.id]);
  async function open(id) {
    setBusy(true); setError('');
    try {
      const response = await employeeReportsAPI.detail(id); setSelected(response.data);
      setReview({ status: response.data.status, note: '', actionsTaken: '', resolution: '', shareWithEmployee: false });
    } catch(e) { setError(errorMessage(e)); if([403,404].includes(e.response?.status)){setSelected(null);setRows([]);} } finally { setBusy(false); }
  }
  async function save(event) {
    event.preventDefault(); setBusy(true); setError('');
    try { await employeeReportsAPI.review(selected.id, {...review,version:selected.version}); await open(selected.id); setRefresh(v => v + 1); }
    catch(e) { setError(errorMessage(e)); if([403,404].includes(e.response?.status)){setSelected(null);setRows([]);} } finally { setBusy(false); }
  }
  async function download(file) {
    setBusy(true); setError('');
    try {
      const response = await employeeReportsAPI.download(selected.id, file.id);
      const url = URL.createObjectURL(response.data); const link = document.createElement('a');
      link.href = url; link.download = file.filename; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
      await open(selected.id);
    } catch(e) { setError(errorMessage(e)); if([403,404].includes(e.response?.status)){setSelected(null);setRows([]);} } finally { setBusy(false); }
  }
  return <>
    <div hidden={!!selected}><p className="report-confidential-note"><Icon name="check" size={16}/> Confidential HR access. Review and download actions are recorded.</p>
    {error && <div className="concern-error" role="alert">{error}</div>}
    {listError && <div className="concern-error" role="alert">{listError}</div>}
    <section className="concern-card concern-filters"><div className="concern-filter-heading"><div><span className="report-kicker">CASE FINDER</span><h2>Find a report</h2><p>Filter by report details or submission date.</p></div><button type="button" aria-expanded={filtersOpen} onClick={() => setFiltersOpen(open => !open)}><Icon name="search" size={16}/>{filtersOpen ? 'Hide filters' : 'Show filters'}</button></div>
      <div className="report-filter-fields" hidden={!filtersOpen}>
      <label>Search Report ID<input type="search" maxLength={36} placeholder="Full or partial Report ID" value={filters.reportId} onChange={e => { setFilters({ ...filters, reportId: e.target.value }); setPage(0); }} /></label>
      <label>Submitted by<select value={filters.anonymous} onChange={e => { setFilters({ ...filters, anonymous: e.target.value }); setPage(0); }}><option value="">Anonymous and identified</option><option value="true">Anonymous</option><option value="false">Identified</option></select></label>
      <label>Category<select value={filters.category} onChange={e => { setFilters({ ...filters, category: e.target.value }); setPage(0); }}><option value="">All categories</option>{Object.entries(categories).map(([key, text]) => <option key={key} value={key}>{text}</option>)}</select></label>
      <label>Status<select aria-label="Status" value={filters.status} onChange={e => { setFilters({ ...filters, status: e.target.value }); setPage(0); }}><option value="">All statuses</option>{statuses.map(s => <option key={s} value={s}>{label(s)}</option>)}</select></label>
      {['from','to'].map(key => <label key={key}>{key === 'from' ? 'Submitted from (UTC)' : 'Submitted through (UTC)'}<input type="date" value={filters[key]} onChange={e => { setFilters({ ...filters, [key]: e.target.value }); setPage(0); }} /></label>)}
      </div>
    </section>
    <section className="concern-card">
      <div className="concern-heading"><div><h2>Employee reports</h2><p className="concern-help">Newest first · Refresh to see updates</p></div><button disabled={loading} onClick={() => setRefresh(v => v + 1)}>Refresh reports</button></div>
      {loading ? <p role="status">Loading reports…</p> : listError ? <p>Reports could not be loaded. Retry using Refresh reports.</p> : rows.length === 0 ? <div className="report-empty"><Icon name="search" size={26}/><h3>No reports match these filters.</h3><p>Try changing a filter or searching another Report ID.</p></div> : <div className="report-case-list">{rows.map(row => <article className="report-case" key={row.id}>
        <div className="report-case-main"><div className="report-case-top"><span className="report-category">{categories[row.category]}</span><span className={`concern-status report-status-${String(row.status).toLowerCase()}`}>{label(row.status)}</span></div><h3>{row.subject}</h3><div className="report-case-meta"><span>{row.anonymous ? 'Anonymous' : row.submitted_by || 'Unavailable'}</span><span>Submitted {dateTime(row.submitted_at)}</span><span>Updated {dateTime(row.updated_at || row.submitted_at)}</span></div><code className="concern-id">{row.id}</code></div>
        <button className="report-case-action" disabled={busy} onClick={() => open(row.id)}>Review <Icon name="arrow" size={16}/></button>
      </article>)}</div>}
      <div className="concern-pagination"><button disabled={page === 0 || loading} onClick={() => setPage(p => p - 1)}>Previous</button><span>Page {page + 1}</span><button disabled={rows.length < 50 || loading} onClick={() => setPage(p => p + 1)}>Next</button></div>
    </section>
    </div>{selected && <section ref={detailRef} tabIndex={-1} className="concern-card concern-details" aria-label="Report details"><div className="concern-heading"><h2>{selected.subject}</h2><button disabled={busy} onClick={() => setSelected(null)}>Close details</button></div>
      {error && <div className="concern-error" role="alert">{error}</div>}<p className="report-reference">Report ID: {selected.id}</p><p><strong>{categories[selected.category]}</strong> · {label(selected.status)}</p>
      <p>Submitted By: {selected.anonymous ? 'Anonymous' : selected.reporter ? `${selected.reporter.first_name} ${selected.reporter.last_name} (${selected.reporter.email})` : 'Unavailable'}</p>
      <p>Submitted: {new Date(selected.submitted_at).toLocaleString()}</p>
      <button disabled={busy} onClick={async()=>{setBusy(true);try{await companyGovernanceAPI(companyId).recuse(selected.id);setSelected(null);setRefresh(v=>v+1);}catch(e){setError(errorMessage(e));}finally{setBusy(false);}}}>Recuse myself from this case</button><h3>Incident description</h3><p className="concern-text">{selected.description}</p>
      {Object.entries({ incident_at: 'Incident date / time (local to incident)', location: 'Location', people_involved: 'People involved', witnesses: 'Witnesses' }).map(([key, text]) => selected[key] && <div key={key}><h3>{text}</h3><p className="concern-text">{selected[key]}</p></div>)}
      <h3>Supporting attachments</h3><p>{selected.anonymous ? 'Anonymous downloads use generic filenames and processed copies with hidden metadata removed. Older files that cannot be processed safely are withheld.' : 'Downloads contain original files.'} Files are not automatically scanned for malware.</p>{selected.attachments.length ? <ul>{selected.attachments.map(file => <li key={file.id}>{file.downloadAvailable === false ? <span>Attachment withheld: its format cannot be anonymized safely.</span> : <button disabled={busy} onClick={() => download(file)}>Download {file.filename}</button>}</li>)}</ul> : <p>No attachments.</p>}
      <ReportProgress status={selected.status} /><h3>Report Tracker</h3><p className="concern-help">Internal notes remain private. Employees see status changes and actions or resolutions explicitly shared with them.</p>
      {selected.status !== 'CLOSED' && <form className="concern-form" onSubmit={save}><h4>Update case</h4><fieldset disabled={busy}>
        <label>Status<select aria-label="Status" value={review.status} onChange={e => setReview({ ...review, status: e.target.value })}>{statuses.filter((s, index) => index === statuses.indexOf(selected.status) || index === statuses.indexOf(selected.status) + 1).map(s => <option key={s} value={s}>{label(s)}</option>)}</select></label>
        <label>Internal note<textarea rows={3} maxLength={10000} value={review.note} onChange={e => setReview({ ...review, note: e.target.value })} /></label>
        <label>Actions taken<textarea rows={3} maxLength={10000} required={review.status === 'RESOLVED' && selected.status !== 'RESOLVED'} value={review.actionsTaken} onChange={e => setReview({ ...review, actionsTaken: e.target.value })} /></label>
        <label>Resolution details<textarea rows={3} maxLength={10000} required={review.status === 'RESOLVED' && selected.status !== 'RESOLVED'} value={review.resolution} onChange={e => setReview({ ...review, resolution: e.target.value })} /></label>
        <label className="concern-check"><input type="checkbox" checked={review.shareWithEmployee} onChange={e => setReview({ ...review, shareWithEmployee: e.target.checked })} />Share actions and resolution with the employee</label><p className="concern-help">Only select this for information intended for the employee. Internal notes always remain private. Previous updates stay unchanged.</p><button className="button button-primary" type="submit">{busy ? 'Saving…' : 'Save review'}</button>
      </fieldset></form>}
      <h4>Case activity</h4><ol className="concern-history concern-timeline">{selected.history.map(item => <li key={item.id}><div className="concern-heading"><strong>{item.action.replaceAll('_', ' ')}</strong>{item.status && <span className="concern-status">{label(item.status)}</span>}</div><p className="concern-help">{item.employee_visible ? "Actions and resolution shared with employee ? " : ""}Updated by: {item.first_name ? `${item.first_name} ${item.last_name || ''}` : 'System'} · <time dateTime={item.created_at}>{dateTime(item.created_at)}</time></p>{[['note','Internal note'],['actions_taken','Actions taken'],['resolution','Resolution']].map(([key, text]) => item[key] && <p key={key} className="concern-text"><strong>{text}: </strong>{item[key]}</p>)}</li>)}</ol>
    </section>}
  </>;
}

export default function EmployeeReports({ management = false }) {
  const {currentCompany,companyCapabilities}=useCompany(); const companyId=currentCompany?.id;const employeeReportsAPI=companyEmployeeReportsAPI(companyId);
  const { user } = useAuth();
  if (management && !companyCapabilities.canHandleConfidentialReports) return <p>Only Company Admins and designated confidential handlers can access employee reports.</p>;
  return <div className={`page-container employee-concerns ${management ? 'concern-management' : 'report-simple'}`}>
    <header className={management ? "report-page-hero" : "report-simple-header"}><div className="report-hero-copy"><span className="report-kicker">{management ? 'HR CASE MANAGEMENT' : 'PRIVATE WORKPLACE CHANNEL'}</span><h1>{management ? 'Report review' : 'Workplace reports'}</h1><p>{management ? 'Review confidential submissions, follow case progress, and record the next step.' : 'Share a concern with HR and follow the progress of reports you submitted with your name.'}</p></div><div className="report-hero-mark" aria-hidden="true"><Icon name="file" size={30}/></div></header>
    {management ? <Management /> : <SubmittedReports SubmissionForm={SubmissionForm} />}
  </div>;
}
