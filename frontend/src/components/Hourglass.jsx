import React from 'react';
import logoUrl from '../assets/Logo.png';

export function HourglassIcon({ spinning = false, className = '', title = 'Chronos hourglass' }) {
  const classes = ['company-logo-icon', spinning ? 'company-logo-icon-loading' : '', className]
    .filter(Boolean)
    .join(' ');

  return (
    <img className={classes} src={logoUrl} alt={title} />
  );
}

export function BrandLogo() {
  return (
    <div className="brand-logo" aria-label="Maxwell Network Inc">
      <img className="brand-logo-image" src={logoUrl} alt="Maxwell Network Inc" />
    </div>
  );
}

export function LoadingIndicator({ label = 'Loading...' }) {
  return (
    <div className="loading-indicator" role="status" aria-live="polite">
      <HourglassIcon spinning />
      <span>{label}</span>
    </div>
  );
}

export function GlobalApiLoader() {
  const [activeRequests, setActiveRequests] = React.useState(0);

  React.useEffect(() => {
    const handleStart = () => setActiveRequests((count) => count + 1);
    const handleEnd = () => setActiveRequests((count) => Math.max(0, count - 1));

    window.addEventListener('chronos:api-start', handleStart);
    window.addEventListener('chronos:api-end', handleEnd);

    return () => {
      window.removeEventListener('chronos:api-start', handleStart);
      window.removeEventListener('chronos:api-end', handleEnd);
    };
  }, []);

  if (activeRequests === 0) {
    return null;
  }

  return (
    <div className="global-api-loader" role="status" aria-live="polite" aria-label="API request in progress">
      <HourglassIcon spinning title="Loading" />
    </div>
  );
}
