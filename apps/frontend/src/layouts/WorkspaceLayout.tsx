import { Outlet, useLocation } from 'react-router-dom';
import Sidebar from '../components/Sidebar.tsx';

function WorkspaceLayout() {
  const { pathname } = useLocation();
  const isReportListPage = pathname === '/reports';
  const isEvacuationPage = /^\/my-zones\/[^/]+\/evacuation$/.test(pathname);
  const contentClassName =
    isReportListPage || isEvacuationPage
      ? 'h-[100dvh] overflow-hidden px-4 py-6 sm:px-6 lg:px-8 lg:py-8'
      : 'overflow-auto px-4 py-6 sm:px-6 lg:px-8 lg:py-8';

  return (
    <div className="flex min-h-[100dvh] bg-background text-ink">
      <Sidebar />
      <main className={`min-w-0 flex-1 ${contentClassName}`}>
        <Outlet />
      </main>
    </div>
  );
}

export default WorkspaceLayout;
