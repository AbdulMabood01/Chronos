import LeaveBalancePreview from '../components/LeaveBalancePreview';
import { RecordSummary } from '../components/ScreenTitle';
import { formatDate } from '../utils/dates';
import React, { useEffect, useMemo, useState } from 'react';
import { userAPI, vacationAPI } from '../api';
import { eachDayOfInterval, isWeekend, parseISO } from 'date-fns';
import { LoadingIndicator } from '../components/Hourglass';
import '../styles.css';
import { useAuth } from '../AuthContext';
import LeaveBalancePanel from '../components/LeaveBalancePanel';
import Icon from '../components/Icon';
import './VacationRequests.css';

const leaveTypes = [
  ['VACATION', 'Vacation'],
  ['SICK', 'Sick Leave'],
  ['BEREAVEMENT', 'Bereavement Leave'],
  ['UNPAID_LEAVE', 'Unpaid Leave'],
  ['SPECIAL', 'Special Leave'],
];

const specialReasons = ['Personal', 'Parental', 'Maternity', 'Paternity', 'Adoption', 'Jury duty', 'Military', 'Family care', 'Religious', 'Other'];
const paidBalanceKeyByType = { VACATION: 'vacation', SICK: 'sick', BEREAVEMENT: 'bereavement' };
const leaveLabel = value => leaveTypes.find(([type]) => type === value)?.[1] || String(value || '').replaceAll('_', ' ').toLowerCase().replace(/^./, c => c.toUpperCase());

const emptyForm = {
  startDate: '',
  endDate: '',
  vacationType: 'VACATION',
  specialReason: '',
  notes: '',
};

function workingDaysBetween(startDate, endDate) {
  if (!startDate || !endDate || startDate > endDate) return 0;
  return eachDayOfInterval({ start: parseISO(startDate), end: parseISO(endDate) }).filter(day => !isWeekend(day)).length;
}

export default function VacationRequests() {
  const { user } = useAuth();
  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [editingRequest, setEditingRequest] = useState(null);
  const [formData, setFormData] = useState(emptyForm);
  const [view, setView] = useState('requests');
  const [recentRequestId, setRecentRequestId] = useState(null);

  useEffect(() => {
    loadRequests();
  }, []);

  const requestedDays = useMemo(() => {
    return workingDaysBetween(formData.startDate, formData.endDate);
  }, [formData.endDate, formData.startDate]);

  const loadRequests = async () => {
    try {
      const response = await vacationAPI.getMyRequests();
      setRequests((response.data || []).sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)));
      setError('');
    } catch (err) {
      setError('Failed to load vacation requests');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const checkBalanceBeforeSubmit = async (request) => {
    const balanceKey = paidBalanceKeyByType[request.vacationType];
    if (!balanceKey) return '';

    const latestRequests = (await vacationAPI.getMyRequests()).data || [];
    const warnings = [];
    const firstYear = Number(request.startDate.slice(0, 4));
    const lastYear = Number(request.endDate.slice(0, 4));
    for (let year = firstYear; year <= lastYear; year++) {
      const start = `${year}-01-01`;
      const end = `${year}-12-31`;
      const daysInYear = (item) => workingDaysBetween(
        item.startDate < start ? start : item.startDate,
        item.endDate > end ? end : item.endDate
      );
      const requestedHours = daysInYear(request) * 8;
      if (!requestedHours) continue;
      const balance = (await userAPI.getMyLeaveBalance(year)).data;
      if (!balance?.configured) return `Leave allowance is not set for ${year}. Ask an Admin to assign it before submitting this request.`;
      const bucket = balance?.[balanceKey];
      if (!bucket) throw new Error('Leave balance is unavailable');
      // Approved leave is already included in the server's remaining balance.
      const pendingHours = latestRequests
        .filter((item) => item.id !== request.id && paidBalanceKeyByType[item.vacationType] === balanceKey && item.status === 'SUBMITTED')
        .reduce((sum, item) => sum + daysInYear(item) * 8, 0);
      const remainingHours = Math.max(0, Number(bucket.remainingDays) * 8 - pendingHours);
      const unpaidHours = Math.max(0, requestedHours - remainingHours);
      if (unpaidHours > 0) {
        warnings.push(`${year}: ${unpaidHours / 8} days exceed the available ${balanceKey} balance after pending requests. Choose Unpaid Leave or ask an Admin to update the allowance.`);
      }
    }
    return warnings.join(' ');
  };

  const startNewRequest = () => {
    setEditingRequest(null);
    setFormData(emptyForm);
    setError('');
    setView('editor');
  };

  const startEditing = (request) => {
    setEditingRequest(request);
    setFormData({
      startDate: request.startDate || '',
      endDate: request.endDate || '',
      vacationType: request.vacationType || 'VACATION',
      specialReason: request.specialReason || '',
      notes: request.notes || '',
    });
    setError('');
    setView('editor');
  };

  const handleSave = async (event) => {
    event.preventDefault();

    if (!formData.startDate || !formData.endDate) {
      setError('Start and end dates are required');
      return;
    }

    setSaving(true);
    try {
      let saved;
      if (editingRequest) {
        saved = await vacationAPI.updateRequest(
          editingRequest.id,
          formData.startDate,
          formData.endDate,
          formData.vacationType,
          formData.notes,
          formData.specialReason
        );
      } else {
        saved = await vacationAPI.createRequest(
          formData.startDate,
          formData.endDate,
          formData.vacationType,
          formData.notes,
          formData.specialReason
        );
      }

      await loadRequests();
      setRecentRequestId(saved?.data?.id || editingRequest?.id || null);
      setEditingRequest(null);
      setFormData(emptyForm);
      setView('requests');
    } catch (err) {
      setError(err.userMessage || err.response?.data?.message || (editingRequest ? 'Failed to update vacation request' : 'Failed to create vacation request'));
      console.error(err);
    } finally {
      setSaving(false);
    }
  };

  const handleSubmitForApproval = async (requestId) => {
    try {
      const request = requests.find((item) => item.id === requestId);
      const warning = request ? await checkBalanceBeforeSubmit(request) : '';
      if (warning) { setError(warning); return; }
      await vacationAPI.submitRequest(requestId);
      await loadRequests();
      setError('');
    } catch (err) {
      setError(err.userMessage || err.response?.data?.message || err.message || 'Failed to submit request');
    }
  };

  const handleDeleteRequest = async (request) => {
    const confirmed = window.confirm('Delete this vacation request? This cannot be undone.');
    if (!confirmed) return;

    try {
      await vacationAPI.deleteRequest(request.id);
      if (editingRequest?.id === request.id) {
        startNewRequest();
      }
      await loadRequests();
      setError('');
    } catch (err) {
      setError('Failed to delete vacation request');
      console.error(err);
    }
  };

  if (loading) {
    return <div className="page-container"><div className="loading-panel"><LoadingIndicator label="Loading vacation requests..." /></div></div>;
  }

  const approvedDays = requests
    .filter((request) => request.status === 'APPROVED' || request.status === 'LOCKED')
    .reduce((sum, request) => sum + (Number(request.hours || 0) / 8), 0);

  return (
    <div className="page-container vacation-page">
      <header className="vacation-page-hero"><div><span className="vacation-kicker">TIME AWAY</span><h1>Time off</h1><p>Plan leave, track requests, and keep your balance in view.</p></div><div className="vacation-hero-mark" aria-hidden="true"><Icon name="calendar" size={30}/></div></header>

      <RecordSummary items={[{ label: 'Total requests', value: requests.length, icon: 'calendar' }, { label: 'Awaiting approval', value: requests.filter(r => r.status === 'SUBMITTED').length, icon: 'clock' }, { label: 'Approved leave days', value: approvedDays.toFixed(1), icon: 'check' }]} />
      {error && <div className="error-message" role="alert">{error}</div>}

      <nav className="vacation-view-switch" aria-label="Time off views">
        <button type="button" className={view === 'requests' ? 'is-active' : ''} aria-current={view === 'requests' ? 'page' : undefined} onClick={() => setView('requests')}>My requests <span>{requests.length}</span></button>
        <button type="button" className={view === 'editor' ? 'is-active' : ''} aria-current={view === 'editor' ? 'page' : undefined} onClick={startNewRequest}>{editingRequest ? 'Edit request' : 'New request'}</button>
        <button type="button" className={view === 'balances' ? 'is-active' : ''} aria-current={view === 'balances' ? 'page' : undefined} onClick={() => setView('balances')}>Leave balances</button>
      </nav>

      <div className="vacation-layout" hidden={view !== 'editor'}>
        <form className="card vacation-editor" onSubmit={handleSave}>
          <div className="vacation-form-intro"><span className="vacation-kicker">{editingRequest ? 'UPDATE DRAFT' : 'NEW REQUEST'}</span><p>Select your dates and leave type, then save a draft to submit for approval.</p></div>
          <div className="panel-heading">
            <div>
              <h2>{editingRequest ? 'Edit draft' : 'New time-off request'}</h2>
              <p>{requestedDays} working day{requestedDays === 1 ? '' : 's'} selected ({requestedDays * 8} hours)</p>
            </div>
            {editingRequest && <span className={`status-badge status-${editingRequest.status.toLowerCase()}`}>{editingRequest.status}</span>}
          </div>

          <div className="form-row">
            <div className="form-group">
              <label htmlFor="vacationrequests-field-1">Start Date</label>
              <input id="vacationrequests-field-1"
                type="date"
                value={formData.startDate}
                onChange={(e) => setFormData({ ...formData, startDate: e.target.value })}
                required
              />
            </div>
            <div className="form-group">
              <label htmlFor="vacationrequests-field-2">End Date</label>
              <input id="vacationrequests-field-2"
                type="date"
                value={formData.endDate}
                onChange={(e) => setFormData({ ...formData, endDate: e.target.value })}
                required
              />
            </div>
          </div>

          <div className="form-row">
            <div className="form-group">
              <label htmlFor="vacationrequests-field-3">Type</label>
              <select id="vacationrequests-field-3"
                value={formData.vacationType}
                onChange={(e) => setFormData({ ...formData, vacationType: e.target.value })}
              >
                {editingRequest && !leaveTypes.some(([value]) => value === editingRequest.vacationType) && <option value={editingRequest.vacationType}>Legacy: {leaveLabel(editingRequest.vacationType)}</option>}
                {leaveTypes.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
            </div>
            <div className="form-group">
              <label htmlFor="vacationrequests-field-4">Status</label>
              <input id="vacationrequests-field-4" value={editingRequest ? editingRequest.status : 'DRAFT'} disabled />
            </div>
          </div>

          {formData.vacationType === 'SPECIAL' && <div className="form-group">
            <label htmlFor="vacation-special-reason">Special leave reason</label>
            <select id="vacation-special-reason" required value={formData.specialReason} onChange={e => setFormData({ ...formData, specialReason: e.target.value })}>
              <option value="">Choose a reason</option>{specialReasons.map(reason => <option key={reason} value={reason}>{reason}</option>)}
            </select>
            <p className="form-hint">An Admin will decide whether this uses a balance and how it is classified.</p>
          </div>}

          <div className="form-group">
            <label htmlFor="vacationrequests-field-5">Notes</label>
            <textarea id="vacationrequests-field-5"
              value={formData.notes}
              onChange={(e) => setFormData({ ...formData, notes: e.target.value })}
              placeholder="Add schedule details or context for approvers"
              rows="4"
              required={formData.vacationType === 'SPECIAL' && formData.specialReason === 'Other'}
            />
          </div>

          <LeaveBalancePreview userId={user?.id} form={formData} requests={requests} editingId={editingRequest?.id} />

          {editingRequest?.rejectionReason && (
            <div className="inline-alert">
              <strong>Rejection Reason:</strong> {editingRequest.rejectionReason}
            </div>
          )}

          <div className="action-bar compact-actions">
            <button type="submit" className="button button-primary" disabled={saving}>
              {saving ? <LoadingIndicator label="Saving..." /> : 'Save draft'}
            </button>
            <button type="button" className="button button-secondary" onClick={() => { setEditingRequest(null); setFormData(emptyForm); setError(''); setView('requests'); }}>
              Cancel
            </button>
          </div>
        </form>

        <div className="vacation-side">
          <div className="vacation-guide"><span className="vacation-kicker">HOW IT WORKS</span><h2>From draft to approval</h2><div><span>01</span><p>Choose dates and a leave type.</p></div><div><span>02</span><p>Save your request as a draft.</p></div><div><span>03</span><p>Submit the draft from My requests.</p></div><button type="button" onClick={() => setView('balances')}>Review leave balances <Icon name="arrow" size={16}/></button></div>
        </div>
      </div>

      <section className="vacation-balances-view" hidden={view !== 'balances'} aria-label="Leave balances">{user?.id && <div className="card"><LeaveBalancePanel userId={user.id} ownBalance /></div>}</section>

      <section className="vacation-requests-section" hidden={view !== 'requests'} aria-label="My requests">
      <div className="vacation-list-heading"><div><span className="vacation-kicker">YOUR REQUESTS</span><h2>My requests</h2><p>Most recently created first</p></div><button type="button" className="button button-primary" onClick={startNewRequest}>Create request <Icon name="arrow" size={16}/></button></div>
      {requests.length === 0 ? (
        <div className="empty-state">
          <p>No vacation requests yet. Create one to get started.</p>
        </div>
      ) : (
        <div className="request-list">
          {requests.map((request) => {
            const canEdit = request.status === 'DRAFT' || request.status === 'REJECTED';
            return (
              <article className={`request-card${request.id === recentRequestId ? ' is-recent' : ''}`} key={request.id}>
                <div>
                  <div className="request-card-topline">
                    <h3>{leaveLabel(request.vacationType)}{request.specialReason ? ` · ${request.specialReason}` : ''}</h3>
                    <span className={`status-badge status-${request.status.toLowerCase()}`}>{request.status}</span>
                  </div>
                  <p className="request-dates">{formatDate(request.startDate)} to {formatDate(request.endDate)}</p>
                  <div className="request-meta">
                    <span>{Number(request.hours || 0) / 8} working days</span>
                    {request.accountingType && <span>{request.accountingType.replaceAll('_', ' ').toLowerCase()}</span>}
                    {request.submittedAt && <span>Submitted {formatDate(request.submittedAt)}</span>}
                    {request.approvedAt && <span>Approved {formatDate(request.approvedAt)}</span>}
                  </div>
                  {request.notes && <p className="request-notes">{request.notes}</p>}
                  {request.rejectionReason && <p className="request-rejection">{request.rejectionReason}</p>}
                </div>
                <div className="request-actions">
                  {canEdit && (
                    <button className="button button-small button-secondary" onClick={() => startEditing(request)} type="button">
                      Edit
                    </button>
                  )}
                  {request.status === 'DRAFT' && (
                    <button className="button button-small button-primary" onClick={() => handleSubmitForApproval(request.id)} type="button">
                      Submit
                    </button>
                  )}
                  {canEdit && (
                    <button className="button button-small button-danger" onClick={() => handleDeleteRequest(request)} type="button">
                      Delete
                    </button>
                  )}
                </div>
              </article>
            );
          })}
        </div>
      )}
      </section>
    </div>
  );
}
