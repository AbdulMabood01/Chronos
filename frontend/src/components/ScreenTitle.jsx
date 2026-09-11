import React from 'react';
import Icon from './Icon';

export default function ScreenTitle({ title, icon = 'file', eyebrow = 'Workspace', description }) {
  return <div className="screen-title"><span className="screen-title-icon"><Icon name={icon} size={25}/></span><div><span className="eyebrow">{eyebrow}</span><h1>{title}</h1>{description && <p className="page-subtitle">{description}</p>}</div></div>;
}
export function RecordSummary({ items }) {
  return <section className="record-summary" aria-label="Record summary">{items.map(({ label, value, icon = 'file' }) => <div key={label}><span className="record-summary-icon"><Icon name={icon}/></span><span><small>{label}</small><strong>{value}</strong></span></div>)}</section>;
}
export function RecordSearch({ value, onChange, placeholder = 'Search records', label = 'Search records' }) {
  return <div className="record-search"><Icon name="search"/><input aria-label={label} type="search" placeholder={placeholder} value={value} onChange={event => onChange(event.target.value)}/></div>;
}
