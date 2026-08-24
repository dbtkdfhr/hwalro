import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { CompletionToastViewport } from '../../../components/notifications/CompletionToast';
import AiReportCompletionNotifier from '../../reports/components/AiReportCompletionNotifier';
import SimulationCompletionNotifier from '../../simulations/components/SimulationCompletionNotifier';
import { useAuth } from '../context/AuthContext';

function ProtectedRoute() {
  const { user, isInitializing } = useAuth();
  const location = useLocation();

  if (isInitializing) {
    return (
      <div className="flex min-h-[100dvh] items-center justify-center bg-background px-4">
        <p className="rounded-xl border border-line bg-surface-raised px-5 py-3 text-sm font-bold text-text-muted shadow-neu-raised">
          불러오는 중...
        </p>
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return (
    <>
      <CompletionToastViewport>
        <SimulationCompletionNotifier userId={user.id} />
        <AiReportCompletionNotifier />
      </CompletionToastViewport>
      <Outlet />
    </>
  );
}

export default ProtectedRoute;
