import React, { useEffect, useState } from 'react';
import { projectAPI } from '../api';
import './ProjectHealth.css';

const labels = { HEALTHY: 'Healthy', ATTENTION_NEEDED: 'Attention Needed', AT_RISK: 'At Risk' };
const number = (value) => value == null ? 'Not set' : Number(value).toLocaleString(undefined, { maximumFractionDigits: 1 });

export function useProjectHealth(enabled = true, revision = null) {
  const [state, setState] = useState({ projects: [], loading: true, error: '' });
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    if (!enabled) return;
    let alive = true;
    const load = async () => {
      setState({ projects: [], loading: true, error: '' });
      try {
        const response = await projectAPI.getHealth();
        if (!Array.isArray(response?.data)) throw new Error('Invalid health response');
        if (alive) setState({ projects: response.data, loading: false, error: '' });
      } catch {
        if (alive) setState({ projects: [], loading: false, error: 'Project health could not be loaded.' });
      }
    };
    load();
    const refresh = () => setAttempt(value => value + 1);
    window.addEventListener('focus', refresh);
    return () => { alive = false; window.removeEventListener('focus', refresh); };
  }, [enabled, revision, attempt]);
  return { ...state, refresh: () => setAttempt(value => value + 1) };
}

export function HealthBadge({ status }) {
  return <span className={`ph-badge ph-${status?.toLowerCase()}`}><span aria-hidden="true" />{labels[status] || 'Unavailable'}</span>;
}

function HealthState({ state }) {
  return state.loading ? <p role="status" className="ph-muted">Evaluating project health…</p>
    : state.error ? <p role="alert">{state.error} <button className="button button-small button-secondary" type="button" onClick={state.refresh}>Retry health</button></p> : null;
}

export function ProjectHealthCard({ health, state }) {
  return <section className="ph-card" aria-label="Project Health">
    <div className="ph-heading"><div><h3>Project Health</h3><p>Lifetime hours · current delivery outlook</p></div>{health && <HealthBadge status={health.status} />}</div>
    <HealthState state={state} />
    {!state.loading && !state.error && !health && <p className="ph-muted">Health data is unavailable for this project.</p>}
    {health && <>
      <div className="ph-metrics">
        <div><span>Logged / allocated</span><strong>{number(health.loggedHours)} / {number(health.allocatedHours)} <small>hrs</small></strong></div>
        <div><span>Remaining allocation</span><strong>{number(health.remainingHours)} <small>hrs</small></strong></div>
        <div><span>Resource plans</span><strong>{number(health.plannedHours)} <small>hrs</small></strong></div>
        <div><span>Resources today</span><strong>{health.activeResources}</strong></div>
      </div>
      {health.hoursUtilization != null && <div className="ph-utilization"><div><span>Hour budget utilization</span><strong>{number(health.hoursUtilization)}%</strong></div><progress aria-label="Hour budget utilization" max="100" value={Math.min(100, Math.max(0, health.hoursUtilization))} aria-valuetext={`${number(health.hoursUtilization)}% used`} /></div>}
      {health.signals.length ? <ul className="ph-signals">{health.signals.map(signal => <li className={`ph-signal ph-${signal.severity.toLowerCase()}`} key={signal.code}><span aria-hidden="true" />{signal.message}</li>)}</ul>
        : <p className="ph-clear">{health.monitored ? 'No issues detected in the available data.' : 'Delivery monitoring is paused. No outstanding timesheet issues detected.'}</p>}
      <details className="ph-method"><summary>Coverage and calculation details</summary><ul>{health.coverageNotes.map(note => <li key={note}>{note}</li>)}</ul><p>Attention begins at 80% of the hour budget; 95% or an overrun is at risk. An assignment window ending within 14 days needs attention, or within 7 days is at risk. Spending at least 50% of hours and running 20 percentage points ahead of the assignment timeline also raises a flag.</p><p>Evaluated {health.evaluatedOn}. Health describes recorded indicators, not a guarantee of delivery.</p></details>
    </>}
  </section>;
}

export function ProjectHealthOverview({ state, onOpen }) {
  const flagged = state.projects.filter(project => project.status !== 'HEALTHY');
  return <section className="ph-card ph-overview" aria-label="Project Health overview">
    <div className="ph-heading"><div><h2>Project Health</h2><p>Projects requiring attention · evaluated from current data</p></div><button type="button" className="button button-small button-secondary" disabled={state.loading} onClick={state.refresh}>Refresh health</button></div>
    <HealthState state={state} />
    {!state.loading && !state.error && <>
      <div className="ph-counts">{Object.entries(labels).map(([key, label]) => <span key={key}>{label} <strong>{state.projects.filter(p => p.status === key).length}</strong></span>)}</div>
      {!state.projects.length ? <p className="ph-muted">No projects to evaluate.</p> : !flagged.length ? <p className="ph-clear">No projects currently require attention based on available data.</p>
        : <div className="ph-project-list">{flagged.map(project => <button className="ph-project-row" type="button" key={project.projectId} onClick={() => onOpen(project.projectId)}>
          <span><strong>{project.projectCode} · {project.projectName}</strong><span className="ph-row-reason">{project.signals[0]?.message}{project.signals.length > 1 ? ` · +${project.signals.length - 1} more` : ''}</span></span><HealthBadge status={project.status} />
        </button>)}</div>}
    </>}
  </section>;
}
