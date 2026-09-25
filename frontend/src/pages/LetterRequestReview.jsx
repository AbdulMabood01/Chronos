import ScreenTitle from '../components/ScreenTitle';
import { formatDate } from '../utils/dates';
import React, { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { letterRequestAPI } from '../api';
import { LoadingIndicator } from '../components/Hourglass';
import logoUrl from '../assets/Logo.png';
import managerSignatureUrl from '../assets/manager-signature.png';
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
  const { id } = useParams();
  const navigate = useNavigate();
  const [request, setRequest] = useState(null);
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState('');
  const [rejecting, setRejecting] = useState(false);
  const [rejectReason, setRejectReason] = useState('');

  useEffect(() => {
    loadRequest();
  }, [id]);

  const loadRequest = async () => {
    try {
      const response = await letterRequestAPI.getRequest(id);
      setRequest(response.data);
      setError('');
    } catch (err) {
      setError('Failed to load letter request');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const downloadPdf = async () => {
    try {
      const response = await letterRequestAPI.downloadPdf(id);
      openPdfBlob(response);
      setError('');
    } catch (err) {
      setError('Failed to download PDF');
      console.error(err);
    }
  };

  const approve = async () => {
    setWorking(true);
    try {
      await letterRequestAPI.approveRequest(id);
      navigate('/admin');
    } catch (err) {
      setError('Failed to approve letter request');
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
      await letterRequestAPI.rejectRequest(id, reason);
      navigate('/admin');
    } catch (err) {
      setError('Failed to reject letter request');
      setWorking(false);
    }
  };

  if (user?.role !== 'ADMIN') {
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
        <Link className="button button-secondary" to="/admin">Back to Admin</Link>
      </div>
    );
  }

  const lines = (request.letterPreview || '').split('\n');
  const title = lines[0] || 'Maxwell';
  const date = lines[1] || '';
  const subjectIndex = lines.findIndex((line) => line.startsWith('Subject:'));
  const salutation = lines.find((line) => line.toLowerCase().startsWith('to whom')) || '';
  const subject = subjectIndex >= 0 ? lines[subjectIndex] : LETTER_TYPES[request.requestType];
  const bodyLines = subjectIndex >= 0 ? lines.slice(subjectIndex + 1).filter(Boolean) : lines.slice(2).filter(Boolean);
  const employerIndex = bodyLines.findIndex((line) => line === EMPLOYER_INFORMATION_TITLE);
  const webFooterIndex = bodyLines.findIndex((line) => line === 'tech.maxwellnetwork.org');
  const detailEndIndex = employerIndex >= 0
    ? (webFooterIndex >= 0 ? webFooterIndex : bodyLines.length)
    : bodyLines.length;
  const letterBodyLines = employerIndex >= 0 ? bodyLines.slice(0, employerIndex) : bodyLines;
  const employerLines = employerIndex >= 0 ? bodyLines.slice(employerIndex, detailEndIndex) : [];
  const webFooterLines = webFooterIndex >= 0 ? bodyLines.slice(webFooterIndex) : [];

  return (
    <div className="page-container letter-review-page">
      <div className="header-bar">
        <div>
          <ScreenTitle title="Review Letter" icon="file" eyebrow="DOCUMENT REVIEW" />
          <p className="page-subtitle">{LETTER_TYPES[request.requestType]} for {request.requestedFullName || request.userName}</p>
        </div>
        <Link className="button button-secondary" to="/admin">Back</Link>
      </div>

      {error && <div className="error-message">{error}</div>}

      <div className="letter-review-layout">
        <section className="letter-paper">
          <header className="letter-paper-header">
            <div className="letter-paper-brand">
              <img src={logoUrl} alt={title} />
              <div>
                <strong>Maxwell Network Inc</strong>
              </div>
            </div>
            <p>{date}</p>
          </header>
          <p className="letter-salutation">{salutation}</p>
          <p className="letter-subject">{subject}</p>
          <div className="letter-body">
            {letterBodyLines.map((line, index) => (
              <React.Fragment key={`${line}-${index}`}>
                {line === 'Best Regards,' && <img className="letter-manager-signature" src={managerSignatureUrl} alt="Manager signature" />}
                <p>{line}</p>
              </React.Fragment>
            ))}
          </div>
          {employerLines.length > 0 && (
            <section className="letter-employer-info">
              <strong>{employerLines[0]}</strong>
              {employerLines.slice(1).map((line, index) => (
                <span key={`${line}-${index}`}>
                  <b>{line.split(':')[0]}:</b>
                  <span>{line.split(':').slice(1).join(':').trim()}</span>
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
        </section>

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
          <button className="button button-secondary" onClick={downloadPdf} type="button">
            Show Letter in PDF
          </button>
          {request.status === 'SUBMITTED' && (
            <>
              <button className="button button-success" onClick={approve} type="button" disabled={working}>
                {working ? <LoadingIndicator label="Working..." /> : 'Approve'}
              </button>
              <button className="button button-danger" onClick={() => setRejecting(true)} type="button" disabled={working}>
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
