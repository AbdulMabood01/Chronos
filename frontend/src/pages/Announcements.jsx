import React, { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { announcementAPI } from '../api';
import { useAuth } from '../AuthContext';
import './Announcements.css';

const label = value => value.charAt(0) + value.slice(1).toLowerCase();
const date = value => new Date(`${value}T12:00:00`).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
const failure = e => e.response?.data?.message || 'Unable to complete this action. Please try again.';
function Meta({ item }) {
  return <div className="announcement-meta"><span className={`announcement-priority ${item.priority.toLowerCase()}`}>{label(item.priority)}</span><span>{date(item.publish_date)}</span>{!item.viewed_at && <span className="announcement-unread">Unread</span>}{item.acknowledgment_required && <span>{item.acknowledged_at ? 'Acknowledged' : 'Acknowledgment required'}</span>}</div>;
}

export function AnnouncementPanel() {
  const [items, setItems] = useState([]);
  const [state, setState] = useState('loading');
  useEffect(() => {
    let active = true;
    announcementAPI.list().then(r => { if (active) { setItems(r.data); setState('ready'); } }).catch(() => { if (active) setState('error'); });
    return () => { active = false; };
  }, []);
  return <section className="announcements announcement-panel" aria-label="Company announcements"><div className="announcement-heading"><div><span className="eyebrow">IN THE LOOP</span><h2>Company announcements</h2></div><Link to="/announcements">View all →</Link></div>
    {state === 'loading' && <p role="status">Loading announcements…</p>}{state === 'error' && <p role="alert">Announcements could not be loaded. <Link to="/announcements">Try the announcements page</Link>.</p>}
    {state === 'ready' && !items.length && <p>No announcements right now. You're all caught up.</p>}
    <div className="announcement-preview-grid">{items.slice(0, 3).map(item => <Link className={`announcement-card ${!item.viewed_at ? 'unread' : ''} ${item.priority.toLowerCase()}`} key={item.id} to={`/announcements?id=${item.id}`}><Meta item={item}/><h3>{item.title}</h3><p className="announcement-excerpt">{item.content}</p><span className="announcement-open">Read announcement →</span></Link>)}</div>
  </section>;
}

const emptyForm = () => ({ title: '', content: '', publishDate: new Date().toISOString().slice(0, 10), expirationDate: '', priority: 'NORMAL', status: 'DRAFT', acknowledgmentRequired: false, version: 0 });
export default function Announcements() {
  const { user } = useAuth();
  const admin = user?.role === 'SUPER_ADMIN';
  const [params, setParams] = useSearchParams();
  const [management, setManagement] = useState(false);
  const [items, setItems] = useState([]);
  const [selected, setSelected] = useState(null);
  const [tracking, setTracking] = useState(null);
  const [pendingOnly, setPendingOnly] = useState(true);
  const [form, setForm] = useState(null);
  const [file, setFile] = useState(null);
  const [filter, setFilter] = useState('ALL');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const id = params.get('id');
  const reload = async () => { const r = await announcementAPI.list(management); setItems(r.data); };
  useEffect(() => {
    let active = true;
    setLoading(true); setError(''); setSelected(null); setTracking(null); setForm(null);
    Promise.all([announcementAPI.list(management), id ? announcementAPI.open(id, management) : Promise.resolve(null)])
      .then(([list, detail]) => { if (active) { setItems(list.data.map(i => detail?.data.id === i.id ? detail.data : i)); setSelected(detail?.data || null); } })
      .catch(e => { if (active) setError(failure(e)); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [management, id]);
  const act = async callback => {
    setBusy(true); setError(''); setNotice('');
    try { await callback(); } catch (e) { setError(failure(e)); } finally { setBusy(false); }
  };
  const edit = item => {
    setFile(null); setTracking(null);
    setForm(item ? { id: item.id, title: item.title, content: item.content, publishDate: item.publish_date, expirationDate: item.expiration_date || '', priority: item.priority, status: item.status, acknowledgmentRequired: item.acknowledgment_required, version: item.version, existingAttachment: item.attachment_name } : emptyForm());
  };
  const field = (key, value) => setForm(current => ({ ...current, [key]: value }));
  const save = e => {
    e.preventDefault();
    act(async () => {
      const data = { ...form, expirationDate: form.expirationDate || null };
      if (file) {
        if (file.size > 5 * 1024 * 1024 || file.size === 0) throw new Error('Choose a file between 1 byte and 5 MB.');
        data.attachmentName = file.name;
        data.attachmentBase64 = await new Promise((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(reader.result.split(',')[1]); reader.onerror = reject; reader.readAsDataURL(file); });
      }
      const r = await announcementAPI.save(form.id, data);
      setForm(null); setFile(null); setSelected((await announcementAPI.open(r.data, true)).data); setParams({ id: r.data }); await reload(); setNotice('Announcement saved.');
    });
  };
  const changeStatus = status => act(async () => {
    await announcementAPI.status(selected.id, status, selected.version);
    setSelected((await announcementAPI.open(selected.id, true)).data); await reload(); setNotice(`Announcement ${status.toLowerCase()}.`);
  });
  const download = () => act(async () => {
    const r = await announcementAPI.attachment(selected.id, management);
    const url = URL.createObjectURL(r.data); const link = document.createElement('a'); link.href = url; link.download = selected.attachment_name; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
  });
  const filtered = items.filter(item => filter === 'ALL' || (filter === 'UNREAD' ? !item.viewed_at : filter === 'PENDING' ? item.acknowledgment_required && !item.acknowledged_at : item.status === filter));
  return <div className="page-container announcements"><div className="announcement-heading"><div><span className="eyebrow">COMPANY UPDATES</span><h1>Announcements</h1><p>News, updates, and the things we need you to know.</p></div>{admin && management && <button className="button" disabled={busy} onClick={() => edit(null)}>New announcement</button>}</div>
    {admin && <div className="announcement-tabs"><button disabled={busy} aria-pressed={!management} onClick={() => { setManagement(false); setFilter('ALL'); setParams({}); }}>Employee view</button><button disabled={busy} aria-pressed={management} onClick={() => { setManagement(true); setFilter('ALL'); setParams({}); }}>Manage announcements</button></div>}
    {error && <div className="error-message" role="alert">{error} <button disabled={busy} onClick={() => act(async () => { await reload(); if (id) setSelected((await announcementAPI.open(id, management)).data); })}>Reload</button></div>}{notice && <p role="status">{notice}</p>}
    {form ? <form className="announcement-editor announcement-card" onSubmit={save}><h2>{form.id ? 'Edit announcement' : 'New announcement'}</h2><fieldset disabled={busy}>
      <label>Title<input required maxLength={200} value={form.title} onChange={e => field('title', e.target.value)}/></label>
      <label>Message<textarea required maxLength={50000} rows={8} value={form.content} onChange={e => field('content', e.target.value)}/></label>
      <div className="announcement-form-grid"><label>Publish date<input required type="date" value={form.publishDate} onChange={e => field('publishDate', e.target.value)}/></label><label>Expiration date (optional)<input type="date" min={form.publishDate} value={form.expirationDate} onChange={e => field('expirationDate', e.target.value)}/></label><label>Priority<select value={form.priority} onChange={e => field('priority', e.target.value)}>{['NORMAL', 'IMPORTANT', 'URGENT'].map(v => <option key={v} value={v}>{label(v)}</option>)}</select></label><label>Status<select value={form.status} onChange={e => field('status', e.target.value)}>{['DRAFT', 'PUBLISHED', 'ARCHIVED'].map(v => <option key={v} value={v}>{label(v)}</option>)}</select></label></div>
      <p className="announcement-hint">Dates use UTC. Published announcements appear on their publish date and remain visible through their expiration date.</p>
      <label className="announcement-checkbox"><input type="checkbox" checked={form.acknowledgmentRequired} onChange={e => field('acknowledgmentRequired', e.target.checked)}/>Require employee acknowledgment</label>
      <label>Attachment (optional, up to 5 MB)<input type="file" onChange={e => { const next = e.target.files[0]; if (next && (next.size > 5 * 1024 * 1024 || !next.size)) { setError('Choose a non-empty file up to 5 MB.'); e.target.value = ''; setFile(null); } else { setError(''); setFile(next || null); } }}/></label>
      {form.existingAttachment && <label className="announcement-checkbox"><input type="checkbox" checked={!!form.removeAttachment} onChange={e => field('removeAttachment', e.target.checked)}/>Remove {form.existingAttachment}</label>}
      {form.id && <p className="announcement-hint">Saving edits resets views and acknowledgments for this announcement. Publishing or archiving without editing preserves tracking.</p>}
      <div className="announcement-actions"><button className="button" type="submit">{busy ? 'Saving…' : 'Save announcement'}</button><button type="button" onClick={() => setForm(null)}>Cancel</button></div>
    </fieldset></form> : selected ? <article className={`announcement-card announcement-detail ${selected.priority.toLowerCase()}`}><button disabled={busy} onClick={() => setParams({})}>← All announcements</button><Meta item={selected}/><h2>{selected.title}</h2>{management && <p>Status: {label(selected.status)}{selected.publish_date > new Date().toISOString().slice(0, 10) && ' · Scheduled'}{selected.expiration_date && selected.expiration_date < new Date().toISOString().slice(0, 10) && ' · Expired'}</p>}<div className="announcement-content">{selected.content}</div>
      {selected.expiration_date && <p className="announcement-hint">Available through {date(selected.expiration_date)}</p>}
      {selected.attachment_name && <button disabled={busy} onClick={download}>Download {selected.attachment_name}</button>}
      {!management && selected.acknowledgment_required && <div className="announcement-ack">{selected.acknowledged_at ? <p role="status">✓ You acknowledged this announcement.</p> : <><p>Please confirm that you have read this announcement.</p><button className="button" disabled={busy} onClick={() => act(async () => { await announcementAPI.acknowledge(selected.id, selected.version); setSelected((await announcementAPI.open(selected.id)).data); await reload(); })}>Acknowledge announcement</button></>}</div>}
      {management && <><div className="announcement-actions"><button disabled={busy} onClick={() => edit(selected)}>Edit</button>{selected.status !== 'PUBLISHED' && <button disabled={busy} onClick={() => changeStatus('PUBLISHED')}>Publish</button>}{selected.status !== 'ARCHIVED' && <button disabled={busy} onClick={() => changeStatus('ARCHIVED')}>Archive</button>}<button disabled={busy} onClick={() => act(async () => setTracking((await announcementAPI.tracking(selected.id)).data))}>View tracking</button><button className="announcement-delete" disabled={busy} onClick={() => { if (window.confirm('Delete this announcement and all its tracking? This cannot be undone.')) act(async () => { await announcementAPI.remove(selected.id, selected.version); setParams({}); await reload(); }); }}>Delete</button></div>
      {tracking && <section aria-label="Announcement tracking"><h3>Employee engagement</h3><div className="announcement-statistics"><span><strong>{tracking.viewed} / {tracking.total}</strong> viewed</span><span><strong>{tracking.acknowledged} / {tracking.total}</strong> acknowledged</span></div><p className="announcement-hint">Includes all active employee accounts. Management previews do not count as views.</p><label className="announcement-checkbox"><input type="checkbox" checked={pendingOnly} onChange={e => setPendingOnly(e.target.checked)}/>Show only employees who have not acknowledged</label><div className="announcement-table"><table><thead><tr><th>Employee</th><th>Viewed</th><th>Acknowledged</th></tr></thead><tbody>{tracking.employees.filter(e => !pendingOnly || !e.acknowledged_at).map(e => <tr key={e.id}><td>{e.name}<small>{e.email}</small></td><td>{e.viewed_at ? 'Yes' : 'No'}</td><td>{e.acknowledged_at ? 'Yes' : 'No'}</td></tr>)}</tbody></table>{pendingOnly && tracking.employees.every(e => e.acknowledged_at) && <p>Everyone has acknowledged this announcement.</p>}</div></section>}</>}
    </article> : <><label className="announcement-filter">Show<select value={filter} onChange={e => setFilter(e.target.value)}>{(management ? ['ALL', 'DRAFT', 'PUBLISHED', 'ARCHIVED'] : ['ALL', 'UNREAD', 'PENDING']).map(v => <option key={v} value={v}>{v === 'PENDING' ? 'Awaiting acknowledgment' : label(v)}</option>)}</select></label>{loading ? <p role="status">Loading announcements…</p> : <div className="announcement-list">{filtered.map(item => <button key={item.id} className={`announcement-card ${!item.viewed_at && !management ? 'unread' : ''} ${item.priority.toLowerCase()}`} onClick={() => setParams({ id: item.id })}><Meta item={item}/><h2>{item.title}</h2><p className="announcement-excerpt">{item.content}</p>{management && <span>{label(item.status)}</span>}<span className="announcement-open">Open announcement →</span></button>)}{!filtered.length && !error && <div className="announcement-card"><h2>You're all caught up</h2><p>No announcements match this view.</p></div>}</div>}</>}
  </div>;
}
