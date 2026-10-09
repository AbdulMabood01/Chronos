import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { useAuth } from './AuthContext';
import { companyAPI } from './api';

const CompanyContext = createContext(null);
export const companyStorageKey = userId => `chronos:company:${userId}`;
const initial = { owner: null, companies: [], memberships: [], currentCompany: null, platformAdmin: false, platformPermissions: null, loading: true, switching: false, error: '' };
const errorText = error => error?.response?.data?.message || error?.userMessage || 'Unable to load workspaces. Please retry.';

export function CompanyProvider({ children }) {
  const { user } = useAuth();
  const userId = user?.id ?? null;
  const identity = useRef(userId);
  identity.current = userId;
  const [state, setState] = useState(initial);
  const current = useRef(null);
  const generation = useRef(0);
  const initialized = useRef(false);
  const refreshVersion = useRef(0);

  const clearSelection = useCallback(id => {
    current.current = null;
    if (id != null) localStorage.removeItem(companyStorageKey(id));
    setState(previous => ({ ...previous, currentCompany: null }));
  }, []);

  const refreshCompanies = useCallback(async () => {
    if (userId == null) return;
    const version = ++refreshVersion.current;
    const selectionVersion = generation.current;
    const active = () => identity.current === userId && version === refreshVersion.current && selectionVersion === generation.current;
    try {
      const { data } = await companyAPI.context();
      if (!active()) return;
      const companies = data.companies || [];
      const platformAdmin = Boolean(data.platformAdmin);
      const firstLoad = !initialized.current;
      initialized.current = true;
      const wanted = current.current?.id ?? (firstLoad ? localStorage.getItem(companyStorageKey(userId)) : null);
      let candidate = companies.find(company => String(company.id) === String(wanted));
      const removed = wanted != null && !candidate;
      if (removed) clearSelection(userId);
      // A removed selection requires an explicit choice; do not silently enter a different company.
      if (!candidate && !removed && firstLoad && !platformAdmin && companies.length === 1) candidate = companies[0];
      let validated = null;
      if (candidate) {
        try {
          validated = (await companyAPI.validateContext(candidate.id)).data;
        } catch (error) {
          if (!active()) return;
          if (error.response?.status !== 403 && error.response?.status !== 404) throw error;
          clearSelection(userId);
          setState({ owner: userId, companies: companies.filter(company => company.id !== candidate.id), memberships: data.memberships || [], currentCompany: null,
            platformAdmin, platformPermissions: data.platformPermissions || null, loading: false, switching: false, error: 'Your company access changed. Choose another workspace.' });
          return;
        }
      }
      if (!active()) return;
      current.current = validated;
      if (validated) localStorage.setItem(companyStorageKey(userId), String(validated.id));
      setState(previous => ({ owner: userId, companies, memberships: data.memberships || [], currentCompany: validated,
        platformAdmin, platformPermissions: data.platformPermissions || null, loading: false, switching: false,
        error: removed ? 'Your company access changed. Choose another workspace.' : !validated && previous.error === 'Your company access changed. Choose another workspace.' ? previous.error : '' }));
    } catch (error) {
      if (active()) setState(previous => ({ ...previous, owner: userId, loading: false, switching: false, error: errorText(error) }));
    }
  }, [userId, clearSelection]);

  const selectCompany = useCallback(async id => {
    if (userId == null) return;
    const version = ++generation.current;
    const active = () => identity.current === userId && version === generation.current;
    if (!id) {
      clearSelection(userId);
      setState(previous => ({ ...previous, switching: false, error: '' }));
      return;
    }
    setState(previous => ({ ...previous, switching: true, error: '' }));
    try {
      const { data } = await companyAPI.validateContext(id);
      if (!active()) return;
      current.current = data;
      localStorage.setItem(companyStorageKey(userId), String(data.id));
      setState(previous => ({ ...previous, currentCompany: data, switching: false, error: '' }));
    } catch (error) {
      if (!active()) return;
      if (error.response?.status === 403 || error.response?.status === 404) {
        clearSelection(userId);
        setState(previous => ({ ...previous, companies: previous.companies.filter(company => String(company.id) !== String(id)) }));
      }
      setState(previous => ({ ...previous, switching: false, error: errorText(error) }));
      throw error;
    }
  }, [userId, clearSelection]);

  useEffect(() => {
    ++generation.current;
    ++refreshVersion.current;
    initialized.current = false;
    current.current = null;
    setState({ ...initial, owner: userId, loading: userId != null });
    if (userId != null) refreshCompanies();
    return () => {
      ++generation.current;
      ++refreshVersion.current;
    };
  }, [userId, refreshCompanies]);

  useEffect(() => {
    if (userId == null) return;
    const refresh = () => { if (document.visibilityState !== 'hidden') refreshCompanies(); };
    const timer = setInterval(refresh, 60000);
    window.addEventListener('focus', refresh);
    window.addEventListener('chronos:company-memberships-changed', refresh);
    return () => {
      clearInterval(timer);
      window.removeEventListener('focus', refresh);
      window.removeEventListener('chronos:company-memberships-changed', refresh);
    };
  }, [userId, refreshCompanies]);

  useEffect(() => {
    const clear = () => {
      ++generation.current; clearSelection(identity.current);
      setState(previous => ({ ...previous, platformAdmin: false, platformPermissions: null }));
    };
    window.addEventListener('chronos:auth-cleared', clear);
    return () => window.removeEventListener('chronos:auth-cleared', clear);
  }, [clearSelection]);

  const visible = state.owner === userId ? state : { ...initial, loading: userId != null };
  // Never use capabilities from a previous company, a pending switch, or a failed revalidation.
  const permissionsReady = !visible.loading && !visible.switching && !visible.error;
  const scoped = visible.currentCompany?.permissions;
  const companyPermissions = permissionsReady && String(scoped?.companyId) === String(visible.currentCompany?.id) ? scoped : null;
  const companyCapabilities = companyPermissions?.capabilities || {};
  const platformCapabilities = permissionsReady && visible.platformAdmin ? visible.platformPermissions?.capabilities || {} : {};
  const projectPermissions = companyPermissions?.projects || [];
  return <CompanyContext.Provider value={{ ...visible, selectCompany, refreshCompanies,
    companyPermissions, companyCapabilities, companyRoles: companyPermissions?.companyRoles || [],
    projectPermissions, platformCapabilities,
    hasCompanyCapability: key => companyCapabilities[key] === true,
    hasPlatformCapability: key => platformCapabilities[key] === true,
    permissionsForProject: id => projectPermissions.find(project => String(project.projectId) === String(id)) || null,
  }}>{children}</CompanyContext.Provider>;
}

export function useCompany() {
  const context = useContext(CompanyContext);
  if (!context) throw new Error('useCompany must be used within CompanyProvider');
  return context;
}
