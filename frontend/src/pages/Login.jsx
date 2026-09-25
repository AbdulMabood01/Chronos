import React, { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { BrandLogo, LoadingIndicator } from '../components/Hourglass';
import '../styles.css';
import './Login.css';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleLogin = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      await login(email, password);
      navigate('/dashboard');
    } catch (err) {
      setError(err.response?.status === 429 ? 'Too many attempts. Please wait a minute.' : 'Login failed. Check your email and password.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-experience"><section className="login-story"><span className="login-wordmark">CHRONOS <span>BY MAXWELL</span></span><div className="login-story-content">
      <div className="login-clock-scene" aria-hidden="true">
        <div className="login-clock-orbit" />
        <div className="login-clock">
          <div className="login-clock-face">
            {Array.from({ length: 12 }, (_, index) => <span className="login-clock-tick" key={index} style={{ '--tick': index }} />)}
            <span className="login-clock-hand login-clock-hour" />
            <span className="login-clock-hand login-clock-minute" />
            <span className="login-clock-pin" />
          </div>
        </div>
        <div className="login-clock-shadow" />
      </div>
      <span className="hero-kicker">A BETTER RHYTHM FOR YOUR WORKDAY</span><h1>Your time.<br/>Well managed.</h1><p>One home for your hours, time off,<br/>and everything that keeps work moving.</p><div className="login-story-rule"/><span className="login-story-caption">More clarity. Less administration.</span></div><small>Maxwell Network Inc. &middot; Employee workspace</small></section><div className="login-container">
      <div className="login-card">
        <BrandLogo /><span className="eyebrow">WELCOME TO CHRONOS</span><h1>Make yourself at home.</h1><p className="signin-subtitle">Sign in to your employee workspace.</p>

        {location.state?.passwordChanged && <p role="status" className="inline-alert">Password changed. Sign in with your new password.</p>}
        <form onSubmit={handleLogin}>
          <div className="form-group">
            <label htmlFor="email">Work email</label>
            <input id="email" type="email" autoComplete="username" required maxLength={255} value={email} onChange={e => setEmail(e.target.value)} />
          </div>
          <div className="form-group">
            <label htmlFor="password">Password</label>
            <input id="password" type="password" autoComplete="current-password" required maxLength={72} value={password} onChange={e => setPassword(e.target.value)} />
          </div>
          <button type="submit" disabled={loading} className="button button-primary">
            {loading ? <LoadingIndicator label="Signing in..." /> : 'Sign In'}
          </button>
        </form>

        <p className="login-note"><Link to="/forgot-password">Forgot password?</Link></p>
        {error && <p className="error-message" role="alert">{error}</p>}
        <p className="login-note">
          New employees: use the invitation email from your administrator to activate your account.
        </p>
      </div>
    </div></div>
  );
}
