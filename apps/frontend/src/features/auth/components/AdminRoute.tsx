import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

function AdminRoute() {
  const { user } = useAuth();

  if (!user?.roles.includes('ADMIN')) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}

export default AdminRoute;
