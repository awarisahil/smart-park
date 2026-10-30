import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import NotificationBell from './NotificationBell.jsx';

export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <nav className="navbar">
      <div className="navbar-inner">
        <Link to="/" className="brand">
          <span className="brand-mark">P</span>
          Smart<span className="brand-accent">Park</span>
        </Link>
        {user && (
          <div className="nav-links">
            <NavLink to="/" end>Live Board</NavLink>
            {(user.role === 'USER' || user.role === 'ADMIN') && (
              <NavLink to="/my-reservations">My Reservations</NavLink>
            )}
            {(user.role === 'ATTENDANT' || user.role === 'ADMIN') && (
              <NavLink to="/attendant">Attendant</NavLink>
            )}
            {user.role === 'ADMIN' && <NavLink to="/admin">Admin</NavLink>}
            <NotificationBell />
            <span className="user-chip">
              {user.name}
              <span className={`role-badge role-${user.role}`}>{user.role}</span>
            </span>
            <button className="btn btn-ghost btn-sm" onClick={handleLogout}>
              Logout
            </button>
          </div>
        )}
      </div>
    </nav>
  );
}
