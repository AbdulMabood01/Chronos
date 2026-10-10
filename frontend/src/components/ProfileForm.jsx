import ProfileChecklist from './ProfileChecklist';
import DateInput from './DateInput';
import React, { useState } from 'react';
import { LoadingIndicator } from './Hourglass';
import './ProfileForm.css';

const MAX_PROFILE_PHOTO_BYTES = 650 * 1024;
const PROFILE_PHOTO_SIZE = 512;
const timezones = Intl.supportedValuesOf?.('timeZone') || ['America/Chicago', 'America/New_York', 'America/Denver', 'America/Los_Angeles', 'Europe/London', 'Asia/Kolkata', 'UTC'];
const additionalSections = [["Contact details",[["phoneNumber","Phone number","tel","tel"],["personalEmail","Personal email","email","email"]]],["Address",[["addressLine1","Address line 1","text","address-line1"],["addressLine2","Address line 2","text","address-line2"],["city","City","text","address-level2"],["stateProvince","State / Province","text","address-level1"],["postalCode","Postal code","text","postal-code"],["country","Country","text","country-name"]]],["Emergency contact",[["emergencyContactName","Contact name"],["emergencyContactRelationship","Relationship"],["emergencyContactPhone","Contact phone","tel"],["emergencyContactEmail","Contact email","email"]]]];
const additionalFieldLimits = {"phoneNumber":40,"personalEmail":255,"addressLine1":200,"addressLine2":200,"city":100,"stateProvince":100,"postalCode":20,"country":100,"bloodGroup":3,"emergencyContactName":200,"emergencyContactRelationship":100,"emergencyContactPhone":40,"emergencyContactEmail":255};

const demographicOptions = {
  gender: ['Male', 'Female', 'Other'],
  race: ['American Indian or Alaska Native', 'Asian', 'Black or African American', 'Native Hawaiian or Other Pacific Islander', 'White', 'Two or more races', 'Other', 'Prefer not to say'],
  ethnicity: ['Hispanic or Latino', 'Not Hispanic or Latino', 'Other', 'Prefer not to say'],
};
const requiredDetails = additionalSections.flatMap(([, fields]) => fields.filter(([field]) => field !== 'addressLine2').map(([field, label]) => [field, label]));

const emptyProfile = {
  firstName: '',
  lastName: '',
  dateOfBirth: '',
  profileImageUrl: '',
};

export default function ProfileForm({ user, onSave, submitLabel = 'Save Profile', showBloodGroup = true, readOnly = false }) {
  const [formData, setFormData] = useState({
    ...emptyProfile,
    timezone: user?.timezone || 'America/Chicago',
    ...Object.fromEntries(Object.keys(additionalFieldLimits).map((field) => [field, user?.[field] || ''])),
    firstName: user?.firstName || '',
    lastName: user?.lastName || '',
    dateOfBirth: user?.dateOfBirth || '',
    gender:user?.gender||'',race:user?.race||'',ethnicity:user?.ethnicity||'',joiningDate:user?.joiningDate||'',
    profileImageUrl: user?.profileImageUrl || '',
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const updateField = (field, value) => {
    setFormData((current) => ({ ...current, [field]: value }));
  };

  const resizeProfilePhoto = (file) => new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const image = new Image();
      image.onload = () => {
        const scale = Math.min(1, PROFILE_PHOTO_SIZE / Math.max(image.width, image.height));
        const width = Math.max(1, Math.round(image.width * scale));
        const height = Math.max(1, Math.round(image.height * scale));
        const canvas = document.createElement('canvas');
        canvas.width = width;
        canvas.height = height;

        const context = canvas.getContext('2d');
        context.drawImage(image, 0, 0, width, height);
        resolve(canvas.toDataURL('image/jpeg', 0.82));
      };
      image.onerror = () => reject(new Error('Profile photo could not be loaded.'));
      image.src = reader.result;
    };
    reader.onerror = () => reject(new Error('Failed to read profile photo.'));
    reader.readAsDataURL(file);
  });

  const handlePhotoChange = (event) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    if (!file.type.startsWith('image/')) {
      setError('Profile photo must be an image file.');
      return;
    }
    if (file.size > 8 * 1024 * 1024) {
      setError('Profile photo must be smaller than 8 MB.');
      event.target.value = '';
      return;
    }

    resizeProfilePhoto(file)
      .then((dataUrl) => {
        if (dataUrl.length > MAX_PROFILE_PHOTO_BYTES) {
          setError('Profile photo is still too large after resizing. Choose a smaller image.');
          event.target.value = '';
          return;
        }
        updateField('profileImageUrl', dataUrl);
        setError('');
      })
      .catch((err) => {
        setError(err.message || 'Failed to read profile photo.');
        event.target.value = '';
      });
      setError('');
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    const requiredFields = [
      ['firstName', 'First name'],
      ['lastName', 'Last name'],
      ['dateOfBirth', 'DOB'],
      ['gender', 'Gender'], ['race', 'Race'], ['ethnicity', 'Ethnicity'], ['joiningDate', 'Joining date'],
      ...requiredDetails,
    ];
    const missingField = requiredFields.find(([field]) => !String(formData[field] || '').trim());
    if (missingField) {
      setError(`${missingField[1]} is required.`);
      return;
    }

    setSaving(true);
    setError('');
    try {
      await onSave({
        timezone: formData.timezone,
        ...Object.fromEntries(Object.keys(additionalFieldLimits).filter(field => showBloodGroup || field !== 'bloodGroup').map((field) => [field, formData[field].trim()])),
        firstName: formData.firstName,
        lastName: formData.lastName,
        dateOfBirth: formData.dateOfBirth || null,
        gender:formData.gender,race:formData.race,ethnicity:formData.ethnicity,joiningDate:formData.joiningDate||null,
        profileImageUrl: formData.profileImageUrl || null,
      });
    } catch (err) {
      const status = err?.response?.status;
      const backendMessage = err?.response?.data?.message;
      setError(backendMessage || (status === 400 ? 'Check the required fields and try again.' : 'Failed to save profile.'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <form className="form profile-form" onSubmit={handleSubmit}>
      {error && <div className="error-message">{error}</div>}

      <ProfileChecklist profile={formData} />
      <fieldset disabled={readOnly} className="profile-fields-wrapper">
      <section className="profile-additional-details"><h3>Personal details</h3><p className="profile-field-note">All fields are required unless marked optional. These personal details lock when submitted.</p><div className="form-row">
        {Object.entries(demographicOptions).map(([field, options]) => <div className="form-group" key={field}>
          <label className="required-field-label" htmlFor={`profile-${field}`}>{field.charAt(0).toUpperCase()+field.slice(1)}</label>
          <select id={`profile-${field}`} required value={formData[field]} disabled={user?.profileDetailsSubmitted===true && !user?.profileCorrectionOpen && !!user?.[field]} onChange={event=>updateField(field,event.target.value)}>
            <option value="">Select {field}</option>
            {user?.[field] && !options.includes(user[field]) && <option value={user[field]}>{user[field]}</option>}
            {options.map(option => <option key={option} value={option}>{option}</option>)}
          </select>
        </div>)}
        <div className="form-group"><label className="required-field-label" htmlFor="profile-joining-date">Joining date</label><DateInput pickerLabel="Choose joining date" id="profile-joining-date" required value={formData.joiningDate} readOnly={user?.profileDetailsSubmitted===true && !user?.profileCorrectionOpen && !!user?.joiningDate} onChange={event=>updateField('joiningDate',event.target.value)}/></div>
      </div></section>

      <div className="profile-photo-section">
        <div className="profile-photo-preview" aria-label="Profile photo preview">
          {formData.profileImageUrl ? (
            <img src={formData.profileImageUrl} alt="" />
          ) : (
            <span>{`${(formData.firstName || user?.firstName || 'U').charAt(0)}${(formData.lastName || user?.lastName || '').charAt(0)}`}</span>
          )}
        </div>
        <div className="form-group">
          <label htmlFor="profile-photo">Profile Pic</label>
          <input
            id="profile-photo"
            type="file"
            disabled={user?.profileCompleted===true && !user?.profileCorrectionOpen && !!user?.profileImageUrl}
            accept="image/*"
            onChange={handlePhotoChange}
          />
          {formData.profileImageUrl && (!user?.profileCompleted || user?.profileCorrectionOpen) && (
            <button
              type="button"
              className="button button-small button-secondary"
              onClick={() => updateField('profileImageUrl', '')}
            >
              Remove Photo
            </button>
          )}
        </div>
      </div>

      <div className="form-row">
        <div className="form-group">
          <label className="required-field-label" htmlFor="profile-first-name">First Name</label>
          <input
            id="profile-first-name"
            name="given-name"
            readOnly={user?.profileCompleted===true && !user?.profileCorrectionOpen}
            value={formData.firstName}
            onChange={(event) => updateField('firstName', event.target.value)}
            autoComplete="section-profile given-name"
            required
          />
        </div>
        <div className="form-group">
          <label className="required-field-label" htmlFor="profile-last-name">Last Name</label>
          <input
            id="profile-last-name"
            name="family-name"
            readOnly={user?.profileCompleted===true && !user?.profileCorrectionOpen}
            value={formData.lastName}
            onChange={(event) => updateField('lastName', event.target.value)}
            autoComplete="section-profile family-name"
            required
          />
        </div>
      </div>


      <div className="form-row">
        <div className="form-group">
          <label className="required-field-label" htmlFor="profile-dob">DOB</label>
          <DateInput pickerLabel="Choose date of birth"
            id="profile-dob"
            readOnly={user?.profileCompleted===true}
            type="date"
            value={formData.dateOfBirth}
            onChange={(event) => updateField('dateOfBirth', event.target.value)}
            autoComplete="bday"
            required
          />
        </div>

      </div>

      <fieldset className="profile-details-section">
        <legend>Reminder preferences</legend>
        <div className="form-group">
          <label htmlFor="profile-timezone">Timezone</label>
          <select id="profile-timezone" value={formData.timezone} onChange={(event) => updateField('timezone', event.target.value)}>
            {[...new Set([...timezones, 'UTC', formData.timezone])].sort().map((zone) => <option key={zone} value={zone}>{zone.replaceAll('_', ' ')}</option>)}
          </select>
          <p>Timesheet reminders arrive at 8:00 PM on Fridays and the last day of each month in this timezone.</p>
        </div>
      </fieldset>

      {showBloodGroup && <fieldset className="profile-details-section">
        <legend>Personal details</legend>
        <div className="form-group">
          <label htmlFor="profile-blood-group">Blood group</label>
          <select id="profile-blood-group" disabled={user?.profileDetailsSubmitted===true && !user?.profileCorrectionOpen && !!user?.bloodGroup} value={formData.bloodGroup} onChange={(event) => updateField('bloodGroup', event.target.value)}>
            <option value="">Unknown / Prefer not to say</option>
            {['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'].map((group) => <option key={group} value={group}>{group}</option>)}
          </select>
        </div>
      </fieldset>}
      {additionalSections.map(([title, fields]) => (
        <fieldset className="profile-details-section" key={title}>
          <legend>{title}</legend>
          {title === 'Address' && !readOnly && <p className="address-autofill-hint">Choose a saved address from your browser’s suggestions, or enter your address below.</p>}
          <div className="profile-details-grid">
            {fields.map(([field, label, type = 'text', autoComplete = 'off']) => (
              <div className="form-group" key={field}>
                <label className={field !== 'addressLine2' ? 'required-field-label' : undefined} htmlFor={`profile-${field}`}>{label}{field === 'addressLine2' ? ' (optional)' : ''}</label>
                <input id={`profile-${field}`} name={autoComplete === 'off' ? field : autoComplete} type={type} autoComplete={autoComplete === 'off' ? 'off' : `section-profile ${autoComplete}`}
                  required={field !== 'addressLine2'} maxLength={additionalFieldLimits[field]} value={formData[field]}
                  onChange={(event) => updateField(field, event.target.value)} />
              </div>
            ))}
          </div>
        </fieldset>
      ))}

      </fieldset>
      {!readOnly && <div className="action-bar compact-actions">
        <button type="submit" className="button button-primary" disabled={saving}>
          {saving ? <LoadingIndicator label="Saving..." /> : submitLabel}
        </button>
      </div>
      }
    </form>
  );
}
