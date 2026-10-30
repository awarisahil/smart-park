import { useCallback, useEffect, useRef, useState } from 'react';
import api from '../api/client.js';
import { Icons } from './ui.jsx';
import { formatTime } from '../utils/format.js';

const ICON_FOR = { INFO: 'info', SUCCESS: 'check', WARNING: 'alert' };

export default function NotificationBell() {
  const [summary, setSummary] = useState({ unread: 0, items: [] });
  const [open, setOpen] = useState(false);
  const panelRef = useRef(null);

  const load = useCallback(async () => {
    try {
      const response = await api.get('/notifications/my');
      setSummary(response.data);
    } catch {
      /* session may have expired; interceptor handles redirect */
    }
  }, []);

  useEffect(() => {
    load();
    const id = setInterval(load, 30000);
    return () => clearInterval(id);
  }, [load]);

  useEffect(() => {
    if (!open) return;
    const onClick = (event) => {
      if (panelRef.current && !panelRef.current.contains(event.target)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, [open]);

  const markAllRead = async () => {
    const response = await api.post('/notifications/read-all');
    setSummary(response.data);
  };

  return (
    <div className="bell-wrap" ref={panelRef}>
      <button
        className={`bell-btn ${open ? 'open' : ''}`}
        onClick={() => setOpen((value) => !value)}
        aria-label="Notifications"
      >
        <Icons.bell size={17} />
        {summary.unread > 0 && <span className="bell-badge">{summary.unread}</span>}
      </button>

      {open && (
        <div className="notif-panel">
          <div className="notif-head">
            <span>Notifications</span>
            {summary.unread > 0 && <button onClick={markAllRead}>Mark all read</button>}
          </div>
          {summary.items.length === 0 ? (
            <div className="notif-empty">You're all caught up.</div>
          ) : (
            summary.items.map((item) => {
              const Icon = Icons[ICON_FOR[item.type] || 'info'];
              return (
                <div key={item.id} className={`notif-item ${item.read ? '' : 'unread'}`}>
                  <span className={`n-icon ${item.type}`}>
                    <Icon size={14} />
                  </span>
                  <div>
                    {item.message}
                    <span className="n-time">{formatTime(item.createdAt)}</span>
                  </div>
                </div>
              );
            })
          )}
        </div>
      )}
    </div>
  );
}
