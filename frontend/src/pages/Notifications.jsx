import ScreenTitle, { RecordSummary } from '../components/ScreenTitle';
import React, { useState, useEffect } from 'react';
import { notificationAPI } from '../api';
import { format } from 'date-fns';
import { LoadingIndicator } from '../components/Hourglass';
import '../styles.css';
import Icon from '../components/Icon';

function notificationIcon(notification) {
  const haystack = [
    notification.notificationType,
    notification.entityType,
    notification.title,
    notification.message,
  ].filter(Boolean).join(' ').toLowerCase();

  if (haystack.includes('vacation') || haystack.includes('time off') || haystack.includes('leave')) return 'calendar';
  if (haystack.includes('timesheet') || haystack.includes('time sheet') || haystack.includes('hours')) return 'clock';
  if (haystack.includes('project')) return 'briefcase';
  if (haystack.includes('letter') || haystack.includes('employment')) return 'file';
  return notification.isRead ? 'file' : 'bell';
}

export default function Notifications() {
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    loadNotifications();
  }, []);

  const loadNotifications = async () => {
    try {
      const response = await notificationAPI.getNotifications();
      const rows = response.data || [];
      setNotifications(rows);
      window.dispatchEvent(new Event("chronos:notifications-changed"));
      setError('');
    } catch (err) {
      setError('Failed to load notifications');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleMarkAsRead = async (id) => {
    try {
      await notificationAPI.markAsRead(id);
      await loadNotifications();
    } catch (err) {
      setError('Failed to mark notification as read');
    }
  };

  const handleMarkAllAsRead = async () => {
    try {
      await notificationAPI.markAllAsRead();
      await loadNotifications();
    } catch (err) {
      setError('Failed to mark all notifications as read');
    }
  };

  if (loading) {
    return <div className="page-container"><div className="loading-panel"><LoadingIndicator label="Loading notifications..." /></div></div>;
  }

  const unreadCount = notifications.filter((n) => !n.isRead).length;

  return (
    <div className="page-container">
      <div className="header-bar">
        <ScreenTitle title="Notifications" icon="bell" eyebrow="YOUR INBOX" />
        {unreadCount > 0 && (
          <button className="button button-secondary" onClick={handleMarkAllAsRead}>
            Mark All as Read
          </button>
        )}
      </div>

      <RecordSummary items={[{ label: "All messages", value: notifications.length, icon: "file" }, { label: "Unread updates", value: unreadCount, icon: "bell" }]} />
      {error && <div className="error-message" role="alert">{error}</div>}

      {notifications.length === 0 ? (
        <div className="empty-state">
          <p>No notifications yet.</p>
        </div>
      ) : (
        <div className="inbox-list">
          {notifications.map(n => <article className={'inbox-item' + (n.isRead ? '' : ' unread')} key={n.id}>
            <span className="inbox-item-icon"><Icon name={notificationIcon(n)} size={21}/></span>
            <div className="inbox-item-content"><div className="inbox-item-heading"><h2>{n.title}</h2><span className={'status-badge ' + (n.isRead ? 'status-draft' : 'status-submitted')}>{n.isRead ? 'Read' : 'Unread'}</span></div><p>{n.message}</p><time dateTime={n.createdAt}>{format(new Date(n.createdAt), 'MMM d, yyyy • h:mm a')}</time></div>
            {!n.isRead && <button className="button button-small button-secondary" onClick={() => handleMarkAsRead(n.id)}>Mark read</button>}
          </article>)}
        </div>
      )}
    </div>
  );
}
