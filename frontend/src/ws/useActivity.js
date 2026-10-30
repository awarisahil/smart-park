import { useEffect, useState } from 'react';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import api from '../api/client.js';

/**
 * Live facility activity: catch-up load via REST, then real-time pushes on
 * /topic/activity. Newest first, capped at 12 entries per client.
 */
export function useActivity() {
  const [events, setEvents] = useState([]);

  useEffect(() => {
    api.get('/activity').then((response) => {
      setEvents(response.data.slice(0, 12));
    }).catch(() => {});

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe('/topic/activity', (message) => {
          const event = JSON.parse(message.body);
          setEvents((current) => [event, ...current].slice(0, 12));
        });
      },
    });
    client.activate();
    return () => client.deactivate();
  }, []);

  return events;
}
