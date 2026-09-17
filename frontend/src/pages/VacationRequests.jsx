import LeaveBalancePreview from '../components/LeaveBalancePreview';
import ScreenTitle, { RecordSummary } from '../components/ScreenTitle';
import { formatDate } from '../utils/dates';
import React, { useEffect, useMemo, useState } from 'react';
import { userAPI, vacationAPI } from '../api';
import { eachDayOfInterval, isWeekend, parseISO } from 'date-fns';
import { LoadingIndicator } from '../components/Hourglass';
import '../styles.css';
import { useAuth } from '../AuthContext';
import LeaveBalancePanel from '../components/LeaveBalancePanel';

const leaveTypes = [
  ['VACATION', 'Vacation'],
  ['SICK', 'Sick Leave'],
  ['PERSONAL', 'Personal Day'],
  ['MATERNITY', 'Maternity Leave'],
  ['PATERNITY', 'Paternity Leave'],
  ['PARENTAL', 'Parental Leave'],
  ['BEREAVEMENT', 'Bereavement Leave'],
  ['ADOPTION', 'Adoption Leave'],
  ['JURY_DUTY', 'Jury Duty'],
  ['MILITARY', 'Military Leave'],
  ['FAMILY_CARE', 'Family Care Leave'],
  ['RELIGIOUS', 'Religious Leave'],
  ['UNPAID_LEAVE', 'Unpaid Leave'],
  ['OTHER', 'Other'],
];

const paidBalanceKeyByType = Object.fromEntries(leaveTypes.filter(([type]) => type !== 'UNPAID_LEAVE').map(([type]) => [type, type === 'SICK' ? 'sick' : type === 'BEREAVEMENT' ? 'bereavement' : 'vacation']));

const emptyForm = {
  startDate: '',
  endDate: '',
  vacationType: 'VACATION',
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

  const buildUnpaidLeaveWarning = async (request) => {
    const typeLabel = leaveTypes.find(([value]) => value === request.vacationType)?.[1] || request.vacationType.replaceAll('_', ' ');
    if (request.vacationType === 'UNPAID_LEAVE') {
      return `All ${Number(request.hours)} requested hours will be unpaid leave. Continue?`;
    }
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
      const balance = (await userAPI.getLeaveBalance(user.id, year)).data;
      const bucket = balance?.[balanceKey];
      if (!bucket) throw new Error('Leave balance is unavailable');
      // Approved leave is already included in the server's remaining balance.
      const pendingHours = latestRequests
        .filter((item) => item.id !== request.id && paidBalanceKeyByType[item.vacationType] === balanceKey && item.status === 'SUBMITTED')
        .reduce((sum, item) => sum + daysInYear(item) * 8, 0);
      const remainingHours = Math.max(0, Number(bucket.remainingDays) * 8 - pendingHours);
      const unpaidHours = Math.max(0, requestedHours - remainingHours);
      if (unpaidHours > 0) {
        warnings.push(`${year}: ${requestedHours} hours requested, ${remainingHours} paid hours remaining after pending requests. ${unpaidHours} hours (${unpaidHours / 8} days) of this request will be unpaid leave.`);
      }
    }
    return warnings.length ? `${typeLabel}\n${warnings.join('\n')}\nContinue?` : '';
  };

  const startNewRequest = () => {
    setEditingRequest(null);
    setFormData(emptyForm);
    setError('');
  };

  const startEditing = (request) => {
    setEditingRequest(request);
    setFormData({
      startDate: request.startDate || '',
      endDate: request.endDate || '',
      vacationType: request.vacationType || 'VACATION',
      notes: request.notes || '',
    });
    setError('');
  };

  const handleSave = async (event) => {
    event.preventDefault();

    if (!formData.startDate || !formData.endDate) {
      setError('Start and end dates are required');
      return;
    }

    setSaving(true);
    try {
      if (editingRequest) {
        await vacationAPI.updateRequest(
          editingRequest.id,
          formData.startDate,
          formData.endDate,
          formData.vacationType,
          formData.notes
        );
      } else {
        await vacationAPI.createRequest(
          formData.startDate,
          formData.endDate,
          formData.vacationType,
          formData.notes
        );
      }

      startNewRequest();
      await loadRequests();
    } catch (err) {
      setError(editingRequest ? 'Failed to update vacation request' : 'Failed to create vacation request');
      console.error(err);
    } finally {
      setSaving(false);
    }
  };

  const handleSubmitForApproval = async (requestId) => {
    try {
      const request = requests.find((item) => item.id === requestId);
      const warning = request ? await buildUnpaidLeaveWarning(request) : '';
      if (warning && !window.confirm(warning)) return;
      await vacationAPI.submitRequest(requestId);
      await loadRequests();
      setError('');
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to submit request');
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

  const activeRequests = requests.filter((request) => ['DRAFT', 'REJECTED'].includes(request.status));
  const approvedDays = requests
    .filter((request) => request.status === 'APPROVED' || request.status === 'LOCKED')
    .reduce((sum, request) => sum + (Number(request.hours || 0) / 8), 0);

  return (
    <div className="page-container vacation-page">
      <div className="header-bar">
        <div>
          <ScreenTitle title="Vacation Requests" icon="calendar" eyebrow="TIME TO RECHARGE" />
          <p className="page-subtitle">Create, edit, and resubmit time-off requests.</p>
        </div>
        <button className="button button-secondary" onClick={startNewRequest} type="button">
          New Request
        </button>
      </div>

      <RecordSummary items={[{ label: 'Total requests', value: requests.length, icon: 'calendar' }, { label: 'Awaiting approval', value: requests.filter(r => r.status === 'SUBMITTED').length, icon: 'clock' }, { label: 'Approved days', value: approvedDays.toFixed(1), icon: 'check' }]} />
      {user?.id && <div className="card"><LeaveBalancePanel userId={user.id} /></div>}
      {error && <div className="error-message" role="alert">{error}</div>}

      <div className="vacation-layout">
        <form className="card vacation-editor" onSubmit={handleSave}>
          <div className="panel-heading">
            <div>
              <h2>{editingRequest ? 'Edit Vacation Request' : 'Create Vacation Request'}</h2>
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
                {leaveTypes.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
            </div>
            <div className="form-group">
              <label htmlFor="vacationrequests-field-4">Status</label>
              <input id="vacationrequests-field-4" value={editingRequest ? editingRequest.status : 'DRAFT'} disabled />
            </div>
          </div>

          <div className="form-group">
            <label htmlFor="vacationrequests-field-5">Notes</label>
            <textarea id="vacationrequests-field-5"
              value={formData.notes}
              onChange={(e) => setFormData({ ...formData, notes: e.target.value })}
              placeholder="Add schedule details or context for approvers"
              rows="4"
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
              {saving ? <LoadingIndicator label="Saving..." /> : editingRequest ? 'Save Changes' : 'Create Request'}
            </button>
            <button type="button" className="button button-secondary" onClick={startNewRequest}>
              Cancel
            </button>
          </div>
        </form>

        <div className="vacation-side">
          <div className="summary-tile">
            <span>Requests to Finish</span>
            <strong>{activeRequests.length}</strong>
          </div>
          <div className="summary-tile gross-pay-tile">
            <span>Approved Vacation Days</span>
            <strong>{approvedDays.toFixed(1)}</strong>
          </div>
        </div>
      </div>

      {requests.length === 0 ? (
        <div className="empty-state">
          <p>No vacation requests yet.</p>
        </div>
      ) : (
        <div className="request-list">
          {requests.map((request) => {
            const canEdit = request.status === 'DRAFT' || request.status === 'REJECTED';
            return (
              <article className="request-card" key={request.id}>
                <div>
                  <div className="request-card-topline">
                    <h3>{request.vacationType.replace('_', ' ')}</h3>
                    <span className={`status-badge status-${request.status.toLowerCase()}`}>{request.status}</span>
                  </div>
                  <p className="request-dates">{formatDate(request.startDate)} to {formatDate(request.endDate)}</p>
                  <div className="request-meta">
                    <span>{Number(request.hours || 0) / 8} vacation days</span>
                    <span>Submitted {request.submittedAt ? formatDate(request.submittedAt) : '-'}</span>
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
    </div>
  );
}
