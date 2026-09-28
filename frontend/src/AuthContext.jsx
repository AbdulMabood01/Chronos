import React, { createContext, useState, useContext, useEffect, useRef } from 'react';
import { authAPI, userAPI } from './api';

const AuthContext = createContext();
const IDLE_MS = 30 * 60 * 1000;
const WARNING_MS = 2 * 60 * 1000;
const tokenExpiry = token => {
  try { return JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))).exp * 1000; }
  catch { return Number.POSITIVE_INFINITY; }
};

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [sessionWarning, setSessionWarning] = useState(false);
  const [sessionExpired, setSessionExpired] = useState(false);
  const lastActivity = useRef(Date.now());
  const lastTouch = useRef(0);

  const expire = () => {
    localStorage.removeItem('authToken');
    setUser(null);
    setSessionWarning(false);
    setSessionExpired(true);
  };

  useEffect(() => {
    const onExpired = () => expire();
    window.addEventListener('chronos:session-expired', onExpired);
    return () => window.removeEventListener('chronos:session-expired', onExpired);
  }, []);

  useEffect(() => {
    checkAuth();
  }, []);

  useEffect(() => {
    if (!user) return;
    const check = () => {
      const idleRemaining = lastActivity.current + IDLE_MS - Date.now();
      const absoluteRemaining = tokenExpiry(localStorage.getItem('authToken') || '') - Date.now();
      if (idleRemaining <= 0 || absoluteRemaining <= 0) expire();
      else setSessionWarning(idleRemaining <= WARNING_MS);
    };
    const activity = () => {
      if (sessionWarning || !localStorage.getItem('authToken')) return;
      lastActivity.current = Date.now();
      if (Date.now() - lastTouch.current >= 60000) {
        lastTouch.current = Date.now();
        authAPI.activity().catch(() => {});
      }
    };
    const events = ['pointerdown', 'keydown', 'scroll', 'touchstart'];
    events.forEach(name => window.addEventListener(name, activity, { passive: true }));
    const timer = setInterval(check, 1000);
    check();
    return () => { clearInterval(timer); events.forEach(name => window.removeEventListener(name, activity)); };
  }, [user, sessionWarning]);

  const checkAuth = async () => {
    try {
      const token = localStorage.getItem('authToken');
      if (token) {
        const response = await authAPI.getCurrentUser();
        setUser(response.data);
      }
    } catch (err) {
      if (err.response?.status === 401 || err.response?.status === 403) expire();
    } finally {
      setLoading(false);
    }
  };

  const login = async (email, password) => {
    try {
      const credentials = await authAPI.login(email, password);
      localStorage.setItem('authToken', credentials.data.token);
      const response = await authAPI.getCurrentUser();
      lastActivity.current = Date.now(); lastTouch.current = Date.now();
      setUser(response.data);
      setError(null);
      setSessionExpired(false);
      return response.data;
    } catch (err) {
      setError('Unable to sign in. Check your credentials or try again later.');
      localStorage.removeItem('authToken');
      throw err;
    }
  };

  const logout = () => {
    const token = localStorage.getItem('authToken');
    localStorage.removeItem('authToken');
    setUser(null);
    setSessionWarning(false);
    setSessionExpired(false);
    if (token) authAPI.logout(token).catch(() => {});
  };

  const staySignedIn = async () => {
    await authAPI.activity();
    lastActivity.current = Date.now(); lastTouch.current = Date.now();
    setSessionWarning(false);
  };

  const updateProfile = async (profile) => {
    const response = await userAPI.updateMyProfile(profile);
    setUser((current) => ({
      ...current,
      ...response.data,
    }));
    return response.data;
  };

  const isAdmin = () => user?.role === 'ADMIN';

  const value = {
    user,
    loading,
    error,
    login,
    logout,
    updateProfile,
    isAdmin,
    sessionWarning,
    sessionExpired,
    staySignedIn,
  };

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
};
