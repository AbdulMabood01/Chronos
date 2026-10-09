import {resolveLetter,letterError} from '../components/LetterBranding';
import ScreenTitle from '../components/ScreenTitle';
import { formatDate } from '../utils/dates';
import React, { useEffect, useState, useRef } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { companyLetterRequestAPI } from '../api';
import { useCompany } from '../CompanyContext';
import { LoadingIndicator } from '../components/Hourglass';
import '../styles.css';

const LETTER_TYPES = {
  EMPLOYMENT_VERIFICATION: 'Employment Verification Letter',
  TRAVEL: 'Travel Letter',
  VACATION: 'Vacation Letter',
};

const EMPLOYER_INFORMATION_TITLE = 'Employer Information';


function openPdfBlob(response) {
  const blob = new Blob([response.data], { type: 'application/pdf' });
  const url = URL.createObjectURL(blob);
  window.open(url, '_blank', 'noopener,noreferrer');
  window.setTimeout(() => URL.revokeObjectURL(url), 30000);
}

export default function LetterRequestReview() {
  const { user } = useAuth();
  const {currentCompany,companyCapabilities}=useCompany();const companyId=currentCompany?.id;const letterRequestAPI=companyLetterRequestAPI(companyId);
  const { id } = useParams();
  const navigate = useNavigate();
  const scope=useRef(`${companyId}:${id}`);scope.current=`${companyId}:${id}`;
  const [request, setRequest] = useState(null);
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState('');
  const [rejecting, setRejecting] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [review, setReview] = useState({ fullName: '', jobTitle: '', employmentStartDate: '', reviewNote: '' });
  const [reviewConfirmed, setReviewConfirmed] = useState(false);
  const currentReview=useRef(review);currentReview.current=review;
  const [configuration,setConfiguration]=useState(null),[pdfPreview,setPdfPreview]=useState('');
  useEffect(()=>{setPdfPreview('');setReviewConfirmed(false);},[review]);
  useEffect(()=>()=>{if(pdfPreview)URL.revokeObjectURL(pdfPreview);},[pdfPreview]);

  useEffect(() => {
    setRequest(null);setLoading(true);setConfiguration(null);setPdfPreview('');
    loadRequest();
  }, [companyId,id]);

  const loadRequest = async () => {
    const target=scope.current;
    try {
      const response = await letterRequestAPI.getRequest(id);
      let settings={version:null,employers:[],signatories:[],templates:[]};
      if(response.data.status==='SUBMITTED')settings=(await letterRequestAPI.configuration()).data;
      if(scope.current!==target)return;
      setRequest(response.data);
      setConfiguration(settings);
      setReview({
        fullName: response.data.approvedFullName || response.data.requestedFullName || '',
        jobTitle: response.data.approvedJobTitle || response.data.requestedJobTitle || '',
        employmentStartDate: response.data.approvedEmploymentStartDate || response.data.employmentStartDate || '',
        reviewNote: response.data.reviewNote || '',
        configurationVersion:settings.version,
        letter:resolveLetter(settings,response.data.requestType),
      });
      setReviewConfirmed(false);
      setError('');
    } catch (err) {
      if(scope.current!==target)return;
      setError('Failed to load letter request');
      console.error(err);
    } finally {
      if(scope.current===target)setLoading(false);
    }
  };

  const downloadPdf = async () => {
    const target=scope.current,draft=review;
    setWorking(true);
    try {
      if(request.status==='SUBMITTED') {
        const response=await letterRequestAPI.previewReview(id,review,request.version);
        if(scope.current!==target||currentReview.current!==draft)return;
        setPdfPreview(URL.createObjectURL(new Blob([response.data],{type:'application/pdf'})));
      }else {const response=await letterRequestAPI.downloadPdf(id);openPdfBlob(response);}
      setError('');
    } catch (err) {
      if(scope.current===target)setError(await letterError(err));
      console.error(err);
    } finally {if(scope.current===target)setWorking(false);}
  };

  const approve = async () => {
    const corrected = review.fullName.trim() !== request.requestedFullName?.trim()
      || review.jobTitle.trim() !== request.requestedJobTitle?.trim()
      || review.employmentStartDate !== request.employmentStartDate;
    if (corrected && !review.reviewNote.trim()) {
      setError('Explain changes to the employee\'s requested details before approval');
      return;
    }
    setWorking(true);
    try {
      await letterRequestAPI.approveRequest(id, review,request.version);
      navigate('/letter-management');
    } catch (err) {
      setError(err?.response?.data?.message || 'Failed to approve letter request');
      setWorking(false);
    }
  };

  const reject = async () => {
    const reason = rejectReason.trim();
    if (!reason) {
      setError('Rejection reason is required');
      return;
    }

    setWorking(true);
    try {
      await letterRequestAPI.rejectRequest(id, reason,request.version);
      navigate('/letter-management');
    } catch (err) {
      setError('Failed to reject letter request');
      setWorking(false);
    }
  };

  if (!companyCapabilities.canReviewLeaveAndLetters) {
    return (
      <div className="page-container">
        <div className="error-message">You do not have permission to access this page.</div>
      </div>
    );
  }

  if (loading) {
    return <div className="page-container"><div className="loading-panel"><LoadingIndicator label="Loading letter..." /></div></div>;
  }

  if (!request) {
    return (
      <div className="page-container">
        {error && <div className="error-message">{error}</div>}
        <Link className="button button-secondary" to="/letter-management">Back to letters</Link>
      </div>
    );
  }

  const lines = (request.letterPreview || '').split('\n');
  const title = lines[0] || currentCompany.name;
  const date = lines[1] || '';
  const subjectIndex = lines.findIndex((line) => line.startsWith('Subject:'));
  const salutation = lines.find((line) => line.toLowerCase().startsWith('to whom')) || '';
  const subject = subjectIndex >= 0 ? lines[subjectIndex] : LETTER_TYPES[request.requestType];
  const bodyLines = subjectIndex >= 0 ? lines.slice(subjectIndex + 1).filter(Boolean) : lines.slice(2).filter(Boolean);
  const employerIndex = bodyLines.findIndex((line) => line === EMPLOYER_INFORMATION_TITLE);
  const webFooterIndex = -1;
  const detailEndIndex = employerIndex >= 0
    ? (webFooterIndex >= 0 ? webFooterIndex : bodyLines.length)
    : bodyLines.length;
  const letterBodyLines = employerIndex >= 0 ? bodyLines.slice(0, employerIndex) : bodyLines;
  const employerLines = employerIndex >= 0 ? bodyLines.slice(employerIndex, detailEndIndex) : [];
  const webFooterLines = webFooterIndex >= 0 ? bodyLines.slice(webFooterIndex) : [];
  const approvalBlocker = request.userId === user?.id ? 'You cannot approve your own letter request.'
    : !review.letter ? 'Complete company and HR details in Letter management first.'
    : !review.fullName.trim() || !review.jobTitle.trim() || !review.employmentStartDate ? 'Enter the confirmed name, title, and employment start date.'
    : !pdfPreview ? 'Select Preview final letter to generate and review the PDF.'
    : !reviewConfirmed ? 'Check the verification box above to enable approval.' : '';

  return (
    <div className="page-container letter-review-page">
      <div className="header-bar">
        <div>
          <ScreenTitle title="Review Letter" icon="file" eyebrow="DOCUMENT REVIEW" />
          <p className="page-subtitle">{LETTER_TYPES[request.requestType]} for {request.requestedFullName || request.userName}</p>
        </div>
        <Link className="button button-secondary" to="/letter-management">Back</Link>
      </div>

      {error && <div className="error-message">{error}</div>}

      <div className="letter-review-layout">
        {pdfPreview?<section className="card management-section"><h2>Final letter preview - not approved</h2><iframe title="Final letter PDF preview" src={pdfPreview} style={{width:'100%',height:850,border:0}}/><a href={pdfPreview} target="_blank" rel="noreferrer">Open preview PDF</a></section>:<section className="letter-paper">
          <header className="letter-paper-header">
            <div className="letter-paper-brand">
              <div>
                <strong>{title}</strong>
              </div>
            </div>
            <p>{date}</p>
          </header>
          <p className="letter-salutation">{salutation}</p>
          <p className="letter-subject">{subject}</p>
          <dl className="letter-employee-details"><div><dt>Employee name</dt><dd>{request.approvedFullName||request.requestedFullName||request.userName}</dd></div><div><dt>Job title</dt><dd>{request.approvedJobTitle||request.requestedJobTitle||request.jobTitle||'-'}</dd></div><div><dt>Employment start date</dt><dd>{formatDate(request.approvedEmploymentStartDate||request.employmentStartDate)}</dd></div></dl>
          <div className="letter-body">
            {letterBodyLines.map((line, index) => (
              <React.Fragment key={`${line}-${index}`}>
                <p>{line}</p>
              </React.Fragment>
            ))}
          </div>
          {employerLines.length > 0 && (
            <section className="letter-employer-info">
              <strong>{employerLines[0]}</strong>
              {employerLines.slice(1).map((line, index) => (
                <span key={`${line}-${index}`}>
                  <strong>{index===0?'Company':index===1?'Address':line.includes('@')?'Email':/^(?:https?:|www\.)/i.test(line)?'Website':'Contact details'}</strong><span>{line}</span>

                </span>
              ))}
            </section>
          )}
          {webFooterLines.length > 0 && (
            <footer className="letter-contact-footer">
              {webFooterLines.map((line, index) => (
                <span key={`${line}-${index}`}>{line}</span>
              ))}
            </footer>
          )}
        </section>}

        <aside className="letter-review-side">
          <div className="summary-tile">
            <span>Employee</span>
            <strong>{request.requestedFullName || request.userName}</strong>
          </div>
          <div className="summary-tile">
            <span>Title</span>
            <strong>{request.requestedJobTitle || '-'}</strong>
          </div>
          <div className="summary-tile">
            <span>Started</span>
            <strong>{formatDate(request.employmentStartDate)}</strong>
          </div>
          {request.status === 'SUBMITTED' && <div className="card letter-confirmation-card">
            <h3>Confirm details for the final letter</h3>
            <p className="letter-confirmation-help">Confirm employee details, preview the PDF, then approve.</p>
            <p>Requested: {request.requestedFullName} · {request.requestedJobTitle} · {formatDate(request.employmentStartDate)}</p>
            <p>Profile: {request.userName} · {request.jobTitle || 'Title missing'} · {request.userJoiningDate ? formatDate(request.userJoiningDate) : 'Joining date missing'}</p>
            <label>Confirmed name<input value={review.fullName} maxLength="200" onChange={event => { setReview({ ...review, fullName: event.target.value }); setReviewConfirmed(false); }} /></label>
            <label>Confirmed title<input value={review.jobTitle} maxLength="120" onChange={event => { setReview({ ...review, jobTitle: event.target.value }); setReviewConfirmed(false); }} /></label>
            <label>Confirmed employment start date<input type="date" value={review.employmentStartDate} onChange={event => { setReview({ ...review, employmentStartDate: event.target.value }); setReviewConfirmed(false); }} /></label>
            <h3>Company &amp; HR details</h3>
            {review.letter?<div className="letter-shared-summary"><p><strong>{review.letter.name}</strong><br/>{review.letter.address}</p><p>{review.letter.hrName} / {review.letter.hrTitle}<br/>{review.letter.email}</p><Link to="/letter-management">Manage company and HR details</Link></div>:<p role="alert">Complete the company and HR details in Letter management before approval.</p>}
            <label>Reason for a correction<textarea value={review.reviewNote} maxLength="500" onChange={event => setReview({ ...review, reviewNote: event.target.value })} /></label>
            <label className="letter-confirmation-check"><input type="checkbox" disabled={!pdfPreview} checked={reviewConfirmed} onChange={event => setReviewConfirmed(event.target.checked)} /> I verified these details for the final PDF</label>
          </div>}
          <button className="button button-secondary" onClick={downloadPdf} type="button" disabled={working}>
            {request.status === 'SUBMITTED' ? 'Preview final letter' : 'Show Letter in PDF'}
          </button>
          {request.status === 'SUBMITTED' && (
            <>
              {approvalBlocker && <p className="letter-approval-guidance" role="status">{approvalBlocker}</p>}
              <button className="button button-success" onClick={approve} type="button" disabled={working || Boolean(approvalBlocker)}>
                {working ? <LoadingIndicator label="Working..." /> : 'Approve'}
              </button>
              <button className="button button-danger" onClick={() => setRejecting(true)} type="button" disabled={working || request.userId===user?.id}>
                Reject
              </button>
            </>
          )}
        </aside>
      </div>

      {rejecting && (
        <div className="modal-backdrop" role="presentation">
          <div className="modal-card" role="dialog" aria-modal="true" aria-labelledby="reject-letter-title">
            <div className="modal-header">
              <h2 id="reject-letter-title">Reject Letter Request</h2>
              <button className="modal-close" onClick={() => setRejecting(false)} type="button" aria-label="Close">
                x
              </button>
            </div>
            <p className="modal-subtitle">Add a clear reason before rejecting this request.</p>
            <textarea
              value={rejectReason}
              onChange={(event) => setRejectReason(event.target.value)}
              rows="4"
              placeholder="Reason for rejection"
              autoFocus
            />
            <div className="action-bar compact-actions">
              <button className="button button-secondary" onClick={() => setRejecting(false)} type="button">
                Cancel
              </button>
              <button className="button button-danger" onClick={reject} type="button" disabled={working}>
                Reject
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
