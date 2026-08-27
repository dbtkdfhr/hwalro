import { lazy, Suspense } from 'react';
import { createBrowserRouter } from 'react-router-dom';
import { Navigate, useParams } from 'react-router-dom';
import App from '../App';
import WorkspaceLayout from '../layouts/WorkspaceLayout';
import HomePage from '../pages/HomePage';
import LoginPage from '../pages/LoginPage';
import SystemManagementPage from '../pages/SystemManagementPage';
import CapabilityRoute from '../features/auth/components/CapabilityRoute';
import ProtectedRoute from '../features/auth/components/ProtectedRoute';
import { can, homeRouteFor } from '../features/auth/capabilities';
import { useAuth } from '../features/auth/context/AuthContext';
import RegulationsPage from '../pages/RegulationsPage';
import RiskManagementPage from '../pages/RiskManagementPage';
import SafetyCheckAreasPage from '../pages/SafetyCheckAreasPage';
import SafetyCheckHistoryPage from '../pages/SafetyCheckHistoryPage';
import SafetyCheckDetailPage from '../pages/SafetyCheckDetailPage';
import SafetyCheckTemplatePage from '../pages/SafetyCheckTemplatePage';
import ReportListPage from '../features/reports/pages/ReportListPage';
import ReportDetailPage from '../features/reports/pages/ReportDetailPage';
import DrawingListPage from '../features/drawings/pages/DrawingListPage';
import SimulationSetupPage from '../features/simulations/pages/SimulationSetupPage';
import SimulationListPage from '../features/simulations/pages/SimulationListPage';
import { CanvasWorkspaceState } from '../components/workspace';
import {
  DRAWING_WORKSPACE_LOADING_MESSAGE,
  SIMULATION_RESULT_LOADING_MESSAGE,
} from '../components/workspace/workspaceLoadingMessages';

const LayoutPage = lazy(() => import('../features/layout/pages/LayoutPage'));
const CreateDrawingPage = lazy(() => import('../features/drawings/pages/CreateDrawingPage'));
const MyZonesPage = lazy(() => import('../features/zones/pages/MyZonesPage'));
const EvacuationPage = lazy(() => import('../features/zones/pages/EvacuationPage'));
const SimulationAnalysisResultPage = lazy(
  () => import('../features/simulationResult/pages/SimulationResultPage'),
);
const LayoutSearchPage = lazy(() => import('../features/layoutSearch/pages/LayoutSearchPage'));
const InspectionMobilePage = lazy(() => import('../pages/InspectionMobilePage'));

function FullscreenRouteFallback({ message }: { message: string }) {
  return <CanvasWorkspaceState message={message} role="status" />;
}

/** 업무 대시보드를 볼 수 없는 사용자는 홈 대신 담당 구역 화면을 본다. */
function HomeOrMyZones() {
  const { user } = useAuth();
  return can(user?.roles, 'simulations') ? (
    <HomePage />
  ) : (
    <Navigate to={homeRouteFor(user?.roles)} replace />
  );
}

function DrawingEditRedirect() {
  const { drawingId } = useParams();
  return <Navigate to={`/layout/${drawingId}`} replace />;
}

export const router = createBrowserRouter([
  {
    path: '/',
    element: <App />,
    children: [
      {
        element: <ProtectedRoute />,
        children: [
          {
            element: <WorkspaceLayout />,
            children: [
              {
                index: true,
                element: <HomeOrMyZones />,
              },
              {
                element: <CapabilityRoute capability="risks" />,
                children: [{ path: 'risk-management', element: <RiskManagementPage /> }],
              },
              {
                element: <CapabilityRoute capability="reports" />,
                children: [
                  { path: 'reports', element: <ReportListPage /> },
                  { path: 'reports/:reportId', element: <ReportDetailPage /> },
                ],
              },
              {
                element: <CapabilityRoute capability="checklists" />,
                children: [
                  { path: 'safety-checklists', element: <SafetyCheckAreasPage /> },
                  {
                    path: 'safety-checklists/areas/:areaId',
                    element: <SafetyCheckHistoryPage />,
                  },
                  {
                    path: 'safety-checklists/inspections/:inspectionId',
                    element: <SafetyCheckDetailPage />,
                  },
                ],
              },
              {
                element: <CapabilityRoute capability="checklists.manage" />,
                children: [
                  {
                    path: 'safety-checklists/areas/:areaId/template',
                    element: <SafetyCheckTemplatePage />,
                  },
                ],
              },
              {
                element: <CapabilityRoute capability="drawings.view" />,
                children: [
                  { path: 'drawings', element: <DrawingListPage /> },
                  { path: 'drawings/:drawingId', element: <DrawingEditRedirect /> },
                ],
              },
              {
                element: <CapabilityRoute capability="simulations" />,
                children: [{ path: 'simulations', element: <SimulationListPage /> }],
              },
              {
                element: <CapabilityRoute capability="regulations" />,
                children: [{ path: 'regulations', element: <RegulationsPage /> }],
              },
              {
                element: <CapabilityRoute capability="zones.assigned" />,
                children: [
                  {
                    path: 'my-zones',
                    element: (
                      <Suspense
                        fallback={
                          <FullscreenRouteFallback message="담당 구역을 준비하고 있습니다." />
                        }
                      >
                        <MyZonesPage />
                      </Suspense>
                    ),
                  },
                  {
                    path: 'my-zones/:zoneId/evacuation',
                    element: (
                      <Suspense
                        fallback={
                          <FullscreenRouteFallback message="대피 안내를 준비하고 있습니다." />
                        }
                      >
                        <EvacuationPage />
                      </Suspense>
                    ),
                  },
                ],
              },
              {
                element: <CapabilityRoute capability="systemManagement" />,
                children: [{ path: 'system-management', element: <SystemManagementPage /> }],
              },
            ],
          },
          {
            element: <CapabilityRoute capability="drawings.manage" />,
            children: [
              {
                path: 'drawings/new',
                element: (
                  <Suspense
                    fallback={
                      <FullscreenRouteFallback message={DRAWING_WORKSPACE_LOADING_MESSAGE} />
                    }
                  >
                    <CreateDrawingPage />
                  </Suspense>
                ),
              },
            ],
          },
          {
            element: <CapabilityRoute capability="drawings.view" />,
            children: [
              {
                path: 'layout/:drawingId',
                element: (
                  <Suspense
                    fallback={
                      <FullscreenRouteFallback message={DRAWING_WORKSPACE_LOADING_MESSAGE} />
                    }
                  >
                    <LayoutPage />
                  </Suspense>
                ),
              },
            ],
          },
          {
            element: <CapabilityRoute capability="simulations" />,
            children: [
              { path: 'simulations/:simulationId/setup', element: <SimulationSetupPage /> },
              {
                path: 'simulations/:simulationId/results',
                element: (
                  <Suspense
                    fallback={
                      <FullscreenRouteFallback message={SIMULATION_RESULT_LOADING_MESSAGE} />
                    }
                  >
                    <SimulationAnalysisResultPage />
                  </Suspense>
                ),
              },
              {
                path: 'simulations/:simulationId/layout-search',
                element: (
                  <Suspense
                    fallback={
                      <FullscreenRouteFallback message="배치 개선안을 준비하고 있습니다." />
                    }
                  >
                    <LayoutSearchPage />
                  </Suspense>
                ),
              },
            ],
          },
          {
            element: <CapabilityRoute capability="checklists" />,
            children: [
              {
                path: 'inspect/:areaId',
                element: (
                  <Suspense
                    fallback={<FullscreenRouteFallback message="점검 화면을 준비하고 있습니다." />}
                  >
                    <InspectionMobilePage />
                  </Suspense>
                ),
              },
            ],
          },
        ],
      },
      { path: 'login', element: <LoginPage /> },
    ],
  },
]);
