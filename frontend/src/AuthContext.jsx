import React, { createContext, useState, useContext, useEffect } from 'react';
import { authAPI, userAPI } from './api';

const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    checkAuth();
  }, []);

  const checkAuth = async () => {
    try {
      const token = localStorage.getItem('authToken');
      if (token) {
        const response = await authAPI.getCurrentUser();
        setUser(response.data);
      }
    } catch (err) {

      localStorage.removeItem('authToken');
    } finally {
      setLoading(false);
    }
  };

  const login = async (email, password) => {
    try {
      const credentials = await authAPI.login(email, password);
      localStorage.setItem('authToken', credentials.data.token);
      const response = await authAPI.getCurrentUser();
      setUser(response.data);
      setError(null);
      return response.data;
    } catch (err) {
      setError('Login failed');
      localStorage.removeItem('authToken');
      throw err;
    }
  };

  const logout = () => {
    localStorage.removeItem('authToken');
    setUser(null);
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
