import React from 'react';
import '../styles.css';

export default function NotFound() {
  return (
    <div className="page-container">
      <div className="error-section">
        <h1>404 - Page Not Found</h1>
        <p>The page you're looking for doesn't exist.</p>
      </div>
    </div>
  );
}
