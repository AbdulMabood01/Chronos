import React, { useState } from 'react';
import { userAPI } from '../api';

export default function EmployeeImport({ onImported }) {
  const [file, setFile] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const submit = async event => {
    event.preventDefault();
    if (!file) return;
    setBusy(true); setError('');
    try {
      const response = await userAPI.importEmployees(file);
      await onImported(response.data.imported);
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to import employees. Check the spreadsheet and try again.');
    } finally { setBusy(false); }
  };
  return <section className="employee-section employee-import" aria-label="Import employees">
    <h2>Import employees</h2>
    <p>Upload an Excel workbook with these headers in its first row. We use the first sheet.</p>
    <div className="import-columns"><span>First name</span><span>Last name</span><span>Email</span></div>
    <p>Up to 1,000 employees · .xlsx or .xls · 5 MB maximum. All rows must be valid and emails unique before anyone is added. Send invitations from the directory after importing.</p>
    <form onSubmit={submit} aria-busy={busy}>
      <div className="form-group"><label htmlFor="employee-workbook">Excel spreadsheet</label>
        <input id="employee-workbook" type="file" accept=".xlsx,.xls" required disabled={busy} onChange={event => {
          const next = event.target.files?.[0];
          setError(''); setFile(null);
          if (next && (!/\.(xlsx|xls)$/i.test(next.name) || next.size > 5 * 1024 * 1024)) {
            setError('Choose an .xlsx or .xls file up to 5 MB.'); return;
          }
          setFile(next);
        }} /></div>
      {error && <p className="error-message" role="alert">{error}</p>}
      <button className="button button-primary" disabled={busy || !file}>{busy ? 'Importing…' : 'Import spreadsheet'}</button>
    </form>
  </section>;
}
