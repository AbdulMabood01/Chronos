import ScreenTitle from '../components/ScreenTitle';
import React from 'react';
import { useAuth } from '../AuthContext';

import ProfileForm from '../components/ProfileForm';
import ChangePassword from '../components/ChangePassword';
import DetailCorrectionRequests from '../components/DetailCorrectionRequests';
import CompanyEmploymentDetails from '../components/CompanyEmploymentDetails';
import '../styles.css';

export default function Profile() {
  const { user, updateProfile, refreshUser } = useAuth();
  const [saved, setSaved] = React.useState(false);

  const handleSave = async (profile) => {
    await updateProfile(profile);
    setSaved(true);
  };

  return (
    <div className="page-container profile-page">
      <div className="header-bar">
        <div>
          <ScreenTitle title="Profile" icon="users" eyebrow="YOUR ACCOUNT" />
          <p className="page-subtitle">Manage your personal details and shared login account.</p>
        </div>
      </div>

      {saved && <div className="inline-alert profile-saved-alert">Profile saved.</div>}

      <div className="card profile-card">
        <ProfileForm user={user} onSave={handleSave} />
      </div>

      {(user?.profileCompleted || user?.profileDetailsSubmitted) && <DetailCorrectionRequests onChanged={refreshUser}/>}
      <CompanyEmploymentDetails/>
      <ChangePassword />
    </div>
  );
}
