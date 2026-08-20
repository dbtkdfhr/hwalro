import { Outlet, useLocation } from 'react-router-dom';
import Sidebar from '../components/Sidebar.tsx';

function WorkspaceLayout() {
  const { pathname } = useLocation();
  const isReportListPage = pathname === '/reports';
  const contentClassName = isReportListPage
    ? 'overflow-hidden p-6 lg:p-6'
    : 'overflow-auto p-6 lg:p-6';

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
