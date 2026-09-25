import { useEffect, useRef, useState } from 'react';
import { employeeReportsAPI } from '../api';
import { categories, ReportProgress } from './EmployeeReports';

const label = value => value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
const date = value => value ? new Date(value).toLocaleString() : '—';

export default function SubmittedReports({ SubmissionForm }) {
  const [refresh, setRefresh] = useState(0);
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [selected, setSelected] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
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
  }, [selectedId, page, refresh]);
  useEffect(() => {
    if (selectedId) heading.current?.focus();
    else if (returnId.current) document.getElementById('view-report-' + returnId.current)?.focus();
  }, [selectedId, loading]);
  return <>
    <div hidden={!!selectedId}>
      <SubmissionForm onSubmitted={() => { setPage(0); setRefresh(v => v + 1); }} />
      <section className="concern-card" aria-label="Submitted Reports">
        <div className="concern-heading"><div><h2>Submitted Reports</h2><p className="concern-help">Your identified reports · Automatically refreshes every 15 seconds</p></div><button disabled={loading} onClick={() => setRefresh(v => v + 1)}>Refresh reports</button></div>
        {!selectedId && error && <p role="alert" className="concern-error">{error}</p>}
        {loading && !selectedId ? <p role="status">Loading reports…</p> : !rows.length ? <p>{error ? 'Reports could not be loaded. Please refresh to retry.' : 'No submitted reports yet.'}</p> : <div className="concern-table"><table><thead><tr><th>Report</th><th>Submitted</th><th>Status</th><th>Last updated</th><th>Actions</th></tr></thead><tbody>{rows.map(row => <tr key={row.id}><td><strong>{row.subject}</strong><small>{categories[row.category]}</small><code className="concern-id">{row.id}</code></td><td>{date(row.submitted_at)}</td><td><span className="concern-status">{label(row.status)}</span></td><td>{date(row.updated_at || row.submitted_at)}</td><td className="concern-actions"><button id={'view-report-' + row.id} aria-label={'View ' + row.subject} onClick={() => { returnId.current = row.id; setSelected(null); setSelectedId(row.id); }}>View</button></td></tr>)}</tbody></table></div>}
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
