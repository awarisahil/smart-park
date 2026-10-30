import { useEffect, useState } from 'react';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import api from '../api/client.js';

/**
 * Live slot board: initial load via REST, then live updates pushed over
 * WebSocket (STOMP topic /topic/slots broadcast by the backend).
 */
export function useSlots() {
  const [board, setBoard] = useState(null);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    api.get('/slots').then((response) => setBoard(response.data)).catch(() => {});

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 4000,
      onConnect: () => {
        setConnected(true);
        client.subscribe('/topic/slots', (message) => {
          setBoard(JSON.parse(message.body));
        });
      },
      onWebSocketClose: () => setConnected(false),
    });
    client.activate();

    return () => client.deactivate();
  }, []);

  return { board, connected };
}
