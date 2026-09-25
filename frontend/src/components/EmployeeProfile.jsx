import React from 'react';

const display = value => String(value ?? '').trim() || '\u2014';
function Fields({ items }) {
  return <dl className="employee-profile-fields">{items.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{display(value)}</dd></div>)}</dl>;
}

export default function EmployeeProfile({ user }) {
  if (!user) return null;
  const initials = (user.firstName || 'U')[0] + ((user.lastName || '')[0] || '');
  return <section className="employee-section employee-profile-card" aria-label="Employee profile">
    <div className="employee-identity">
      <div className="employee-avatar">{user.profileImageUrl ? <img src={user.profileImageUrl} alt="Profile" /> : <span>{initials}</span>}</div>
      <div><span className="eyebrow">EMPLOYEE PROFILE</span><h2>{display(user.jobTitle)}</h2><p>{display(user.employeeId)}</p>
        <span className={'status-badge ' + (user.isActive ? 'status-approved' : 'status-rejected')}>{user.isActive ? 'Active' : 'Inactive'}</span>
      </div>
    </div>
    <section className="employee-info-section"><h3>Personal information</h3><Fields items={[
      ['First name', user.firstName], ['Last name', user.lastName], ['Role', user.role?.replace(/_/g, ' ')],
      ['Date of birth', user.dateOfBirth], ['Blood group', user.bloodGroup],
      ...(user.role !== 'ADMIN' ? [['SSN Last 4', user.ssnLast4]] : []),
      ['Profile completed', user.profileCompleted ? 'Yes' : 'No'],
    ]} /></section>
    <section className="employee-info-section"><h3>Contact & address</h3><Fields items={[
      ['Work email', user.email], ['Personal email', user.personalEmail], ['Phone', user.phoneNumber],
      ['Address', [user.addressLine1, user.addressLine2, user.city, user.stateProvince, user.postalCode, user.country].filter(Boolean).join(', ')],
    ]} /></section>
    <section className="employee-info-section"><h3>Emergency contact</h3><Fields items={[
      ['Name', user.emergencyContactName], ['Relationship', user.emergencyContactRelationship],
      ['Phone', user.emergencyContactPhone], ['Email', user.emergencyContactEmail],
    ]} /></section>
    <section className="employee-info-section"><h3>Account history</h3><Fields items={[
      ['Created', user.createdAt ? new Date(user.createdAt).toLocaleDateString() : null],
      ['Last updated', user.updatedAt ? new Date(user.updatedAt).toLocaleDateString() : null],
    ]} /></section>
  </section>;
}
