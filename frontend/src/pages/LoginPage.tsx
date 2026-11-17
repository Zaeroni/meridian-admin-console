import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import { ErrorBox } from '../components/ErrorBox';

const DEMO_ACCOUNTS = [
  { label: 'Operations lead', email: 'lead@meridian.test' },
  { label: 'Reviewer', email: 'reviewer@meridian.test' },
  { label: 'Read-only', email: 'readonly@meridian.test' },
];

export function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  const from = (location.state as { from?: string } | null)?.from ?? '/';
  if (user) return <Navigate to={from} replace />;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email, password);
      navigate(from, { replace: true });
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login-wrap">
      <form className="card login-card" onSubmit={submit}>
        <h1>Meridian Admin Console</h1>
        <p className="muted">Sign in to review pharmacy applications and monitor the platform.</p>
        <label>
          Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="username" required />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </label>
        <ErrorBox error={error} />
        <button className="btn btn-primary" disabled={busy}>
          {busy ? 'Signing in...' : 'Sign in'}
        </button>
        <div className="demo-box">
          <strong>Demo accounts</strong> (synthetic data, password <code>Password123!</code>)
          <ul>
            {DEMO_ACCOUNTS.map((a) => (
              <li key={a.email}>
                <button
                  type="button"
                  className="link-button"
                  onClick={() => {
                    setEmail(a.email);
                    setPassword('Password123!');
                  }}
                >
                  {a.label}
                </button>{' '}
                <span className="muted">{a.email}</span>
              </li>
            ))}
          </ul>
        </div>
      </form>
    </div>
  );
}
