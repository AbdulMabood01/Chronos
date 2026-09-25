import ScreenTitle, { RecordSummary } from '../components/ScreenTitle';
import { formatDate } from '../utils/dates';
import React, { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { letterRequestAPI } from '../api';
import { LoadingIndicator } from '../components/Hourglass';
import logoUrl from '../assets/Logo.png';
import managerSignatureUrl from '../assets/manager-signature.png';
import '../styles.css';

const REQUEST_TYPES = {
  EMPLOYMENT_VERIFICATION: {
    label: 'Employment Verification Letter',
    description: 'Confirm current employment details for a lender, landlord, school, or agency.',
  },
  TRAVEL: {
    label: 'Travel Letter',
    description: 'Confirm employment details and company travel clearance for selected dates.',
  },
  VACATION: {
    label: 'Vacation Letter',
    description: 'Request a formal letter confirming vacation dates and purpose.',
  },
};

const EMPLOYER_INFORMATION = `Employer Information
E-Verification Number: 2601516
EIN: 933751606
Employer Telephone Number: (872) 999-2576
Supervisor: Syed Hussain
Email: hr@maxwellnetwork.org`;

const COMPANY_FOOTER = `tech.maxwellnetwork.org
5875 N Lincoln Ave, Suite LL26, Chicago, IL 60659-2122`;

const emptyForm = {
  requestType: 'EMPLOYMENT_VERIFICATION',
  requestedFullName: '',
  requestedJobTitle: '',
  employmentStartDate: '',
  destinationCountry: '',
  travelStartDate: '',
  travelEndDate: '',
  vacationStartDate: '',
  vacationEndDate: '',
  notes: '',
};


function downloadBlob(response, fallbackName) {
  const blob = new Blob([response.data], { type: 'application/pdf' });
  const disposition = response.headers?.['content-disposition'] || '';
  const match = disposition.match(/filename="?([^"]+)"?/i);
  const fileName = match?.[1] || fallbackName;
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export default function EmployeeRequests() {
  const { user } = useAuth();
  const [searchParams] = useSearchParams();
  const [requests, setRequests] = useState([]);
  const [formData, setFormData] = useState(emptyForm);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    loadRequests();
  }, []);

  useEffect(() => {
    setFormData((current) => ({
      ...current,
      requestedFullName: current.requestedFullName || `${user?.firstName || ''} ${user?.lastName || ''}`.trim(),
      requestedJobTitle: current.requestedJobTitle || user?.jobTitle || '',
    }));
  }, [user]);

  useEffect(() => {
    const requestedType = searchParams.get('type');
    if (REQUEST_TYPES[requestedType]) {
      setFormData((current) => ({
        ...emptyForm,
        requestType: requestedType,
        requestedFullName: current.requestedFullName || `${user?.firstName || ''} ${user?.lastName || ''}`.trim(),
        requestedJobTitle: current.requestedJobTitle || user?.jobTitle || '',
        employmentStartDate: current.employmentStartDate,
      }));
    }
  }, [searchParams, user]);

  const preview = useMemo(() => {
    const type = REQUEST_TYPES[formData.requestType].label;
    const fullName = formData.requestedFullName || '[full name]';
    const title = formData.requestedJobTitle || '[title]';
    const startDate = formData.employmentStartDate || '[job start date]';
    let letterBody;

    if (formData.requestType === 'TRAVEL') {
      letterBody = `At the request of ${fullName}, this letter confirms their current employment with Maxwell Network Inc as ${title}. Their employment began on ${startDate}.\n\nAccording to the information provided with their request, ${fullName} plans to travel${formData.destinationCountry ? ` to ${formData.destinationCountry}` : ''} from ${formData.travelStartDate || '[travel start date]'} through ${formData.travelEndDate || '[travel end date]'}.\n\n${fullName} remains an active employee and is expected to resume their work responsibilities after the stated travel period. This letter confirms employment and the travel dates supplied to us; it does not replace any required travel authorization.`;
    } else if (formData.requestType === 'VACATION') {
      letterBody = `At the request of ${fullName}, this letter confirms their current employment with Maxwell Network Inc as ${title}. Their employment began on ${startDate}.\n\n${fullName} has provided vacation dates of ${formData.vacationStartDate || '[vacation start date]'} through ${formData.vacationEndDate || '[vacation end date]'}. These dates are recorded with the company for administrative review and remain subject to the applicable leave approval process.\n\n${fullName} is expected to resume their regular work responsibilities following any approved leave. Please contact our Human Resources department if further employment verification is needed.`;
    } else {
      letterBody = `At the request of ${fullName}, this letter confirms their current employment with Maxwell Network Inc.\n\n${fullName} has been employed since ${startDate} and currently holds the position of ${title}. Our records show that their employment is active as of the date of this letter.\n\nThis verification reflects our employment records and is provided for the recipient's use. For further confirmation, please contact our Human Resources department using the details on this letter.`;
    }

    return `Maxwell Network Inc\n\nTo whom it may concern,\n\nSubject: ${type}\n\n${letterBody}\n\nBest Regards,\nSyed Hussain\nManager\n\n${EMPLOYER_INFORMATION}\n\n${COMPANY_FOOTER}`;
  }, [formData]);

  const loadRequests = async () => {
    try {
      const response = await letterRequestAPI.getMyRequests();
      setRequests(response.data || []);
      setError('');
    } catch (err) {
      setError('Failed to load letter requests');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const updateField = (field, value) => {
    setFormData((current) => ({ ...current, [field]: value }));
  };

  const handleTypeChange = (requestType) => {
    setFormData((current) => ({
      ...emptyForm,
      requestType,
      requestedFullName: current.requestedFullName,
      requestedJobTitle: current.requestedJobTitle,
      employmentStartDate: current.employmentStartDate,
    }));
    setError('');
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!formData.requestedFullName || !formData.requestedJobTitle || !formData.employmentStartDate) {
      setError('Full name, title, and job start date are required');
      return;
    }
    if (formData.requestType === 'TRAVEL' && (!formData.travelStartDate || !formData.travelEndDate)) {
      setError('Travel start and end dates are required');
      return;
    }
    if (formData.requestType === 'VACATION' && (!formData.vacationStartDate || !formData.vacationEndDate)) {
      setError('Vacation start and end dates are required');
      return;
    }

    setSaving(true);
    try {
      await letterRequestAPI.createRequest({
        ...formData,
        employmentStartDate: formData.employmentStartDate || null,
        destinationCountry: formData.destinationCountry || null,
        travelStartDate: formData.travelStartDate || null,
        travelEndDate: formData.travelEndDate || null,
        vacationStartDate: formData.vacationStartDate || null,
        vacationEndDate: formData.vacationEndDate || null,
      });
      setFormData({
        ...emptyForm,
        requestedFullName: formData.requestedFullName,
        requestedJobTitle: formData.requestedJobTitle,
        employmentStartDate: formData.employmentStartDate,
      });
      await loadRequests();
      setError('');
    } catch (err) {
      setError('Failed to submit letter request');
      console.error(err);
    } finally {
      setSaving(false);
    }
  };

  const handleDownload = async (request) => {
    try {
      const response = await letterRequestAPI.downloadPdf(request.id);
      downloadBlob(response, `${REQUEST_TYPES[request.requestType].label}.pdf`);
      setError('');
    } catch (err) {
      setError('Failed to download letter PDF');
      console.error(err);
    }
  };

  if (loading) {
    return <div className="page-container"><div className="loading-panel"><LoadingIndicator label="Loading requests..." /></div></div>;
  }

  return (
    <div className="page-container requests-page">
      <div className="header-bar">
        <div>
          <ScreenTitle title="Requests" icon="file" eyebrow="EMPLOYEE SERVICES" />
          <p className="page-subtitle">Submit employee letters for admin approval and download approved PDFs.</p>
        </div>
      </div>

      <RecordSummary items={[{ label: 'Letter requests', value: requests.length, icon: 'file' }, { label: 'In review', value: requests.filter(r => r.status === 'SUBMITTED').length, icon: 'clock' }, { label: 'Ready to download', value: requests.filter(r => r.status === 'APPROVED').length, icon: 'check' }]} />
      {error && <div className="error-message" role="alert">{error}</div>}

      <div className="requests-layout">
        <form className="card request-editor" onSubmit={handleSubmit}>
          <div className="panel-heading">
            <div>
              <h2>{REQUEST_TYPES[formData.requestType].label}</h2>
              <p>{REQUEST_TYPES[formData.requestType].description}</p>
            </div>
          </div>

          <div className="request-type-tabs" role="group" aria-label="Letter request type">
            {Object.entries(REQUEST_TYPES).map(([value, config]) => (
              <button
                aria-pressed={formData.requestType === value}
                className={`request-type-tab ${formData.requestType === value ? 'active' : ''}`}
                key={value}
                onClick={() => handleTypeChange(value)}
                type="button"
              >
                {config.label}
              </button>
            ))}
          </div>

          <div className="form-row">
            <div className="form-group">
              <label htmlFor="employeerequests-field-1">Full Name</label>
              <input id="employeerequests-field-1"
                value={formData.requestedFullName}
                onChange={(e) => updateField('requestedFullName', e.target.value)}
                placeholder="Full legal name"
                required
              />
            </div>
            <div className="form-group">
              <label htmlFor="employeerequests-field-2">Title</label>
              <input id="employeerequests-field-2"
                value={formData.requestedJobTitle}
                onChange={(e) => updateField('requestedJobTitle', e.target.value)}
                placeholder="Job title"
                required
              />
            </div>
          </div>

          <div className="form-row">
            <div className="form-group">
              <label htmlFor="employeerequests-field-3">Job Start Date</label>
              <input id="employeerequests-field-3"
                type="date"
                value={formData.employmentStartDate}
                onChange={(e) => updateField('employmentStartDate', e.target.value)}
                required
              />
            </div>
          </div>

          {formData.requestType === 'TRAVEL' && (
            <div className="form-row">
              <div className="form-group">
                <label htmlFor="employeerequests-field-4">Travel Destination</label>
                <input id="employeerequests-field-4"
                  value={formData.destinationCountry}
                  onChange={(e) => updateField('destinationCountry', e.target.value)}
                  placeholder="Optional city or country"
                />
              </div>
            </div>
          )}

          {formData.requestType === 'TRAVEL' && (
            <div className="form-row">
              <div className="form-group">
                <label htmlFor="employeerequests-field-5">Travel Start Date</label>
                <input id="employeerequests-field-5"
                  type="date"
                  value={formData.travelStartDate}
                  onChange={(e) => updateField('travelStartDate', e.target.value)}
                  required
                />
              </div>
              <div className="form-group">
                <label htmlFor="employeerequests-field-6">Travel End Date</label>
                <input id="employeerequests-field-6"
                  type="date"
                  value={formData.travelEndDate}
                  onChange={(e) => updateField('travelEndDate', e.target.value)}
                  required
                />
              </div>
            </div>
          )}

          {formData.requestType === 'VACATION' && (
            <div className="form-row">
              <div className="form-group">
                <label htmlFor="employeerequests-field-7">Vacation Start Date</label>
                <input id="employeerequests-field-7"
                  type="date"
                  value={formData.vacationStartDate}
                  onChange={(e) => updateField('vacationStartDate', e.target.value)}
                  required
                />
              </div>
              <div className="form-group">
                <label htmlFor="employeerequests-field-8">Vacation End Date</label>
                <input id="employeerequests-field-8"
                  type="date"
                  value={formData.vacationEndDate}
                  onChange={(e) => updateField('vacationEndDate', e.target.value)}
                  required
                />
              </div>
            </div>
          )}

          <div className="form-group">
            <label htmlFor="employeerequests-field-9">Additional Information</label>
            <textarea id="employeerequests-field-9"
              value={formData.notes}
              onChange={(e) => updateField('notes', e.target.value)}
              placeholder="Any details admin should include or verify"
              rows="4"
            />
          </div>

          <div className="letter-preview">
            <span>Sample Letter Content</span>
            <div className="letter-preview-paper">
              <header className="letter-preview-header">
                <div className="letter-paper-brand">
                  <img src={logoUrl} alt="Maxwell Network Inc" />
                  <div>
                    <strong>Maxwell Network Inc</strong>
                  </div>
                </div>
              </header>
              {preview.split(EMPLOYER_INFORMATION)[0].split('\n').slice(1).map((line, index) => (
                line
                  ? (
                      <React.Fragment key={`${line}-${index}`}>
                        {line === 'Best Regards,' && <img className="letter-manager-signature" src={managerSignatureUrl} alt="Manager signature" />}
                        <p className={line.toLowerCase().startsWith('to whom') ? 'letter-salutation' : ''}>{line}</p>
                      </React.Fragment>
                    )
                  : <br key={`break-${index}`} />
              ))}
              <section className="letter-employer-info">
                <strong>{EMPLOYER_INFORMATION.split('\n')[0]}</strong>
                {EMPLOYER_INFORMATION.split('\n').slice(1).map((line) => (
                  <span key={line}>
                    <b>{line.split(':')[0]}:</b>
                    <span>{line.split(':').slice(1).join(':').trim()}</span>
                  </span>
                ))}
              </section>
              <footer className="letter-preview-footer">
                {COMPANY_FOOTER.split('\n').map((line) => (
                  <span key={line}>{line}</span>
                ))}
              </footer>
            </div>
          </div>

          <div className="action-bar compact-actions">
            <button className="button button-primary" type="submit" disabled={saving}>
              {saving ? <LoadingIndicator label="Submitting..." /> : 'Submit for Approval'}
            </button>
          </div>
        </form>

        <aside className="requests-side">
          <div className="summary-tile">
            <span>Total Requests</span>
            <strong>{requests.length}</strong>
          </div>
          <div className="summary-tile gross-pay-tile">
            <span>Ready to Download</span>
            <strong>{requests.filter((request) => request.status === 'APPROVED').length}</strong>
          </div>
        </aside>
      </div>

      {requests.length === 0 ? (
        <div className="empty-state">
          <p>No letter requests yet.</p>
        </div>
      ) : (
        <div className="request-list">
          {requests.map((request) => (
            <article className="request-card" key={request.id}>
              <div>
                <div className="request-card-topline">
                  <h3>{REQUEST_TYPES[request.requestType]?.label || request.requestType}</h3>
                  <span className={`status-badge status-${request.status.toLowerCase()}`}>{request.status.replace('_', ' ')}</span>
                </div>
                <p className="request-dates">{request.requestedFullName || request.userName}</p>
                <div className="request-meta">
                  {request.requestedJobTitle && <span>{request.requestedJobTitle}</span>}
                  {request.employmentStartDate && <span>Started {formatDate(request.employmentStartDate)}</span>}
                  <span>Submitted {formatDate(request.submittedAt)}</span>
                  {request.approvedAt && <span>Approved {formatDate(request.approvedAt)}</span>}
                </div>
                {request.rejectionReason && <p className="request-rejection">{request.rejectionReason}</p>}
              </div>
              <div className="request-actions">
                {request.status === 'APPROVED' && (
                  <button className="button button-small button-primary" onClick={() => handleDownload(request)} type="button">
                    Download PDF
                  </button>
                )}
              </div>
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
