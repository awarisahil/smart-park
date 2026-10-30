import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import { Icons } from '../components/ui.jsx';

const DEMO_ACCOUNTS = [
  { label: 'Admin', email: 'admin@parking.com', password: 'admin123' },
  { label: 'Attendant', email: 'attendant@parking.com', password: 'attendant123' },
  { label: 'User', email: 'user@parking.com', password: 'user123' },
];

export function BrandPanel() {
  return (
    <div className="auth-brand">
      <h2>Parking that runs itself.</h2>
      <p>
        Real-time slot tracking, automated allocation, and gate-ready entry codes —
        all in one live dashboard.
      </p>
      <ul className="feature-list">
        <li>
          <span className="feat-icon"><Icons.zap size={16} /></span>
          Automated slot allocation with live availability
        </li>
        <li>
          <span className="feat-icon"><Icons.ticket size={16} /></span>
          Entry codes with auto-expiring hold windows
        </li>
        <li>
          <span className="feat-icon"><Icons.trending size={16} /></span>
          Revenue and occupancy analytics for admins
        </li>
      </ul>
    </div>
  );
}

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      await login(email, password);
      navigate('/');
    } catch (err) {
      setError(err.response?.data?.error || 'Login failed. Please try again.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth-wrap">
      <BrandPanel />
      <form className="card auth-card" onSubmit={handleSubmit}>
        <h1>Welcome back</h1>
        <p className="muted">Sign in to your parking account.</p>
        {error && <div className="alert alert-error">{error}</div>}
        <label>
          Email
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoComplete="email"
            placeholder="you@example.com"
          />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            autoComplete="current-password"
            placeholder="••••••••"
          />
        </label>
        <button className="btn btn-primary btn-block" type="submit" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
        <p className="muted" style={{ marginBottom: 0 }}>
          No account yet? <Link to="/register">Create one</Link>
        </p>
        <div className="demo-chips">
          <span className="code-label" style={{ textAlign: 'left' }}>Demo accounts — tap to autofill</span>
          {DEMO_ACCOUNTS.map((account) => (
            <button
              key={account.label}
              type="button"
              className="demo-chip"
              onClick={() => {
                setEmail(account.email);
                setPassword(account.password);
                setError('');
              }}
            >
              <strong>{account.label}</strong>
              <span>{account.email}</span>
            </button>
          ))}
        </div>
      </form>
    </div>
  );
}
