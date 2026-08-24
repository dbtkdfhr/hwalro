import {
  BookOpen,
  ChevronDown,
  ClipboardCheck,
  FileText,
  LayoutDashboard,
  LogOut,
  Map,
  Settings,
  TriangleAlert,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useEffect, useState } from 'react';
import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth/context/AuthContext';

interface NavigationChild {
  label: string;
  to?: string;
}

interface NavigationItem {
  label: string;
  icon: LucideIcon;
  to?: string;
  children?: NavigationChild[];
  requiredRole?: string;
}

const navigationItems: NavigationItem[] = [
  { label: '홈', icon: LayoutDashboard, to: '/' },
  {
    label: '시뮬레이션 검토',
    icon: Map,
    children: [
      { label: '도면 목록', to: '/drawings' },
      { label: '시뮬레이션 목록', to: '/simulations' },
    ],
  },
  { label: '보고서 관리', icon: FileText, to: '/reports' },
  { label: '위험 예상 항목 관리', icon: TriangleAlert, to: '/risk-management' },
  { label: '안전 체크리스트', icon: ClipboardCheck, to: '/safety-checklists' },
  { label: '안전 법령', icon: BookOpen, to: '/regulations' },
  { label: '시스템 관리', icon: Settings, to: '/system-management', requiredRole: 'ADMIN' },
];

function SidebarIcon({ icon: Icon }: { icon: LucideIcon }) {
  return <Icon aria-hidden="true" className="h-5 w-5" strokeWidth={1.8} />;
}

const activeItemClassName =
  'flex h-12 w-full items-center justify-center gap-4 rounded-lg bg-lime/25 px-3 text-sm font-bold text-ink outline-none focus-visible:ring-2 focus-visible:ring-focus-ring focus-visible:ring-offset-2 focus-visible:ring-offset-surface-overlay lg:justify-start';
const inactiveItemClassName =
  'flex h-12 w-full items-center justify-center gap-4 rounded-lg px-3 text-left text-sm font-normal text-text-muted outline-none focus-visible:ring-2 focus-visible:ring-focus-ring focus-visible:ring-offset-2 focus-visible:ring-offset-surface-overlay lg:justify-start';

const ROLE_LABELS: Record<string, string> = {
  ADMIN: '관리자',
  OPERATOR: '운영 담당자',
  SAFETY_REVIEWER: '안전 검토자',
  GENERAL_EMPLOYEE: '일반 직원',
};

function Sidebar() {
  const [isSimulationMenuOpen, setIsSimulationMenuOpen] = useState(false);
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const isSimulationRoute =
    pathname.startsWith('/drawings') || pathname.startsWith('/simulations');
  const visibleNavigationItems = navigationItems.filter(
    (item) => !item.requiredRole || user?.roles.includes(item.requiredRole),
  );

  useEffect(() => {
    if (pathname.startsWith('/drawings') || pathname.startsWith('/simulations')) {
      setIsSimulationMenuOpen(true);
    }
  }, [pathname]);

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <aside className="sticky top-0 flex h-[100dvh] w-20 shrink-0 flex-col border-r border-line bg-surface-overlay px-3 py-6 text-ink lg:w-60 lg:px-5">
      <div className="flex items-center justify-center gap-3 px-1 lg:justify-start">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center lg:w-15">
          <img
            src="/hyundai-department-group-ci.png"
            alt="현대백화점그룹"
            className="h-auto w-full"
          />
        </div>
        <div className="hidden min-w-0 lg:block">
          <p className="text-lg font-black tracking-[0.16em]">활로</p>
          <p className="mt-0.5 text-[10px] font-normal tracking-[0.12em] text-text-faint">
            HWALRO
          </p>
        </div>
      </div>

      <div className="my-6 h-px bg-line" />

      <nav aria-label="주요 메뉴" className="flex flex-col gap-2">
        {visibleNavigationItems.map((item) =>
          item.children ? (
            <div key={item.label} className="relative">
              <button
                type="button"
                aria-label={item.label}
                aria-expanded={isSimulationMenuOpen}
                aria-controls="simulation-review-submenu"
                onClick={() => setIsSimulationMenuOpen((isOpen) => !isOpen)}
                className={`${inactiveItemClassName} transition-colors hover:bg-surface-sunken hover:text-ink ${
                  isSimulationRoute
                    ? 'font-bold text-primary'
                    : isSimulationMenuOpen
                      ? 'bg-surface-sunken text-ink'
                      : ''
                }`}
              >
                <SidebarIcon icon={item.icon} />
                <span className="hidden min-w-0 flex-1 truncate lg:block">{item.label}</span>
                <ChevronDown
                  aria-hidden="true"
                  className={`hidden h-4 w-4 shrink-0 transition-transform lg:block ${
                    isSimulationMenuOpen ? 'rotate-180' : ''
                  }`}
                  strokeWidth={1.8}
                />
              </button>

              <ul
                id="simulation-review-submenu"
                hidden={!isSimulationMenuOpen}
                className="absolute left-full top-12 z-10 ml-2 w-44 space-y-1 rounded-lg border border-line bg-surface-overlay p-2 shadow-overlay lg:static lg:mt-1 lg:ml-0 lg:w-auto lg:border-0 lg:bg-transparent lg:p-0 lg:shadow-none"
              >
                {item.children.map((child) => (
                  <li key={child.label}>
                    {child.to ? (
                      <NavLink
                        to={child.to}
                        aria-label={child.label}
                        className={({ isActive }) =>
                          isActive
                            ? 'flex h-9 w-full items-center rounded-lg bg-lime/25 px-3 text-left text-xs font-bold text-ink outline-none focus-visible:ring-2 focus-visible:ring-focus-ring focus-visible:ring-offset-2 focus-visible:ring-offset-surface-overlay lg:pl-12'
                            : 'flex h-9 w-full items-center rounded-lg px-3 text-left text-xs font-normal text-text-faint outline-none transition-colors hover:bg-surface-sunken hover:text-ink focus-visible:ring-2 focus-visible:ring-focus-ring focus-visible:ring-offset-2 focus-visible:ring-offset-surface-overlay lg:pl-12'
                        }
                      >
                        <span className="truncate">{child.label}</span>
                      </NavLink>
                    ) : (
                      <button
                        type="button"
                        disabled
                        aria-label={`${child.label} (준비 중)`}
                        className="flex h-9 w-full items-center rounded-lg px-3 text-left text-xs font-normal text-text-faint lg:pl-12"
                      >
                        <span className="truncate">{child.label}</span>
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          ) : item.to ? (
            <NavLink
              key={item.label}
              to={item.to}
              end={item.to === '/'}
              aria-label={item.label}
              className={({ isActive }) =>
                isActive
                  ? activeItemClassName
                  : `${inactiveItemClassName} transition-colors hover:bg-surface-sunken hover:text-ink`
              }
            >
              <SidebarIcon icon={item.icon} />
              <span className="hidden truncate lg:block">{item.label}</span>
            </NavLink>
          ) : (
            <button
              key={item.label}
              type="button"
              disabled
              aria-label={`${item.label} (준비 중)`}
              className={inactiveItemClassName}
            >
              <SidebarIcon icon={item.icon} />
              <span className="hidden truncate lg:block">{item.label}</span>
            </button>
          ),
        )}
      </nav>

      <div className="mt-auto space-y-1.5 border-t border-line pt-4">
        <div className="flex w-full items-center justify-center gap-3 lg:justify-start">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-lime text-xs font-black text-ink">
            {user?.name.charAt(0) ?? '활'}
          </div>
          <div className="hidden min-w-0 text-left lg:block">
            <p className="truncate text-xs font-bold text-ink">{user?.name ?? '사용자'}</p>
            <p className="mt-1 truncate text-[10px] text-text-muted">
              {user?.roles.map((role) => ROLE_LABELS[role] ?? role).join(', ')}
            </p>
          </div>
        </div>
        <button
          type="button"
          onClick={() => void handleLogout()}
          aria-label="로그아웃"
          className="group flex w-full items-center justify-center gap-3 rounded-lg px-3 py-2 outline-none transition-colors hover:bg-surface-sunken active:bg-line-subtle focus-visible:ring-2 focus-visible:ring-focus-ring focus-visible:ring-offset-2 focus-visible:ring-offset-surface-overlay lg:justify-start"
        >
          <LogOut
            aria-hidden="true"
            className="h-4 w-4 shrink-0 text-text-muted transition-colors group-hover:text-ink"
            strokeWidth={1.8}
          />
          <span className="hidden text-xs font-normal text-text-muted transition-colors group-hover:text-ink lg:block">
            로그아웃
          </span>
        </button>
      </div>
    </aside>
  );
}

export default Sidebar;
