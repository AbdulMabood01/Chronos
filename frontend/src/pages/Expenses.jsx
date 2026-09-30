import React, { useEffect, useState } from 'react';
import { format } from 'date-fns';
import { expenseAPI, projectAPI } from '../api';
import { useAuth } from '../AuthContext';
import Icon from '../components/Icon';
import { hasActiveAssignment } from '../utils/projectAssignments';
import './Expenses.css';

const categories = ['TRAVEL', 'MEALS', 'SOFTWARE', 'EQUIPMENT', 'SUPPLIES', 'OTHER'];
const empty = { projectId: '', category: 'TRAVEL', amount: '', expenseDate: new Date().toISOString().slice(0, 10), description: '' };
const label = value => String(value || '').replaceAll('_', ' ').toLowerCase().replace(/^./, c => c.toUpperCase());
const money = value => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(Number(value || 0));
const date = value => value ? new Date(String(value).slice(0, 10) + 'T12:00:00').toLocaleDateString() : '—';
const message = error => error?.userMessage || error?.response?.data?.message || 'Unable to complete the request. Please try again.';

export async function downloadReceipt(id, name) {
  const response = await expenseAPI.receipt(id);
  const url = URL.createObjectURL(response.data);
  const link = document.createElement('a');
  link.href = url; link.download = name || 'receipt'; link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function ExpenseTable({ rows, onSelect, showEmployee = false }) {
  return <div className="expense-table-scroll"><table className="data-table expense-table"><thead><tr>
    {showEmployee && <th>Employee</th>}<th>Project</th><th>Category</th><th>Amount</th><th>Date</th><th>Status</th><th>Submitted</th><th>Reviewer comments</th><th>Actions</th>
  </tr></thead><tbody>{rows.map(row => <tr key={row.id}>
    {showEmployee && <td>{row.employee_name}</td>}<td>{row.project_code}</td><td>{label(row.category)}</td><td>{money(row.amount)}</td><td>{date(row.expense_date)}</td>
    <td><span className={'status-badge status-' + String(row.status).toLowerCase()}>{label(row.status)}</span></td>
    <td>{date(row.submitted_at)}</td><td>{row.reviewer_comments || '—'}</td><td><button className="button button-secondary button-small" type="button" onClick={() => onSelect(row)}>View</button></td>
  </tr>)}</tbody></table>{rows.length === 0 && <p className="expense-empty">No expenses found.</p>}</div>;
}

export function ProjectExpenseHistory({ projectId, revision = 0, showSummary = true }) {
  const [rows, setRows] = useState([]);
  const [totals, setTotals] = useState(null);
  const [filters, setFilters] = useState({ employee: '', category: '', status: '', from: '', to: '' });
  const [selected, setSelected] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    if (!projectId) return;
    let active = true;
    Promise.all([expenseAPI.project(projectId), expenseAPI.totals(projectId)]).then(([items, summary]) => {
      if (active) { setRows(items.data || []); setTotals(summary.data); setError(''); }
    }).catch(e => { if (active) setError(message(e)); });
    return () => { active = false; };
  }, [projectId, revision]);
  const employees = [...new Map(rows.map(row => [row.employee_id, row.employee_name])).entries()];
  const shown = rows.filter(row => (!filters.employee || String(row.employee_id) === filters.employee) &&
    (!filters.category || row.category === filters.category) && (!filters.status || row.status === filters.status) &&
    (!filters.from || String(row.expense_date).slice(0, 10) >= filters.from) && (!filters.to || String(row.expense_date).slice(0, 10) <= filters.to));
  return <section className="expense-project-history">
    {error && <p role="alert" className="error-message">{error}</p>}
    {showSummary && totals && <div className="expense-summary"><div><span>Expense Budget</span><strong>{totals.budget == null ? 'Not set' : money(totals.budget)}</strong></div><div><span>Approved Expenses</span><strong>{money(totals.approved)}</strong></div><div><span>Pending Expenses</span><strong>{money(totals.pending)}</strong></div><div><span>Remaining Budget</span><strong>{totals.remaining == null ? 'Not set' : money(totals.remaining)}</strong></div></div>}
    <div className="expense-filters">
      <label>Employee<select value={filters.employee} onChange={e => setFilters({ ...filters, employee: e.target.value })}><option value="">All</option>{employees.map(([id, name]) => <option key={id} value={id}>{name}</option>)}</select></label>
      <label>Category<select value={filters.category} onChange={e => setFilters({ ...filters, category: e.target.value })}><option value="">All</option>{categories.map(item => <option key={item} value={item}>{label(item)}</option>)}</select></label>
      <label>Status<select value={filters.status} onChange={e => setFilters({ ...filters, status: e.target.value })}><option value="">All</option>{['PENDING_APPROVAL','APPROVED','REJECTED','CHANGES_REQUESTED'].map(item => <option key={item} value={item}>{label(item)}</option>)}</select></label>
      <label>From<input type="date" value={filters.from} onChange={e => setFilters({ ...filters, from: e.target.value })} /></label>
      <label>To<input type="date" value={filters.to} onChange={e => setFilters({ ...filters, to: e.target.value })} /></label>
    </div>
    <ExpenseTable rows={shown} showEmployee onSelect={setSelected} />
    {selected && <ExpenseDetail id={selected.id} onClose={() => setSelected(null)} />}
  </section>;
}

function ExpenseDetail({ id, onClose }) {
  const [row, setRow] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => { expenseAPI.detail(id).then(res => setRow(res.data)).catch(e => setError(message(e))); }, [id]);
  return <div className="expense-detail" role="region" aria-label="Expense details"><button type="button" className="button button-secondary button-small" onClick={onClose}>Close</button>
    {error && <p role="alert">{error}</p>}{row && <><h3>{row.project_code} · {label(row.category)} · {money(row.amount)}</h3><p>{row.description}</p>
      {row.over_budget_at_submission && <p role="status">This claim exceeded the project budget when submitted.</p>}
      {row.receipt_name && <button type="button" className="button button-secondary button-small" onClick={() => downloadReceipt(id, row.receipt_name).catch(e => setError(message(e)))}>Download receipt: {row.receipt_name}</button>}
      <h4>Status history</h4><ol>{(row.history || []).map((item, index) => <li key={index}>{label(item.status)} · {item.actor_name} · {date(item.created_at)}{item.comment && <p>{item.comment}</p>}</li>)}</ol></>}
  </div>;
}

export default function Expenses() {
  const { user } = useAuth();
  const [projects, setProjects] = useState([]);
  const [projectsLoaded, setProjectsLoaded] = useState(false);
  const [mine, setMine] = useState([]);
  const [form, setForm] = useState(empty);
  const [receipt, setReceipt] = useState(null);
  const [editing, setEditing] = useState(null);
  const [selected, setSelected] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [budgetWarning, setBudgetWarning] = useState(false);
  const [formVersion, setFormVersion] = useState(0);
  const load = async () => {
    const [myRes, projectRes] = await Promise.all([expenseAPI.mine(), projectAPI.getAssignedProjects()]);
    const today = format(new Date(), 'yyyy-MM-dd');
    setMine([...(myRes.data || [])].sort((a, b) => String(b.submitted_at || b.created_at || '').localeCompare(String(a.submitted_at || a.created_at || '')))); setProjects((projectRes.data || []).filter(p => hasActiveAssignment(p, user?.id, today)));
    setProjectsLoaded(true);
  };
  useEffect(() => { load().catch(e => setError(message(e))); }, [user?.id]);
  useEffect(() => {
    if (!form.projectId || !Number(form.amount)) { setBudgetWarning(false); return; }
    let cancelled = false;
    const timer = setTimeout(() => expenseAPI.budgetCheck(form.projectId, form.amount, editing?.id)
      .then(({ data }) => { if (!cancelled) setBudgetWarning(Boolean(data.overBudget)); })
      .catch(() => { if (!cancelled) setBudgetWarning(false); }), 300);
    return () => { cancelled = true; clearTimeout(timer); };
  }, [form.projectId, form.amount, editing?.id]);
  const submit = async event => {
    event.preventDefault(); setBusy(true); setError('');
    try {
      await expenseAPI.save(editing?.id, { ...form, projectId: Number(form.projectId), amount: Number(form.amount) }, receipt);
      setForm(empty); setReceipt(null); setEditing(null); setFormVersion(value => value + 1); await load();
    } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };
  const pendingCount = mine.filter(row => row.status === 'PENDING_APPROVAL').length;
  const approvedTotal = mine.filter(row => row.status === 'APPROVED').reduce((sum, row) => sum + Number(row.amount || 0), 0);
  return <div className="page-container highlighted-workspace expenses-page">
    <header className="expense-page-hero"><div><span className="expense-kicker">PROJECT REIMBURSEMENT</span><h1>Expenses</h1><p>Track your claims and submit project expenses for approval.</p></div><div className="expense-hero-mark" aria-hidden="true"><Icon name="file" size={30}/></div></header>
    <div className="expense-overview" aria-label="Expense summary"><div><span>Total claims</span><strong>{mine.length}</strong></div><div><span>Awaiting approval</span><strong>{pendingCount}</strong></div><div><span>Approved amount</span><strong>{money(approvedTotal)}</strong></div></div>
    {error && <p role="alert" className="error-message">{error}</p>}
    {user?.role === 'EMPLOYEE' && projectsLoaded && projects.length === 0 && <div className="empty-state" role="status"><h2>No projects assigned</h2><p>You aren't currently assigned to any active projects. Contact your Project Admin if you believe this is incorrect.</p></div>}
    <div className="expense-workspace-grid">
    <section className="admin-panel expense-panel expense-my-panel"><div className="expense-panel-heading"><div><span className="expense-kicker">YOUR CLAIMS</span><h2>My expenses</h2><p>Most recent submissions first</p></div><span className="expense-count">{mine.length}</span></div>
      {mine.length === 0 ? <div className="expense-list-empty"><Icon name="file" size={26}/><h3>No expenses yet</h3><p>Your submitted expenses will appear here.</p></div> : <div className="expense-card-list">{mine.map(row => <article className="expense-item" key={row.id}><div className="expense-item-top"><span className="expense-item-category">{label(row.category)}</span><span className={'status-badge status-' + String(row.status).toLowerCase()}>{label(row.status)}</span></div><div className="expense-item-title"><h3>{row.project_code || 'Project expense'}</h3><strong>{money(row.amount)}</strong></div><p>{row.description}</p><div className="expense-item-footer"><span>{date(row.expense_date)}</span><span>{row.submitted_at ? `Submitted ${date(row.submitted_at)}` : 'Draft'}</span><button className="button button-secondary button-small" type="button" onClick={() => setSelected(row)}>View details <Icon name="arrow" size={15}/></button></div></article>)}</div>}
      {selected && <><ExpenseDetail id={selected.id} onClose={() => setSelected(null)} />{selected.status === 'CHANGES_REQUESTED' && <button className="button button-primary expense-resubmit" onClick={() => { setEditing(selected); setForm({ projectId: selected.project_id, category: selected.category, amount: selected.amount, expenseDate: String(selected.expense_date).slice(0,10), description: selected.description }); setSelected(null); document.getElementById('expense-entry')?.scrollIntoView({ behavior: 'smooth', block: 'start' }); }}>Edit and resubmit</button>}</>}
    </section>
    <section className="admin-panel expense-panel expense-entry-panel" id="expense-entry"><div className="expense-panel-heading"><div><span className="expense-kicker">{editing ? 'UPDATE CLAIM' : 'NEW CLAIM'}</span><h2>{editing ? 'Resubmit expense' : 'Submit an expense'}</h2><p>Add the details and attach a receipt.</p></div></div>
      <form onSubmit={submit} className="expense-form">
        <label>Project<select required value={form.projectId} disabled={!!editing} onChange={e => setForm({ ...form, projectId: e.target.value })}><option value="">Select assigned project</option>{projects.map(p => <option key={p.id} value={p.id}>{p.code} · {p.name}</option>)}</select></label>
        <label>Category<select value={form.category} onChange={e => setForm({ ...form, category: e.target.value })}>{categories.map(item => <option key={item} value={item}>{label(item)}</option>)}</select></label>
        <label>Amount<input type="number" min="0.01" step="0.01" required value={form.amount} onChange={e => setForm({ ...form, amount: e.target.value })} /></label>
        {budgetWarning && <p className="expense-wide" role="status">This expense would exceed the project budget. You can still submit it for review.</p>}
        <label>Expense date<input type="date" required value={form.expenseDate} onChange={e => setForm({ ...form, expenseDate: e.target.value })} /></label>
        <label className="expense-wide">Description<textarea required minLength={form.category === 'OTHER' ? 10 : 1} maxLength="2000" value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} /></label>
        <label className="expense-wide">Receipt or document (PDF, PNG, JPG, WebP; 10 MB max)<input key={formVersion} type="file" accept=".pdf,.png,.jpg,.jpeg,.webp" required={!editing} onChange={e => setReceipt(e.target.files[0] || null)} /></label>
        <div className="expense-wide"><button className="button button-primary" disabled={busy || !projects.length}>{editing ? 'Resubmit for approval' : 'Submit for approval'}</button>{editing && <button className="button button-secondary" type="button" onClick={() => { setEditing(null); setForm(empty); setReceipt(null); }}>Cancel</button>}</div>
      </form>
    </section>
    </div>
  </div>;
}
