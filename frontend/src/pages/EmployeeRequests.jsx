import ScreenTitle, { RecordSummary } from '../components/ScreenTitle';
import { formatDate } from '../utils/dates';
import React, { useEffect, useMemo, useState, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { companyLetterRequestAPI,companyAPI } from '../api';
import { useCompany } from '../CompanyContext';
import { LoadingIndicator } from '../components/Hourglass';
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
  const {currentCompany}=useCompany();const companyId=currentCompany?.id;const letterRequestAPI=companyLetterRequestAPI(companyId);const companyName=currentCompany?.name||'Company';
  const scope=useRef(companyId);scope.current=companyId;
  const [availability,setAvailability]=useState(null);
  useEffect(()=>{let active=true;setAvailability(null);companyLetterRequestAPI(companyId).availability().then(r=>{if(active)setAvailability(r.data);}).catch(()=>{if(active)setAvailability({companyId,templates:[],failed:true});});return()=>{active=false;};},[companyId]);
  const availableTemplates=String(availability?.companyId)===String(companyId)?availability.templates||[]:[];
  const [employment,setEmployment]=useState(null);
  useEffect(()=>{let active=true;setEmployment(null);setFormData({...emptyForm});companyAPI.employment(companyId,'me').then(r=>{if(active)setEmployment(r.data);}).catch(()=>{});return()=>{active=false;};},[companyId]);
  const [searchParams] = useSearchParams();
  const [requests, setRequests] = useState([]);
  const [formData, setFormData] = useState(emptyForm);
  const selectedTemplate=availableTemplates.find(t=>t.type===formData.requestType);
  useEffect(()=>{if(availableTemplates.length&&!availableTemplates.some(t=>t.type===formData.requestType))setFormData(f=>({...f,requestType:availableTemplates[0].type}));},[availability]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    setRequests([]);setLoading(true);
    loadRequests();
  }, [companyId]);

  useEffect(() => {
    setFormData((current) => ({
      ...current,
      requestedFullName: current.requestedFullName || `${user?.firstName || ''} ${user?.lastName || ''}`.trim(),
      requestedJobTitle: current.requestedJobTitle || employment?.jobTitle || '',
      employmentStartDate:current.employmentStartDate||employment?.joiningDate||'',
    }));
  }, [user,employment]);

  useEffect(() => {
    const requestedType = searchParams.get('type');
    if (REQUEST_TYPES[requestedType]) {
      setFormData((current) => ({
        ...emptyForm,
        requestType: requestedType,
        requestedFullName: current.requestedFullName || `${user?.firstName || ''} ${user?.lastName || ''}`.trim(),
        requestedJobTitle: current.requestedJobTitle || employment?.jobTitle || '',
        employmentStartDate: current.employmentStartDate,
      }));
    }
  }, [searchParams,user,employment]);

  const preview = useMemo(() => {
    const d=selectedTemplate?.definition;if(!d)return 'Your company is setting up this letter type.';
    const values={employee_name:formData.requestedFullName||'[full name]',job_title:formData.requestedJobTitle||'[job title]',joining_date:formData.employmentStartDate?formatDate(formData.employmentStartDate):'[joining date]',issue_date:new Date().toLocaleDateString('en-CA'),employer_name:d.name,destination_country:formData.destinationCountry||'[destination]',travel_start_date:formData.travelStartDate?formatDate(formData.travelStartDate):'[travel start]',travel_end_date:formData.travelEndDate?formatDate(formData.travelEndDate):'[travel end]',vacation_start_date:formData.vacationStartDate?formatDate(formData.vacationStartDate):'[vacation start]',vacation_end_date:formData.vacationEndDate?formatDate(formData.vacationEndDate):'[vacation end]'};
    const body=d.body.replace(/\{\{([^{}]+)}}/g,(_,key)=>values[key]||'');
    return d.name+'\n\nUNSIGNED DRAFT - NOT APPROVED\n\n'+body+'\n\n'+d.hrName+'\n'+d.hrTitle+'\n\nEmployer Information\n'+[d.name,d.address,d.email,d.phone,d.website,d.identifiers].filter(Boolean).join('\n');
  }, [formData,selectedTemplate]);

  const loadRequests = async () => {
    const target=companyId;
    try {
      const response = await letterRequestAPI.getMyRequests();
      if(scope.current!==target)return;
      setRequests(response.data || []);
      setError('');
    } catch (err) {
      if(scope.current!==target)return;
      setError('Failed to load letter requests');
      console.error(err);
    } finally {
      if(scope.current===target)setLoading(false);
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
    if(!selectedTemplate){setError('This letter type is not configured yet.');return;}
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
      setError(err.response?.data?.message||'Failed to submit letter request');
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
      <div className="header-bar accent-page-header">
        <div>
          <ScreenTitle title="Requests" icon="file" eyebrow="EMPLOYEE SERVICES" />
          <p className="page-subtitle">Submit employee letters for admin approval and download approved PDFs.</p>
        </div>
      </div>

      <RecordSummary items={[{ label: 'Letter requests', value: requests.length, icon: 'file' }, { label: 'In review', value: requests.filter(r => r.status === 'SUBMITTED').length, icon: 'clock' }, { label: 'Ready to download', value: requests.filter(r => r.status === 'APPROVED').length, icon: 'check' }]} />
      {error && <div className="error-message" role="alert">{error}</div>}

      {!availability?<LoadingIndicator label="Checking company letter setup..."/>:!availableTemplates.length?<div className="card"><h2>{availability.failed?'Unable to check company letter setup':'Your company is setting up employee letters'}</h2><p>New requests will be available after a Company Admin completes the company and HR details. Your request history and issued letters remain available below.</p></div>:<div className="requests-layout">
        <form className="card request-editor" onSubmit={handleSubmit}>
          <div className="panel-heading">
            <div>
              <h2>{REQUEST_TYPES[formData.requestType].label}</h2>
              <p>{REQUEST_TYPES[formData.requestType].description}</p>
            </div>
          </div>

          <div className="request-type-tabs" role="group" aria-label="Letter request type">
            {Object.entries(REQUEST_TYPES).filter(([value])=>availableTemplates.some(t=>t.type===value)).map(([value, config]) => (
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
                value={`${user?.firstName || ''} ${user?.lastName || ''}`.trim()}
                readOnly
                placeholder="Full legal name"
                required
              />
            </div>
            <div className="form-group">
              <label htmlFor="letter-profile-dob">Date of birth</label>
              <input id="letter-profile-dob" type="date" value={user?.dateOfBirth||''} readOnly />
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

          <div className="letter-preview"><span>Unsigned draft - not approved</span><div className="letter-preview-paper">{selectedTemplate?.definition.logo&&<img src={selectedTemplate.definition.logo} alt="Company logo" style={{maxWidth:160,maxHeight:70,objectFit:'contain'}}/>}<pre style={{whiteSpace:'pre-wrap',font:'inherit',lineHeight:1.7}}>{preview}</pre></div></div>

          <div className="action-bar compact-actions">
            <button className="button button-primary" type="submit" disabled={saving||!selectedTemplate}>
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
      </div>}

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
                {request.status === 'SUBMITTED' && <button className="button button-small button-secondary" onClick={()=>handleDownload(request)} type="button">Download unsigned draft</button>}
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
