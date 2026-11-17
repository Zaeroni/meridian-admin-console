import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import { ROLE_LABEL } from '../format';

export function Layout() {
  const { user, logout, canWrite } = useAuth();
  const navigate = useNavigate();

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true" />
          Meridian <span className="brand-sub">Admin Console</span>
        </div>
        <nav aria-label="Main">
          <NavLink to="/" end>
            Overview
          </NavLink>
          <NavLink to="/applications" end>
            Review queue
          </NavLink>
          {canWrite && <NavLink to="/applications/new">New application</NavLink>}
        </nav>
        <div className="who">
          <span>
            {user?.fullName} <span className="role-pill">{user ? ROLE_LABEL[user.role] : ''}</span>
          </span>
          <button
            className="btn btn-quiet"
            onClick={() => {
              logout();
              navigate('/login');
            }}
          >
            Log out
          </button>
        </div>
      </header>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
