import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { authAPI } from '../api';
import PasswordForm from './PasswordForm';

export default function ChangePassword() {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const submit = async data => {
    await authAPI.changePassword(data);
    logout();
    navigate('/login', { replace: true, state: { passwordChanged: true } });
  };
  return <section className="card password-card" aria-labelledby="change-password-heading">
    <h2 id="change-password-heading">Change password</h2>
    <p className="password-help">You will be signed out on all devices after changing your password.</p>
    <PasswordForm requireCurrent onSubmit={submit} label="Change password" />
  </section>;
}
