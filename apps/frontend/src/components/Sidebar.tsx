import { useEffect, useState } from 'react';
import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth/context/AuthContext';

type SidebarIconName =
  'home' | 'review' | 'risk' | 'checklist' | 'report' | 'regulation' | 'settings';

interface NavigationChild {
  label: string;
  to?: string;
}

interface NavigationItem {
  label: string;
  icon: SidebarIconName;
  to?: string;
  children?: NavigationChild[];
  requiredRole?: string;
}

const navigationItems: NavigationItem[] = [
  { label: '홈', icon: 'home', to: '/' },
  {
    label: '시뮬레이션 검토',
    icon: 'review',
    children: [
      { label: '도면 목록', to: '/drawings' },
      { label: '시뮬레이션 목록', to: '/simulations' },
    ],
  },
  { label: '보고서 관리', icon: 'report', to: '/reports' },
  { label: '위험 예상 항목 관리', icon: 'risk', to: '/risk-management' },
  { label: '안전 체크리스트', icon: 'checklist', to: '/safety-checklists' },
  { label: '안전 법령', icon: 'regulation', to: '/regulations' },
  { label: '시스템 관리', icon: 'settings', to: '/system-management', requiredRole: 'ADMIN' },
];

const iconPaths: Record<SidebarIconName, React.ReactNode> = {
  home: (
    <>
      <rect x="3" y="3" width="7" height="7" rx="2" />
      <rect x="14" y="3" width="7" height="7" rx="2" />
      <rect x="3" y="14" width="7" height="7" rx="2" />
      <rect x="14" y="14" width="7" height="7" rx="2" />
    </>
  ),
  review: (
    <>
      <path d="M4 4h16v16H4V4ZM9 4v5h5v4h6" />
      <path d="M7 16c2-4 4-4 6-2s3 2 5-1" strokeDasharray="2 2" />
      <path d="m16 11 2 2-2 2" />
    </>
  ),
  risk: (
    <>
      <path d="M12 3 2.8 19h18.4L12 3Z" />
      <path d="M12 9v4M12 17h.01" />
    </>
  ),
  checklist: (
    <>
      <rect x="4" y="3" width="16" height="18" rx="2" />
      <path d="m8 9 1.5 1.5L12 8M14 9h3M8 15l1.5 1.5L12 14M14 15h3" />
    </>
  ),
  report: (
    <>
      <path d="M6 3h9l4 4v14H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z" />
      <path d="M14 3v5h5M8 12h7M8 16h7" />
    </>
  ),
  regulation: (
    <>
      <path d="M5 4h14v16H5zM8 8h8M8 12h8M8 16h5" />
    </>
  ),
  settings: (
    <>
      <circle cx="12" cy="12" r="3" />
      <path d="M12 2v3M12 19v3M4.9 4.9 7 7M17 17l2.1 2.1M2 12h3M19 12h3M4.9 19.1 7 17M17 7l2.1-2.1" />
    </>
  ),
};

function SidebarIcon({ name }: { name: SidebarIconName }) {
  return (
    <svg
      aria-hidden="true"
      className="h-5 w-5"
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      {iconPaths[name]}
    </svg>
  );
}

const activeItemClassName =
  'relative flex h-12 items-center gap-4 rounded-xl bg-white/10 px-3 text-sm font-bold text-white before:absolute before:-left-5 before:h-8 before:w-1 before:rounded-r-full before:bg-lime';
const inactiveItemClassName =
  'flex h-12 w-full items-center gap-4 rounded-xl px-3 text-left text-sm font-medium text-white/45';

const ROLE_LABELS: Record<string, string> = {
  ADMIN: '관리자',
  OPERATOR: '운영 담당자',
  SAFETY_REVIEWER: '안전 검토자',
};

function Sidebar() {
  const [isSimulationMenuOpen, setIsSimulationMenuOpen] = useState(false);
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
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
    <aside className="sticky top-0 flex h-[100dvh] w-20 shrink-0 flex-col rounded-r-3xl bg-linear-to-br from-ink via-ink to-ink-deep px-5 py-7 text-white shadow-xl shadow-ink/15 lg:w-60">
      <div className="flex items-center gap-3 px-1">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center lg:w-15">
          <img
            src="/hyundai-department-group-ci.png"
            alt="현대백화점그룹"
            className="h-auto w-full"
          />
        </div>
        <div className="hidden min-w-0 lg:block">
          <p className="text-lg font-black tracking-[0.16em]">활로</p>
          <p className="mt-0.5 text-[10px] font-medium tracking-[0.12em] text-white/45">HWALRO</p>
        </div>
      </div>

      <div className="my-7 h-px bg-white/10" />

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
                className={`${inactiveItemClassName} transition-colors hover:bg-white/5 hover:text-white/80 ${
                  isSimulationMenuOpen ? 'bg-white/5 text-white/80' : ''
                }`}
              >
                <SidebarIcon name={item.icon} />
                <span className="hidden min-w-0 flex-1 truncate lg:block">{item.label}</span>
                <svg
                  aria-hidden="true"
                  className={`hidden h-4 w-4 shrink-0 transition-transform lg:block ${
                    isSimulationMenuOpen ? 'rotate-180' : ''
                  }`}
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d="m6 9 6 6 6-6" />
                </svg>
              </button>

              <ul
                id="simulation-review-submenu"
                hidden={!isSimulationMenuOpen}
                className="absolute left-full top-12 z-10 ml-2 w-44 space-y-1 rounded-xl bg-ink-deep p-2 shadow-xl shadow-ink/25 lg:static lg:mt-1 lg:ml-0 lg:w-auto lg:rounded-none lg:bg-transparent lg:p-0 lg:shadow-none"
              >
                {item.children.map((child) => (
                  <li key={child.label}>
                    {child.to ? (
                      <NavLink
                        to={child.to}
                        aria-label={child.label}
                        className={({ isActive }) =>
                          isActive
                            ? 'flex h-9 w-full items-center gap-3 rounded-lg bg-white/10 px-3 text-left text-xs font-bold text-white lg:pl-12'
                            : 'flex h-9 w-full items-center gap-3 rounded-lg px-3 text-left text-xs font-medium text-white/35 transition-colors hover:bg-white/5 hover:text-white/70 lg:pl-12'
                        }
                      >
                        <span
                          aria-hidden="true"
                          className="h-1 w-1 shrink-0 rounded-full bg-white/30"
                        />
                        <span className="truncate">{child.label}</span>
                      </NavLink>
                    ) : (
                      <button
                        type="button"
                        disabled
                        aria-label={`${child.label} (준비 중)`}
                        className="flex h-9 w-full items-center gap-3 rounded-lg px-3 text-left text-xs font-medium text-white/35 lg:pl-12"
                      >
                        <span
                          aria-hidden="true"
                          className="h-1 w-1 shrink-0 rounded-full bg-white/30"
                        />
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
                  : `${inactiveItemClassName} transition-colors hover:bg-white/5 hover:text-white/80`
              }
            >
              <SidebarIcon name={item.icon} />
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
              <SidebarIcon name={item.icon} />
              <span className="hidden truncate lg:block">{item.label}</span>
            </button>
          ),
        )}
      </nav>

      <div className="mt-auto space-y-1.5 rounded-2xl bg-white/5 p-2 lg:p-3">
        <div className="flex w-full items-center justify-center gap-3 lg:justify-start">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-lime text-xs font-black text-ink">
            {user?.name.charAt(0) ?? '활'}
          </div>
          <div className="hidden min-w-0 text-left lg:block">
            <p className="truncate text-xs font-bold text-white">{user?.name ?? '사용자'}</p>
            <p className="mt-1 truncate text-[10px] text-white/45">
              {user?.roles.map((role) => ROLE_LABELS[role] ?? role).join(', ')}
            </p>
          </div>
        </div>
        <button
          type="button"
          onClick={() => void handleLogout()}
          aria-label="로그아웃"
          className="group flex w-full items-center justify-center gap-3 rounded-xl px-3 py-2 transition-colors hover:bg-white/10 lg:justify-start"
        >
          <svg
            aria-hidden="true"
            className="h-4 w-4 shrink-0 text-white/45 transition-colors group-hover:text-white/80"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
            <path d="m16 17 5-5-5-5" />
            <path d="M21 12H9" />
          </svg>
          <span className="hidden text-xs font-medium text-white/45 transition-colors group-hover:text-white/80 lg:block">
            로그아웃
          </span>
        </button>
      </div>
    </aside>
  );
}

export default Sidebar;
