import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { CompletionToastViewport } from '../../../components/notifications/CompletionToast';
import LayoutSearchCompletionNotifier from '../../layoutSearch/components/LayoutSearchCompletionNotifier';
import AiReportCompletionNotifier from '../../reports/components/AiReportCompletionNotifier';
import SimulationCompletionNotifier from '../../simulations/components/SimulationCompletionNotifier';
import { useAuth } from '../context/AuthContext';
import { can } from '../capabilities';
import { CanvasWorkspaceState } from '../../../components/workspace';
import {
  DRAWING_WORKSPACE_LOADING_MESSAGE,
  SIMULATION_RESULT_LOADING_MESSAGE,
  SIMULATION_SETUP_LOADING_MESSAGE,
} from '../../../components/workspace/workspaceLoadingMessages';

function getProtectedRouteLoadingMessage(pathname: string): string | null {
  if (/^\/layout\/[^/]+\/?$/.test(pathname)) return DRAWING_WORKSPACE_LOADING_MESSAGE;
  if (/^\/simulations\/[^/]+\/setup\/?$/.test(pathname)) {
    return SIMULATION_SETUP_LOADING_MESSAGE;
  }
  if (/^\/simulations\/[^/]+\/results\/?$/.test(pathname)) {
    return SIMULATION_RESULT_LOADING_MESSAGE;
  }
  return null;
}

function ProtectedRoute() {
  const { user, isInitializing } = useAuth();
  const location = useLocation();

  if (isInitializing) {
    const workspaceMessage = getProtectedRouteLoadingMessage(location.pathname);
    if (workspaceMessage) {
      return <CanvasWorkspaceState message={workspaceMessage} role="status" />;
    }
    return (
      <div className="flex min-h-[100dvh] items-center justify-center text-sm text-ink/50">
        불러오는 중...
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  // 완료 알림은 그 업무를 볼 수 있는 사용자에게만 붙인다. 일반 직원 화면에서 3초 폴링이 돌 이유가 없다.
  return (
    <>
      <CompletionToastViewport>
        {can(user.roles, 'simulations') && <SimulationCompletionNotifier userId={user.id} />}
        {can(user.roles, 'simulations') && <LayoutSearchCompletionNotifier />}
        {can(user.roles, 'reports') && <AiReportCompletionNotifier />}
      </CompletionToastViewport>
      <Outlet />
    </>
  );
}

export default ProtectedRoute;
