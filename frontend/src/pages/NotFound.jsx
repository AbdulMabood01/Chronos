import ScreenTitle from '../components/ScreenTitle';
import React from 'react';
import '../styles.css';

export default function NotFound() {
  return (
    <div className="page-container">
      <div className="error-section">
        <ScreenTitle title="404 - Page Not Found" icon="grid" eyebrow="LET'S GET YOU BACK" />
        <p>The page you're looking for doesn't exist.</p>
      </div>
    </div>
  );
}
