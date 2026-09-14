import React from 'react';
import { createPortal } from 'react-dom';

// Shared validation feedback: visible regardless of the page's scroll position.
export default function ValidationMessage({ message, onDismiss }) {
  if (!message) return null;
  return createPortal(
    <div className="timesheet-error-toast" role="alert" aria-live="assertive">
      <div><strong>Unable to complete this action</strong><p>{message}</p></div>
      <button type="button" aria-label="Dismiss error" onClick={onDismiss}>Close</button>
    </div>,
    document.body,
  );
}
