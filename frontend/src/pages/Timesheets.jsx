import ScreenTitle from '../components/ScreenTitle';
import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { timesheetAPI } from '../api';
import { format } from 'date-fns';
import { LoadingIndicator } from '../components/Hourglass';
import '../styles.css';

export default function Timesheets() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [timesheets, setTimesheets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);

  useEffect(() => {
    loadTimesheets();
  }, []);

  const loadTimesheets = async () => {
    try {
      const response = await timesheetAPI.getMyTimesheets();
      setTimesheets(response.data || []);
    } catch (error) {
      console.error('Error loading timesheets:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleOpenTimesheet = async (e) => {
    e.preventDefault();
    try {
      const response = await timesheetAPI.getTimesheet(year, month);
      navigate(`/timesheet/${response.data.id}`);
    } catch (err) {
      setError('Failed to open timesheet for that period');
    }
  };

  const handleViewTimesheet = (timesheet) => {
    navigate(`/timesheet/${timesheet.id}`);
  };

  if (loading) {
    return <div className="page-container highlighted-workspace"><div className="loading-panel"><LoadingIndicator label="Loading timesheets..." /></div></div>;
  }

  return (
    <div className="page-container highlighted-workspace">
      <ScreenTitle title="My Timesheets" icon="clock" eyebrow="YOUR TIME ARCHIVE" />

      {error && <div className="error-message">{error}</div>}

      <div className="card">
        <h2>Create or Open a Timesheet</h2>
        <form onSubmit={handleOpenTimesheet} className="form">
          <div className="form-row">
            <div className="form-group">
              <label htmlFor="timesheets-field-1">Year</label>
              <input id="timesheets-field-1" type="number" value={year} onChange={(e) => setYear(Number(e.target.value))} required />
            </div>
            <div className="form-group">
              <label htmlFor="timesheets-field-2">Month</label>
              <input id="timesheets-field-2" type="number" min="1" max="12" value={month} onChange={(e) => setMonth(Number(e.target.value))} required />
            </div>
          </div>
          <button type="submit" className="button button-primary">Open Timesheet</button>
        </form>
      </div>

      {timesheets.length === 0 ? (
        <div className="empty-state">
          <p>No timesheets yet. Use the form above to create one.</p>
        </div>
      ) : (
        <div className="table-container">
          <table className="data-table">
            <thead>
              <tr>
                <th>Month</th>
                <th>Year</th>
                <th>Status</th>
                <th>Total Hours</th>
                <th>Submitted</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {timesheets.map((timesheet) => (
                <tr key={timesheet.id}>
                  <td>{timesheet.month}</td>
                  <td>{timesheet.year}</td>
                  <td><span className={`status-badge status-${timesheet.status.toLowerCase()}`}>{timesheet.status}</span></td>
                  <td>{timesheet.totalHours || '-'}</td>
                  <td>{timesheet.submittedAt ? format(new Date(timesheet.submittedAt), 'MMM dd, yyyy') : '-'}</td>
                  <td>
                    <button 
                      className="button button-small"
                      onClick={() => handleViewTimesheet(timesheet)}
                    >
                      View
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
