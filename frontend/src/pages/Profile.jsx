import React from 'react';
import { useAuth } from '../AuthContext';
import ProfileForm from '../components/ProfileForm';
import '../styles.css';

export default function Profile() {
  const { user, updateProfile } = useAuth();
  const [saved, setSaved] = React.useState(false);

  const handleSave = async (profile) => {
    await updateProfile(profile);
    setSaved(true);
  };

  return (
    <div className="page-container profile-page">
      <div className="header-bar">
        <div>
          <h1>Profile</h1>
          <p className="page-subtitle">Keep your employee details current.</p>
        </div>
      </div>

      {saved && <div className="inline-alert profile-saved-alert">Profile saved.</div>}

      <div className="card profile-card">
        <ProfileForm user={user} onSave={handleSave} />
      </div>
    </div>
  );
}
