import React, { useEffect, useMemo, useState } from 'react';
import { eachDayOfInterval, endOfMonth, format, startOfMonth } from 'date-fns';
import { vacationAPI } from '../api';
import './WorkflowFeatures.css';
export default function TeamLeaveCalendar() {
  const [month, setMonth] = useState(() => format(new Date(), 'yyyy-MM'));
  const [absences, setAbsences] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    let current = true;
    setAbsences([]); setError(''); setLoading(true);
    const [year, number] = month.split('-').map(Number);
    if (!year || !number) { setLoading(false); return; }
    Promise.resolve().then(() => vacationAPI.getTeamCalendar(year, number))
      .then(response => { if (current) setAbsences(response.data || []); })
      .catch(() => { if (current) setError('Could not load the team leave calendar.'); })
      .finally(() => { if (current) setLoading(false); });
    return () => { current = false; };
  }, [month, refresh]);
  const days = useMemo(() => {
    if (!month) return [];
    const date = new Date(month + '-01T00:00:00');
    return eachDayOfInterval({ start: startOfMonth(date), end: endOfMonth(date) });
  }, [month]);
  const [selected, setSelected] = useState('');
  useEffect(() => setSelected(''), [month]);
  const onDay = date => absences.filter(item => item.startDate <= date && item.endDate >= date);
  const selectedAbsences = selected ? onDay(selected) : [];
  return <section className="card workflow-panel" aria-labelledby="team-calendar-title">
    <div className="panel-heading"><div><h2 id="team-calendar-title">Team leave calendar</h2><p>Approved absences for your teams. Select a day to see who is away.</p></div>
      <div className="workflow-controls"><label>Calendar month<input type="month" value={month} onChange={event => setMonth(event.target.value)} /></label><button type="button" className="button button-secondary" disabled={loading} onClick={() => setRefresh(n => n + 1)}>Refresh calendar</button></div></div>
    {loading ? <p role="status">Loading team leave...</p> : error ? <p role="alert">{error}</p> : !month ? <p>Select a month.</p> : <>
      {!absences.length && <p>No approved absences this month.</p>}
      <div className="team-calendar-grid" aria-label="Monthly absences">
        {['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map(day => <div className="team-calendar-weekday" key={day}>{day}</div>)}
        {Array.from({ length: days[0]?.getDay() || 0 }, (_, index) => <div aria-hidden="true" key={'blank-' + index} />)}
        {days.map(day => { const date = format(day, 'yyyy-MM-dd'), away = onDay(date), count = new Set(away.map(item => item.userId)).size;
          return <button type="button" key={date} className={'team-calendar-day' + (count ? ' has-absence' : '')} aria-pressed={selected === date}
            aria-label={format(day, 'MMMM d, yyyy') + ': ' + count + ' away'} onClick={() => setSelected(date)}>
            <span>{day.getDate()}</span>{count > 0 && <strong>{count} away</strong>}
          </button>;
        })}
      </div>
      {selected && <div className="team-day-detail" aria-live="polite"><h3>Away on {selected}</h3>{!selectedAbsences.length ? <p>No approved absences.</p> : <ul>{selectedAbsences.map(item => <li key={item.id}><strong>{item.userName}</strong><span>{item.startDate} to {item.endDate}</span></li>)}</ul>}</div>}
      <p className="table-subtext">Calendar shows full approved date ranges, including weekends.</p>
    </>}
  </section>;
}
