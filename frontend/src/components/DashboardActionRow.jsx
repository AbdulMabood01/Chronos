import React from 'react';
import { Link } from 'react-router-dom';
import Icon from './Icon';

export default function DashboardActionRow({ item }) {
  return <li><Link className="dashboard-row" to={item.to}>
    <span className={`dashboard-row-icon ${item.urgent ? 'dashboard-urgent' : ''}`}><Icon name={item.icon || 'file'} size={18} /></span>
    <span className="dashboard-row-copy"><strong>{item.title}</strong><small>{item.detail}</small></span>
    {item.count > 1 && <span className="dashboard-count">{item.count}</span>}
    <Icon name="arrow" size={16} />
  </Link></li>;
}
