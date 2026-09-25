import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../AuthContext';
import { feedbackReviewsAPI as api } from '../api';
import './FeedbackReviews.css';

const categories = { APPRECIATION: 'Appreciation / Recognition', GENERAL: 'General Feedback', COLLABORATION: 'Collaboration / Teamwork', COMMUNICATION: 'Communication', IMPROVEMENT: 'Areas for Improvement', OTHER: 'Other' };
const feedbackFields = [['general', 'General Feedback', 'Overall thoughts'], ['improvements', 'Areas for Improvement', 'What could be better?'], ['recognition', 'Continue the Great Work', 'What is working well?'], ['suggestions', 'Suggestions / Ideas', 'New ideas or recommendations'], ['comments', 'Additional Comments', 'Optional comments']];
const emptyFeedback = Object.fromEntries(feedbackFields.map(([key]) => [key, '']));
const fields = { summary: 'Overall performance summary', accomplishments: 'Key accomplishments', strengths: 'Strengths', improvements: 'Areas for improvement', goals: 'Goals / expectations for the next quarter', comments: 'Additional comments' };
const message = e => e.response?.data?.message || 'Unable to complete the request. Please try again.';
const date = value => value ? new Date(value).toLocaleString() : 'Not published';
const yearOf = value => new Date(value).getFullYear();

function FeedbackContent({ content }) {
  const sections = [];
  let current = { title: '', lines: [] };
  for (const line of (content || '').split(/\r?\n/)) {
    const title = line.trim().replace(/^\[DEMO\]\s*/, '');
    if (feedbackFields.some(([, label]) => label === title)) {
      if (current.title || current.lines.some(text => text.trim())) sections.push(current);
      current = { title: line.trim(), lines: [] };
    } else current.lines.push(line);
  }
  if (current.title || current.lines.some(text => text.trim())) sections.push(current);
  return <div className="feedback-card-content">{sections.map((section, index) => <section className="feedback-card-section" key={index}>
    {section.title && <h3>{section.title}</h3>}
    <p>{section.lines.join('\n').trim()}</p>
  </section>)}</div>;
}

export function EmployeeSearch({ selected, onSelect, exclude }) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  useEffect(() => {
    let active = true;
    setResults([]); setError('');
    if (query.trim().length < 2 || selected) { setLoading(false); return; }
    setLoading(true);
    const timer = setTimeout(() => api.employees(query).then(r => { if (active) setResults(r.data.filter(u => u.id !== exclude)); })
      .catch(e => { if (active) setError(message(e)); }).finally(() => { if (active) setLoading(false); }), 250);
    return () => { active = false; clearTimeout(timer); };
  }, [query, selected, exclude]);
  return <div className="employee-search">
    <label>Search employee by name or email<input value={query} maxLength={200} onChange={e => { setQuery(e.target.value); onSelect(null); }} placeholder="Enter at least 2 characters" /></label>
    {selected && <p role="status">Selected: <strong>{selected.first_name} {selected.last_name}</strong> ({selected.email}) <button type="button" onClick={() => { onSelect(null); setQuery(''); }}>Change employee</button></p>}
    {error && <p role="alert">{error}</p>}{loading && <p role="status">Searching…</p>}
    {!selected && !loading && query.trim().length >= 2 && !error && !results.length && <p>No employees found.</p>}
    {!selected && results.length > 0 && <ul className="employee-results">{results.map(u => <li key={u.id}><button type="button" onClick={() => { onSelect(u); setQuery(''); }}>{u.first_name} {u.last_name} <small>{u.email}</small></button></li>)}</ul>}
  </div>;
}

export function FeedbackPage() {
  const { user } = useAuth();
  const [tab, setTab] = useState('received');
  const [rows, setRows] = useState([]);
  const [selected, setSelected] = useState(null);
  const [draft, setDraft] = useState(emptyFeedback);
  const content = feedbackFields.filter(([key]) => draft[key].trim()).map(([key, label]) => `${label}\n${draft[key].trim()}`).join('\n\n');
  const [category, setCategory] = useState('');
  const [anonymous, setAnonymous] = useState(false);
  const [filters, setFilters] = useState({ year: '', category: '', sender: '', privacy: '' });
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let active = true; setRows([]); setError('');
    if (tab === 'submit') { setBusy(false); return; }
    setBusy(true);
    api.feedback(tab === 'given').then(r => { if (active) setRows(r.data); }).catch(e => { if (active) setError(message(e)); }).finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [tab]);
  async function submit(e) {
    e.preventDefault();
    if (busy) return;
    if (!selected || !draft.general.trim() || content.length > 20000) { setError('Select an employee, enter General Feedback, and keep feedback within 20,000 characters.'); return; }
    setBusy(true); setError(''); setNotice('');
    try { await api.submit({ employeeId: selected.id, content, category: category || null, anonymous }); setDraft(emptyFeedback); setSelected(null); setCategory(''); setAnonymous(false); setNotice('Feedback submitted.'); setTab('given'); }
    catch (e) { setError(message(e)); } finally { setBusy(false); }
  }
  const years = [...new Set(rows.map(r => yearOf(r.submitted_at)))].sort((a,b) => b-a);
  const historyTitle = tab === 'given' ? "Feedback I've Given" : 'My Feedback';
  const historyDescription = tab === 'given' ? 'Feedback you have sent to colleagues.' : 'Feedback colleagues have sent to you.';
  const emptyMessage = Object.values(filters).some(Boolean)
    ? 'No feedback matches this selection.'
    : tab === 'given' ? "You haven't given any feedback yet." : "You haven't received any feedback yet.";
  const visible = rows.filter(r => (!filters.year || String(yearOf(r.submitted_at)) === filters.year) && (!filters.category || r.category === filters.category) && (!filters.sender || r.sender_type === filters.sender) && (!filters.privacy || String(r.anonymous) === filters.privacy));
  const filter = (key, label, choices) => <label>{label}<select value={filters[key]} onChange={e => setFilters({ ...filters, [key]: e.target.value })}><option value="">All</option>{choices.map(([value,label]) => <option key={value} value={value}>{label}</option>)}</select></label>;
  return <div className="page-container feedback-reviews feedback-page"><h1>Feedback</h1><p className="page-subtitle">Share recognition and constructive feedback with colleagues.</p>
    <div className="review-tabs">{[['received','My Feedback'],['given',"Feedback I've Given"],['submit','Submit Feedback']].map(([key,label]) => <button key={key} disabled={busy && tab === 'submit'} aria-pressed={tab===key} onClick={() => { setTab(key); setFilters({ year:'', category:'', sender:'', privacy:'' }); }}>{label}</button>)}</div>
    {error && <p className="feedback-message feedback-error" role="alert">{error}</p>}{notice && <p className="feedback-message" role="status">{notice}</p>}
    {tab === 'submit' ? <form className="card feedback-form" onSubmit={submit} aria-busy={busy}>
      <header><h2>Share feedback</h2><p className="page-subtitle">Be specific, thoughtful, and constructive. General Feedback is required; all other prompts are optional.</p></header>
      <fieldset disabled={busy}>
        <div className="feedback-recipient"><EmployeeSearch selected={selected} onSelect={setSelected} exclude={user.id}/>
          <label>Category (optional)<select value={category} onChange={e => setCategory(e.target.value)}><option value="">No category</option>{Object.entries(categories).map(([key,label]) => <option key={key} value={key}>{label}</option>)}</select></label>
        </div>
        <div className="feedback-prompts">{feedbackFields.map(([key, label, hint]) => <div className={'form-group feedback-prompt feedback-prompt-' + key} key={key}>
          <label htmlFor={'feedback-' + key}>{label}{key === 'general' && <span aria-hidden="true"> *</span>}</label>
          <p id={'feedback-' + key + '-hint'} className="feedback-hint">{hint}</p>
          <textarea id={'feedback-' + key} aria-describedby={'feedback-' + key + '-hint'} required={key === 'general'} rows={key === 'general' ? 5 : 4} maxLength={20000} value={draft[key]} onChange={e => setDraft({ ...draft, [key]: e.target.value })}/>
        </div>)}</div>
        <div className="feedback-privacy"><label className="review-checkbox"><input type="checkbox" checked={anonymous} onChange={e => setAnonymous(e.target.checked)}/>Anonymous Feedback</label>
          <p className="page-subtitle" aria-live="polite">{anonymous ? 'Anonymous: your identity will be hidden from the recipient. Avoid identifying yourself in the feedback text.' : 'Identified: the recipient will see your name.'}</p>
        </div>
        <footer className="feedback-submit"><div><p className={content.length > 20000 ? 'feedback-limit-error' : 'page-subtitle'} aria-live="polite">{content.length.toLocaleString()} / 20,000 characters, including section headings</p><p className="page-subtitle">{!selected ? 'Select an employee to continue.' : !draft.general.trim() ? 'Add your overall thoughts to continue.' : anonymous ? 'Ready to send anonymously.' : 'Your name will be included.'}</p></div>
          <button className="button button-primary" disabled={busy || !selected || !draft.general.trim() || content.length > 20000}>{busy ? 'Submitting...' : anonymous ? 'Submit anonymous feedback' : 'Submit identified feedback'}</button>
        </footer>
      </fieldset>
    </form> : <><h2>{historyTitle}</h2><p className="page-subtitle">{historyDescription}</p>{busy ? <p role="status">Loading feedback…</p> : <><div className="review-filters">{filter('year','Year',years.map(y => [y,y]))}{filter('category','Category',Object.entries(categories))}{tab !== 'given' && filter('sender','Sender type',[['Manager','Manager'],['Employee','Employee']])}{filter('privacy','Identity',[['true','Anonymous'],['false','Identified']])}</div>
      {!visible.length && <p>{emptyMessage}</p>}{years.filter(y => visible.some(r => yearOf(r.submitted_at)===y)).map(y => <section key={y}><h2>{y}</h2>{visible.filter(r => yearOf(r.submitted_at)===y).map(r => <article className="review-card feedback-history-card" key={r.id}><div className="review-meta"><strong>{categories[r.category] || 'Uncategorized'}</strong><time>{date(r.submitted_at)}</time></div><FeedbackContent content={r.content}/><p>{tab === 'given' ? `To: ${r.recipient_name} (${r.recipient_email}) · ${r.anonymous ? 'Anonymous to recipient' : 'Identified'}` : `Submitted by: ${r.anonymous ? 'Anonymous' : r.sender_name}`} · {r.sender_type}</p></article>)}</section>)}</>}</>}
  </div>;
}

export function PerformanceReviewsPage() {
  const { user } = useAuth();
  const admin = user.role === 'ADMIN';
  const [employee, setEmployee] = useState(null);
  const [rows, setRows] = useState([]);
  const [year, setYear] = useState('');
  const [quarter, setQuarter] = useState('');
  const [form, setForm] = useState(null);
  const [audit, setAudit] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [revision, setRevision] = useState(0);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [searchYear, setSearchYear] = useState('');
  const [searchQuarter, setSearchQuarter] = useState('');
  const desiredPeriod = useRef(null);
  useEffect(() => {
    let active = true; setRows([]); setForm(null); setAudit(null); setError(''); setYear(''); setQuarter('');
    setBusy(true);
    api.reviews(admin ? employee?.id : undefined).then(r => { if (active) { setRows(r.data); const latest = r.data.find(row => desiredPeriod.current && row.review_year === desiredPeriod.current.year && row.quarter === desiredPeriod.current.quarter) || r.data[0]; desiredPeriod.current = null; if (latest) { setYear(String(latest.review_year)); setQuarter(String(latest.quarter)); } } })
      .catch(e => { if (active) setError(message(e)); }).finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [employee, admin, revision]);
  const selected = rows.find(r => String(r.review_year) === year && String(r.quarter) === quarter);
  const years = [...new Set([new Date().getFullYear(), ...rows.map(r => r.review_year)])].sort((a,b) => b-a);
  const matchingReviews = rows.filter(row => (row.employee_name + ' ' + row.employee_email).toLowerCase().includes(search.toLowerCase()) && (!status || Boolean(row.published_at) === (status === 'published'))
    && (!searchYear || String(row.review_year) === searchYear)
    && (!searchQuarter || String(row.quarter) === searchQuarter));
  async function save(e) {
    e.preventDefault(); if (busy || !form.summary.trim()) return; setBusy(true); setError(''); setNotice('');
    try { await api.save(form.id, { ...form, employeeId: employee.id, year: Number(form.year), quarter: Number(form.quarter) }); desiredPeriod.current = { year: Number(form.year), quarter: Number(form.quarter) }; setNotice('Review saved.'); setRevision(r => r+1); }
    catch (e) { setError(message(e)); } finally { setBusy(false); }
  }
  async function publish() {
    setBusy(true); setError('');
    try { await api.publish(selected.id, selected.version); desiredPeriod.current = { year: selected.review_year, quarter: selected.quarter }; setNotice('Review published to the employee.'); setRevision(r => r+1); }
    catch (e) { setError(message(e)); } finally { setBusy(false); }
  }
  return <div className="page-container feedback-reviews feedback-page performance-page"><header className="performance-page-header"><div><span className="eyebrow">DEVELOPMENT &amp; GROWTH</span><h1>Performance Reviews</h1><p className="page-subtitle">Reflect on achievements, recognize strengths, and plan what comes next.</p></div><span className="performance-access">{admin ? "Review management" : "My quarterly reviews"}</span></header><p className="performance-visibility">{admin ? "Drafts are visible only to Admins. Publish a review when it is ready to share." : "Your published quarterly reviews, all in one place. Reviews are read-only."}</p>
    {admin && <section className="card performance-directory"><><div className="performance-heading"><div><h2>{employee ? 'Employee review history' : 'All employee reviews'}</h2><p className="page-subtitle">Search an employee to create a quarterly review or view their history.</p></div>{employee && <button disabled={busy} onClick={() => setEmployee(null)}>All employee reviews</button>}</div><fieldset disabled={busy || !!form}><EmployeeSearch selected={employee} onSelect={value => { setEmployee(value); setNotice(''); }}/></fieldset></></section>}
    {error && <p className="feedback-message feedback-error" role="alert">{error}</p>}{notice && <p className="feedback-message" role="status">{notice}</p>}
    {admin && employee && !form && <button className="button button-primary" disabled={busy} onClick={() => { setForm({ year: new Date().getFullYear(), quarter: Math.floor(new Date().getMonth()/3)+1, version: 0, ...Object.fromEntries(Object.keys(fields).map(k => [k,''])) }); setAudit(null); }}>Create quarterly review</button>}
    {admin && !employee && !busy && <section className="card performance-directory">
      <div className="review-filters"><label>Filter by employee<input placeholder="Name or email" value={search} onChange={e => setSearch(e.target.value)}/></label><label>Year<select value={searchYear} onChange={e => setSearchYear(e.target.value)}><option value="">All years</option>{years.map(y => <option key={y} value={y}>{y}</option>)}</select></label><label>Quarter<select value={searchQuarter} onChange={e => setSearchQuarter(e.target.value)}><option value="">All quarters</option>{[1,2,3,4].map(q => <option key={q} value={q}>Q{q}</option>)}</select></label><label>Review status<select value={status} onChange={e => setStatus(e.target.value)}><option value="">All statuses</option><option value="draft">Draft</option><option value="published">Published</option></select></label></div>
      <div className="performance-list">{matchingReviews.map(row => <button className="performance-list-item" key={row.id} onClick={() => { desiredPeriod.current = { year: row.review_year, quarter: row.quarter }; setEmployee({ id: row.employee_id, first_name: row.first_name, last_name: row.last_name, email: row.employee_email }); }}><span><strong>{row.employee_name}</strong><small>{row.employee_email}</small></span><span>Q{row.quarter} {row.review_year}</span><span className={`performance-status ${row.published_at ? "is-published" : "is-draft"}`}>{row.published_at ? 'Published' : 'Draft'}</span><span>View review &rarr;</span></button>)}</div>
      {!matchingReviews.length && <p className="page-subtitle">No reviews match this selection. Search an employee above to create a review.</p>}
    </section>}
    {busy && <p role="status">Loading…</p>}
    {form ? <form className="card performance-form" onSubmit={save}><fieldset disabled={busy}><h2>{form.id ? 'Edit review' : 'New quarterly review'} — {employee.first_name} {employee.last_name}</h2>
      {form.published_at && <p>Changes to this published review will be visible to the employee when saved.</p>}
      <div className="review-filters"><label>Review year<input type="number" min="1900" max="9999" required disabled={!!form.id} value={form.year} onChange={e => setForm({ ...form, year: e.target.value })}/></label><label>Quarter<select disabled={!!form.id} value={form.quarter} onChange={e => setForm({ ...form, quarter: e.target.value })}>{[1,2,3,4].map(q => <option key={q} value={q}>Q{q}</option>)}</select></label></div>
      <div className="performance-fields">{Object.entries(fields).map(([key,label]) => <label className={key === 'summary' || key === 'goals' ? 'performance-wide' : ''} key={key}>{label}{key === 'summary' ? ' (required)' : ''}<textarea required={key === 'summary'} maxLength={20000} value={form[key] || ''} onChange={e => setForm({ ...form, [key]: e.target.value })}/></label>)}</div>
      <div className="review-tabs"><button className="button button-primary" disabled={busy || !form.summary.trim()}>{busy ? 'Saving...' : 'Save review'}</button><button type="button" disabled={busy} onClick={() => setForm(null)}>Cancel</button></div><p className="page-subtitle">{form.published_at ? 'Saving updates the published review.' : 'Save a draft first, then publish it to the employee.'}</p></fieldset></form>
    : (!admin || employee) && <><div className="review-filters"><label>Year<select value={year} onChange={e => { setYear(e.target.value); setAudit(null); }}><option value="">Select year</option>{years.map(y => <option key={y}>{y}</option>)}</select></label><label>Quarter<select value={quarter} onChange={e => { setQuarter(e.target.value); setAudit(null); }}><option value="">Select quarter</option>{[1,2,3,4].map(q => <option key={q} value={q}>Q{q}</option>)}</select></label></div>
      {selected ? <article className="review-card performance-review"><header className="performance-review-header"><span className={`performance-status ${selected.published_at ? "is-published" : "is-draft"}`}>{selected.published_at ? "Published" : "Draft"}</span><h2>Performance Review — Q{selected.quarter} {selected.review_year}</h2><p>{selected.employee_name} · {selected.published_at ? `Published ${date(selected.published_at)}` : 'Draft'}</p></header>
        <div className="performance-review-sections">{Object.entries(fields).map(([key,label], index) => <section className={key === "summary" || key === "goals" ? "performance-wide" : ""} key={key}><h3><span className="performance-section-number" aria-hidden="true">{String(index + 1).padStart(2, "0")}</span>{label}</h3><p className={`review-text${selected[key] ? "" : " performance-unfilled"}`}>{selected[key] || "No comments added."}</p></section>)}</div>
        {admin && <><p>Created {date(selected.created_at)} · Created by user #{selected.created_by} · Last modified {date(selected.modified_at)}</p><div className="review-tabs"><button disabled={busy} onClick={() => setForm({ ...selected, year: selected.review_year })}>Edit review</button>{!selected.published_at && <button className="button button-primary" disabled={busy} onClick={publish}>Publish to employee</button>}<button disabled={busy} onClick={async () => { setBusy(true); try { setAudit((await api.audit(selected.id)).data); } catch(e) { setError(message(e)); } finally { setBusy(false); } }}>View audit history</button></div>
          {audit && <section><h3>Audit history</h3>{audit.map(a => <p key={a.id}>{a.action} · {date(a.recorded_at)} · User #{a.actor_id}</p>)}</section>}</>}
      </article> : !busy && <p className="performance-empty">No {admin ? '' : 'published '}review for this period.</p>}</>}
  </div>;
}
