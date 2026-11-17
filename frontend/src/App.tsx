import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from './auth';
import { Layout } from './components/Layout';
import { ApplicationPage } from './pages/ApplicationPage';
import { LoginPage } from './pages/LoginPage';
import { NewApplicationPage } from './pages/NewApplicationPage';
import { OverviewPage } from './pages/OverviewPage';
import { QueuePage } from './pages/QueuePage';

function RequireLogin() {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <p className="muted center">Checking your session...</p>;
  if (!user) return <Navigate to="/login" state={{ from: location.pathname + location.search }} replace />;
  return <Layout />;
}

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireLogin />}>
        <Route path="/" element={<OverviewPage />} />
        <Route path="/applications" element={<QueuePage />} />
        <Route path="/applications/new" element={<NewApplicationPage />} />
        <Route path="/applications/:id" element={<ApplicationPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
