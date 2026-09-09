import React, { useState, useEffect } from 'react';
import { notificationAPI } from '../api';
import { format } from 'date-fns';
import { LoadingIndicator } from '../components/Hourglass';
import '../styles.css';

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
      const hasUnread = rows.some((notification) => !notification.isRead);
      if (hasUnread) {
        await notificationAPI.markAllAsRead();
      }
      setNotifications(rows.map((notification) => ({ ...notification, isRead: true })));
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
        <h1>Notifications</h1>
        {unreadCount > 0 && (
          <button className="button button-secondary" onClick={handleMarkAllAsRead}>
            Mark All as Read
          </button>
        )}
      </div>

      {error && <div className="error-message">{error}</div>}

      {notifications.length === 0 ? (
        <div className="empty-state">
          <p>No notifications yet.</p>
        </div>
      ) : (
        <div className="table-container">
          <table className="data-table">
            <thead>
              <tr>
                <th>Title</th>
                <th>Message</th>
                <th>Received</th>
                <th>Status</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {notifications.map((n) => (
                <tr key={n.id}>
                  <td>{n.title}</td>
                  <td>{n.message}</td>
                  <td>{format(new Date(n.createdAt), 'MMM dd, yyyy HH:mm')}</td>
                  <td>
                    <span className={`status-badge ${n.isRead ? 'status-approved' : 'status-submitted'}`}>
                      {n.isRead ? 'Read' : 'Unread'}
                    </span>
                  </td>
                  <td>
                    {!n.isRead && (
                      <button className="button button-small" onClick={() => handleMarkAsRead(n.id)}>
                        Mark Read
                      </button>
                    )}
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
