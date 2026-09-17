import React from 'react';
import { useAuth } from '../AuthContext';
import ProfileForm from './ProfileForm';
import './ProfileCompletionPrompt.css';

export default function ProfileCompletionPrompt() {
  const { user, updateProfile } = useAuth();

  if (!user || user.profileCompleted) {
    return null;
  }

  return (
    <div className="modal-backdrop profile-modal-backdrop" role="presentation">
      <div className="modal-card profile-modal-card" role="dialog" aria-modal="true" aria-labelledby="profile-completion-title">
        <div className="modal-header">
          <h2 id="profile-completion-title">Complete Your Profile</h2>
        </div>
        <p className="modal-subtitle">Fill in what you know now. Unknown fields can stay blank.</p>
        <ProfileForm user={user} onSave={updateProfile} submitLabel="Continue" />
      </div>
    </div>
  );
}
