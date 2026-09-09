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
    <div className="page-container login-container">
      <div className="login-card">
        <BrandLogo />

        <form onSubmit={handleDevLogin}>
          <div className="form-group">
            <label>Dev Login (email)</label>
            <input
              type="email"
              value={devEmail}
              onChange={(e) => setDevEmail(e.target.value)}
              placeholder="you@maxwellnetwork.org"
              required
            />
          </div>
          {error && <p className="error-message">{error}</p>}
          <button type="submit" disabled={loading} className="button button-primary">
            {loading ? <LoadingIndicator label="Signing in..." /> : 'Dev Sign In'}
          </button>
        </form>

        <form onSubmit={handleLogin} style={{ marginTop: '2rem' }}>
          <div className="form-group">
            <label>OAuth Token</label>
            <input
              type="password"
              value={token}
              onChange={(e) => setToken(e.target.value)}
              placeholder="Enter your authentication token"
            />
          </div>
          <button type="submit" disabled={loading} className="button button-secondary">
            {loading ? <LoadingIndicator label="Logging in..." /> : 'Sign In With Token'}
          </button>
        </form>

        <p className="login-note">
          In production, this will use Microsoft Entra ID / OAuth2 for seamless company authentication.
        </p>
      </div>
    </div>
  );
}
