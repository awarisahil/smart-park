import { useEffect, useState } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import Navbar from './components/Navbar.jsx';
import { ProtectedRoute, RoleRoute } from './components/ProtectedRoute.jsx';
import { ToastProvider, useToast } from './components/Toast.jsx';
import { Icons } from './components/ui.jsx';
import Login from './pages/Login.jsx';
import Register from './pages/Register.jsx';
import SlotBoard from './pages/SlotBoard.jsx';
import MyReservations from './pages/MyReservations.jsx';
import AttendantPanel from './pages/AttendantPanel.jsx';
import AdminDashboard from './pages/AdminDashboard.jsx';

function AnnouncementListener() {
  const toast = useToast();
  const [announcement, setAnnouncement] = useState(null);

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe('/topic/announcements', (message) => {
          const payload = JSON.parse(message.body);
          setAnnouncement(payload.message);
          toast('success', `Announcement: ${payload.message}`);
        });
      },
    });
    client.activate();
    return () => client.deactivate();
  }, [toast]);

  if (!announcement) return null;

  return (
    <div className="announce-banner">
      <Icons.megaphone size={16} />
      <span>{announcement}</span>
      <button onClick={() => setAnnouncement(null)} aria-label="Dismiss announcement">
        ×
      </button>
    </div>
  );
}

function AppShell() {
  const location = useLocation();

  return (
    <>
      <Navbar />
      <main className="container">
        <AnnouncementListener />
        <div className="page" key={location.pathname}>
          <Routes location={location}>
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            <Route path="/" element={<ProtectedRoute><SlotBoard /></ProtectedRoute>} />
            <Route
              path="/my-reservations"
              element={
                <RoleRoute roles={['USER', 'ADMIN']}>
                  <MyReservations />
                </RoleRoute>
              }
            />
            <Route
              path="/attendant"
              element={
                <RoleRoute roles={['ATTENDANT', 'ADMIN']}>
                  <AttendantPanel />
                </RoleRoute>
              }
            />
            <Route
              path="/admin"
              element={
                <RoleRoute roles={['ADMIN']}>
                  <AdminDashboard />
                </RoleRoute>
              }
            />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </div>
      </main>
    </>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <AppShell />
      </ToastProvider>
    </BrowserRouter>
  );
}
