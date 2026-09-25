import React from 'react';
import './WorkflowFeatures.css';
export default function ProjectBudget({ budget, logged }) {
  const planned = Number(budget), used = Number(logged || 0);
  if (budget == null || planned <= 0) return null;
  const percent = used / planned * 100;
  const status = percent >= 100 ? 'Budget reached' : percent >= 80 ? 'Approaching budget' : 'Within budget';
  return <section className={'budget-panel budget-' + (percent >= 100 ? 'over' : percent >= 80 ? 'near' : 'ok')} aria-label="Project budget">
    <div className="budget-heading"><strong>{status}</strong><span>{percent.toFixed(1)}% used</span></div>
    <progress aria-label="Project hours budget used" max={planned} value={Math.min(used, planned)} />
    <p>{used.toFixed(2)} of {planned.toFixed(2)} hours recorded across all months. {used > planned ? (used - planned).toFixed(2) + ' hours over budget.' : (planned - used).toFixed(2) + ' hours remaining.'}</p>
    <small>Includes draft, submitted, rejected and approved hours. Warning starts at 80%.</small>
  </section>;
}
