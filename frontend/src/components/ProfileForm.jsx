import React, { useState } from 'react';
import { LoadingIndicator } from './Hourglass';

const MAX_PROFILE_PHOTO_BYTES = 650 * 1024;
const PROFILE_PHOTO_SIZE = 512;

const emptyProfile = {
  firstName: '',
  lastName: '',
  jobTitle: '',
  dateOfBirth: '',
  ssnLast4: '',
  profileImageUrl: '',
};

export default function ProfileForm({ user, onSave, submitLabel = 'Save Profile' }) {
  const [formData, setFormData] = useState({
    ...emptyProfile,
    firstName: user?.firstName || '',
    lastName: user?.lastName || '',
    jobTitle: user?.jobTitle || '',
    dateOfBirth: user?.dateOfBirth || '',
    ssnLast4: user?.ssnLast4 || '',
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
      ['jobTitle', 'Job title'],
      ['dateOfBirth', 'DOB'],
    ];
    const missingField = requiredFields.find(([field]) => !String(formData[field] || '').trim());
    if (missingField) {
      setError(`${missingField[1]} is required.`);
      return;
    }

    const ssnLast4 = formData.ssnLast4.trim();
    if (ssnLast4 && !/^\d{4}$/.test(ssnLast4)) {
      setError('SSN last 4 must be blank or exactly 4 digits.');
      return;
    }

    setSaving(true);
    setError('');
    try {
      await onSave({
        firstName: formData.firstName,
        lastName: formData.lastName,
        jobTitle: formData.jobTitle,
        dateOfBirth: formData.dateOfBirth || null,
        ssnLast4,
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
            accept="image/*"
            onChange={handlePhotoChange}
          />
          {formData.profileImageUrl && (
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
          <label htmlFor="profile-first-name">First Name</label>
          <input
            id="profile-first-name"
            value={formData.firstName}
            onChange={(event) => updateField('firstName', event.target.value)}
            autoComplete="given-name"
            required
          />
        </div>
        <div className="form-group">
          <label htmlFor="profile-last-name">Last Name</label>
          <input
            id="profile-last-name"
            value={formData.lastName}
            onChange={(event) => updateField('lastName', event.target.value)}
            autoComplete="family-name"
            required
          />
        </div>
      </div>

      <div className="form-group">
        <label htmlFor="profile-job-title">Job Title</label>
        <input
          id="profile-job-title"
          value={formData.jobTitle}
          onChange={(event) => updateField('jobTitle', event.target.value)}
          autoComplete="organization-title"
          required
        />
      </div>

      <div className="form-row">
        <div className="form-group">
          <label htmlFor="profile-dob">DOB</label>
          <input
            id="profile-dob"
            type="date"
            value={formData.dateOfBirth}
            onChange={(event) => updateField('dateOfBirth', event.target.value)}
            autoComplete="bday"
            required
          />
        </div>
        <div className="form-group">
          <label htmlFor="profile-ssn-last4">4 Digits of SSN</label>
          <input
            id="profile-ssn-last4"
            value={formData.ssnLast4}
            onChange={(event) => updateField('ssnLast4', event.target.value.replace(/\D/g, '').slice(0, 4))}
            inputMode="numeric"
            maxLength="4"
            autoComplete="off"
          />
        </div>
      </div>

      <div className="action-bar compact-actions">
        <button type="submit" className="button button-primary" disabled={saving}>
          {saving ? <LoadingIndicator label="Saving..." /> : submitLabel}
        </button>
      </div>
    </form>
  );
}
