import React from 'react';
import Icon from './Icon';
import './ProfileChecklist.css';
const groups = [
  ['Personal details', [['firstName', 'profile-first-name'], ['lastName', 'profile-last-name'], ['jobTitle', 'profile-job-title'], ['dateOfBirth', 'profile-dob']]],
  ['Phone number', [['phoneNumber', 'profile-phoneNumber']]],
  ['Home address', [['addressLine1', 'profile-addressLine1'], ['city', 'profile-city'], ['country', 'profile-country']]],
  ['Emergency contact', [['emergencyContactName', 'profile-emergencyContactName'], ['emergencyContactRelationship', 'profile-emergencyContactRelationship'], ['emergencyContactPhone', 'profile-emergencyContactPhone']]],
];
export default function ProfileChecklist({ profile }) {
  const items = groups.map(([label, fields], index) => ({ label, fields, required: index === 0,
    completed: fields.filter(([key]) => String(profile?.[key] || '').trim()).length,
    missing: fields.find(([key]) => !String(profile?.[key] || '').trim()) }));
  const count = items.filter(item => !item.missing).length;
  const next = items.find(item => item.missing);
  const navigateTo = (event, item) => {
    const field = event.currentTarget.closest('form')?.querySelector('#' + (item.missing || item.fields[0])[1]);
    field?.focus({ preventScroll: true });
    field?.scrollIntoView?.({ behavior: window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'center' });
  };
  return <section className="profile-overview" aria-label="Profile completion checklist">
    <div className="profile-overview-heading"><div><h3>Profile overview</h3><p>Keep your information current and easy to find.</p></div><span className="profile-overview-count">{count} of {items.length} complete</span></div>
    <progress aria-label="Profile completeness" max={items.length} value={count} />
    <nav aria-label="Profile sections" className="profile-section-nav">
      {items.map((item, index) => <button key={item.label} type="button" className="profile-section-link" aria-label={`Review ${item.label.toLowerCase()}`} onClick={event => navigateTo(event, item)}>
        <span className="profile-section-icon"><Icon name={['users', 'phone', 'home', 'heart'][index]} size={18} /></span>
        <span className="profile-section-copy"><strong>{item.label}</strong><small>{item.required ? 'Required' : 'Recommended'} · {item.completed}/{item.fields.length} fields</small></span>
        <span className={`profile-section-status${item.missing ? '' : ' is-complete'}`}>{item.missing ? 'Incomplete' : 'Complete'}</span><Icon name="arrow" size={16} />
      </button>)}
    </nav>
    <div className="profile-overview-footer"><p>{next ? 'Select a section to review, or continue with the next missing detail.' : 'Your details are ready. Save your profile to keep changes.'}</p>
      {next && <button type="button" className="button button-secondary button-small" onClick={event => navigateTo(event, next)}>Continue profile<Icon name="arrow" size={16} /></button>}
    </div>
  </section>;
}
