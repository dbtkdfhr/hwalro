import { Outlet, useLocation } from 'react-router-dom';
import Sidebar from '../components/Sidebar.tsx';

function WorkspaceLayout() {
  const { pathname } = useLocation();
  const isReportListPage = pathname === '/reports';
  const contentClassName = isReportListPage
    ? 'overflow-hidden p-5 lg:p-7'
    : 'overflow-auto p-5 lg:p-7';

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
