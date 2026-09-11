import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { authAPI } from '../api';
import { BrandLogo, LoadingIndicator } from '../components/Hourglass';
import '../styles.css';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [token, setToken] = useState('');
  const [devEmail, setDevEmail] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleLogin = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      await login(token);
      navigate('/dashboard');
    } catch (err) {
      setError('Login failed. Please check your token.');
    } finally {
      setLoading(false);
    }
  };

  const handleDevLogin = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      const response = await authAPI.devLogin(devEmail);
      await login(response.data.token);
      navigate('/dashboard');
    } catch (err) {
      setError('Dev login failed.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-experience"><section className="login-story"><span className="login-wordmark">CHRONOS <span>BY MAXWELL</span></span><div><span className="hero-kicker">A BETTER RHYTHM FOR YOUR WORKDAY</span><h1>Your time.<br/>Well managed.</h1><p>One home for your hours, time off,<br/>and everything that keeps work moving.</p><div className="login-story-rule"/><span className="login-story-caption">More clarity. Less administration.</span></div><small>Maxwell Network Inc. &middot; Employee workspace</small></section><div className="login-container">
      <div className="login-card">
        <BrandLogo /><span className="eyebrow">WELCOME TO CHRONOS</span><h1>Make yourself at home.</h1><p className="signin-subtitle">Sign in to your employee workspace.</p>

        {import.meta.env.DEV && <form onSubmit={handleDevLogin}>
          <div className="form-group">
            <label htmlFor="dev-email">Work email &middot; development sign-in</label>
            <input
              id="dev-email" autoComplete="email" type="email"
              value={devEmail}
              onChange={(e) => setDevEmail(e.target.value)}
              placeholder="you@maxwellnetwork.org"
              required
            />
          </div>
          <button type="submit" disabled={loading} className="button button-primary">
            {loading ? <LoadingIndicator label="Signing in..." /> : 'Dev Sign In'}
          </button>
        </form>}

        <form onSubmit={handleLogin} style={{ marginTop: '2rem' }}>
          <div className="form-group">
            <label htmlFor="access-token">Company access token</label>
            <input
              id="access-token" required type="password"
              value={token}
              onChange={(e) => setToken(e.target.value)}
              placeholder="Enter your authentication token"
            />
          </div>
          <button type="submit" disabled={loading} className="button button-secondary">
            {loading ? <LoadingIndicator label="Logging in..." /> : 'Sign In With Token'}
          </button>
        </form>

        {error && <p className="error-message" role="alert">{error}</p>}
        <p className="login-note">
          Sign in using an access token issued for this application by your company.
        </p>
      </div>
    </div></div>
  );
}
