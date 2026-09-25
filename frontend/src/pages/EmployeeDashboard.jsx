import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { format } from 'date-fns';
import Icon from '../components/Icon';
import ActionRow from '../components/DashboardActionRow';
import { LoadingIndicator } from '../components/Hourglass';
import { displayNumber } from '../utils/dashboard';
import './EmployeeDashboard.css';
import TimeSculpture from '../components/TimeSculpture';

export default function EmployeeDashboard({ user, model, state, onRefresh }) {
  const [expanded, setExpanded] = useState(false);
  const items = expanded ? model.attention : model.attention.slice(0, 3);
  const news = model.announcements.filter(item => !model.attention.some(action => action.id === `announcement-${item.id}`)).slice(0, 2);
  return <div className="page-container focus-dashboard employee-day">
    <header className="day-topline"><span><span className="day-live-dot" /> YOUR SPACE</span><time dateTime={format(new Date(), 'yyyy-MM-dd')}>{format(new Date(), 'EEEE, MMMM d')}</time></header>
    <section className="day-welcome" aria-label="Welcome">
      <div className="day-welcome-copy"><p className="day-greeting">Good to see you, {user?.firstName || 'there'}.</p><h1>A little focus.<br /><em>A great day.</em></h1><p className="day-intro">Make space for your best work. We’ll keep the little things together.</p>
        <div className="day-welcome-actions"><Link className="button day-primary" to="/timesheets">Log your time <Icon name="arrow" size={16} /></Link><Link className="day-secondary" to="/vacation">Plan a little time off <Icon name="arrow" size={15} /></Link></div>
      </div>
      <TimeSculpture />
    </section>
    <section className="day-pulse" aria-label="Workspace summary" aria-busy={state.loading}>
      {model.metrics.filter(metric => metric.label !== 'Pending Actions').map(metric => <Link to={metric.to} key={metric.label}>
        <strong>{state.loading || metric.value == null ? '—' : metric.value}</strong><span>{metric.label}<small>{state.loading ? 'Loading…' : metric.value == null ? 'Unavailable' : metric.label === 'Time Off Balance' ? 'Vacation remaining' : metric.label === 'Hours This Week' ? 'Monday through today' : 'Your current work'}</small></span><Icon name="arrow" size={14} />
      </Link>)}
    </section>
    {state.errors.length > 0 && <p className="dashboard-error" role="alert">Some updates couldn’t load. <button onClick={onRefresh} type="button">Retry</button></p>}
    {/* Temporarily disabled: Needs Attention. Restore when work resumes.
    <section className="day-attention" id="needs-attention" aria-labelledby="day-attention-title">
      <div className="day-section-title"><h2 id="day-attention-title">Needs Attention {model.attention.length > 0 && !state.loading && <span>{model.actionCount}</span>}</h2><span>{state.loading ? 'Getting things ready' : model.attention.length ? 'A few small next steps' : 'Room to breathe'}</span></div>
      {state.loading ? <LoadingIndicator label="Loading your dashboard…" /> : items.length ? <ul className="dashboard-rows">{items.map(item => <ActionRow item={item} key={item.id} />)}</ul> : <p className="day-all-clear"><Icon name="check" size={20} />{state.errors.length ? 'Refresh to confirm whether anything needs attention.' : 'You’re all caught up. Enjoy a little breathing room.'}</p>}
      {model.attention.length > 3 && <button className="dashboard-more" type="button" aria-expanded={expanded} onClick={() => setExpanded(value => !value)}>{expanded ? 'Show less' : `See ${model.attention.length - 3} more`}</button>}
    </section>
    */}
    {!state.loading && <div className="day-bottom-grid">
      {model.projects.length > 0 && <section className="day-projects" aria-labelledby="day-projects-title"><div className="day-section-title"><h2 id="day-projects-title">My Projects</h2><Link to="/timesheets">View all <Icon name="arrow" size={14} /></Link></div>
        <ul className="day-project-list">{model.projects.slice(0, 3).map((project, index) => <li key={project.id}><Link to={project.to}>
          <span className="day-project-symbol" aria-hidden="true">{String(index + 1).padStart(2, '0')}</span><span><strong>{project.name}</strong><small>{project.code} · {displayNumber(project.logged)} / {displayNumber(project.planned)} hrs logged / planned</small></span><Icon name="arrow" size={15} />
        </Link></li>)}</ul>
      </section>}
      {news.length > 0 && <section className="day-news" aria-label="Company announcements"><div className="day-section-title"><h2>Announcements</h2><Link to="/announcements">View all <Icon name="arrow" size={14} /></Link></div><ul>{news.map(item => <li key={item.id}><Link to={`/announcements?id=${encodeURIComponent(item.id)}`}><span className="day-news-kicker">{item.priority === 'URGENT' ? 'Important update' : 'Around Maxwell'}{!item.viewed_at && <i title="Unread" />}</span><strong>{item.title}</strong><span className="day-news-read">Take a look <Icon name="arrow" size={15} /></span></Link></li>)}</ul></section>}
    </div>}
  </div>;
}
