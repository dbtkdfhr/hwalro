import { Navigate, Outlet } from 'react-router-dom';
import { can, homeRouteFor, type Capability } from '../capabilities';
import { useAuth } from '../context/AuthContext';

interface CapabilityRouteProps {
  capability: Capability;
}

/** 화면 권한이 없으면 그 사용자의 홈으로 돌려보낸다. 실제 차단은 서버가 한다. */
function CapabilityRoute({ capability }: CapabilityRouteProps) {
  const { user } = useAuth();

  if (!can(user?.roles, capability)) {
    return <Navigate to={homeRouteFor(user?.roles)} replace />;
  }

  return <Outlet />;
}

export default CapabilityRoute;
