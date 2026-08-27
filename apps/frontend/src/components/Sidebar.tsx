import {
  ChevronDown,
  ClipboardCheck,
  FileChartColumn,
  House,
  LayoutTemplate,
  LogOut,
  MapPinned,
  Scale,
  Settings,
  ShieldAlert,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useEffect, useState } from 'react';
import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth/context/AuthContext';
import { can, type Capability } from '../features/auth/capabilities';

type SidebarIconName =
  'home' | 'review' | 'risk' | 'checklist' | 'report' | 'regulation' | 'settings' | 'zone';

interface NavigationChild {
  label: string;
  to?: string;
}

interface NavigationItem {
  label: string;
  icon: SidebarIconName;
  to?: string;
  children?: NavigationChild[];
  /** 이 권한이 없으면 메뉴를 감춘다. 차단 자체는 라우트 가드와 서버가 한다. */
  requiredCapability?: Capability;
  /** 이 권한을 가진 사용자에게는 감춘다. 같은 화면을 가리키는 메뉴가 둘 생기는 것을 막는다. */
  hiddenWithCapability?: Capability;
}

const navigationItems: NavigationItem[] = [
  { label: '홈', icon: 'home', to: '/', requiredCapability: 'simulations' },
  {
    label: '내 구역',
    icon: 'zone',
    to: '/my-zones',
    requiredCapability: 'zones.assigned',
  },
  {
    label: '시뮬레이션 검토',
    icon: 'review',
    requiredCapability: 'simulations',
    children: [
      { label: '도면 목록', to: '/drawings' },
      { label: '시뮬레이션 목록', to: '/simulations' },
    ],
  },
  { label: '보고서 관리', icon: 'report', to: '/reports', requiredCapability: 'reports' },
  {
    label: '주의 항목 관리',
    icon: 'risk',
    to: '/risk-management',
    requiredCapability: 'risks',
  },
  {
    label: '안전 체크리스트',
    icon: 'checklist',
    to: '/safety-checklists',
    requiredCapability: 'checklists',
  },
  { label: '안전 법령', icon: 'regulation', to: '/regulations', requiredCapability: 'regulations' },
  {
    label: '시스템 관리',
    icon: 'settings',
    to: '/system-management',
    requiredCapability: 'systemManagement',
  },
];

const iconComponents: Record<SidebarIconName, LucideIcon> = {
  home: House,
  review: LayoutTemplate,
  risk: ShieldAlert,
  checklist: ClipboardCheck,
  report: FileChartColumn,
  regulation: Scale,
  zone: MapPinned,
  settings: Settings,
};

function SidebarIcon({ name }: { name: SidebarIconName }) {
  const Icon = iconComponents[name];
  return <Icon aria-hidden="true" className="h-5 w-5" strokeWidth={1.8} />;
}

const activeItemClassName =
  'flex h-11 items-center gap-3 rounded-lg bg-primary px-3 text-sm font-bold text-white';
const inactiveItemClassName =
  'flex h-11 w-full items-center gap-3 rounded-lg px-3 text-left text-sm font-medium text-workspace-muted';

const ROLE_LABELS: Record<string, string> = {
  ADMIN: '관리자',
  OPERATOR: '운영 담당자',
  SAFETY_REVIEWER: '안전 검토자',
  GENERAL_EMPLOYEE: '매장 직원',
};

function Sidebar() {
  const [isSimulationMenuOpen, setIsSimulationMenuOpen] = useState(false);
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const visibleNavigationItems = navigationItems.filter(
    (item) =>
      (!item.requiredCapability || can(user?.roles, item.requiredCapability)) &&
      (!item.hiddenWithCapability || !can(user?.roles, item.hiddenWithCapability)),
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
    <aside className="sticky top-0 flex h-[100dvh] w-16 shrink-0 flex-col border-r border-workspace-line bg-workspace-panel px-3 py-6 text-workspace-text lg:w-[232px] lg:px-4">
      <div className="flex items-center gap-3 px-1">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center lg:w-15">
          <img
            src="/hyundai-department-group-ci.png"
            alt="현대백화점그룹"
            className="h-auto w-full"
          />
        </div>
        <div className="hidden min-w-0 lg:block">
          <p className="text-lg font-bold tracking-[0.12em]">활로</p>
          <p className="mt-0.5 text-[10px] font-medium tracking-[0.1em] text-workspace-muted">
            HWALRO
          </p>
        </div>
      </div>

      <div className="my-6 h-px bg-workspace-line" />

      <nav aria-label="주요 메뉴" className="flex flex-col gap-1.5">
        {visibleNavigationItems.map((item) =>
          item.children ? (
            <div key={item.label} className="relative">
              <button
                type="button"
                aria-label={item.label}
                aria-expanded={isSimulationMenuOpen}
                aria-controls="simulation-review-submenu"
                onClick={() => setIsSimulationMenuOpen((isOpen) => !isOpen)}
                className={`${inactiveItemClassName} transition-colors hover:bg-workspace-raised hover:text-workspace-text ${
                  isSimulationMenuOpen ? 'bg-workspace-raised text-workspace-text' : ''
                }`}
              >
                <SidebarIcon name={item.icon} />
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
                className="absolute left-full top-11 z-10 ml-2 w-44 space-y-1 rounded-lg border border-workspace-line bg-workspace-panel p-2 shadow-floating lg:static lg:mt-1 lg:ml-0 lg:w-auto lg:border-0 lg:bg-transparent lg:p-0 lg:shadow-none"
              >
                {item.children.map((child) => (
                  <li key={child.label}>
                    {child.to ? (
                      <NavLink
                        to={child.to}
                        aria-label={child.label}
                        className={({ isActive }) =>
                          isActive
                            ? 'flex h-9 w-full items-center rounded-md bg-primary/15 px-3 text-left text-xs font-bold text-workspace-accent lg:pl-11'
                            : 'flex h-9 w-full items-center rounded-md px-3 text-left text-xs font-medium text-workspace-muted transition-colors hover:bg-workspace-raised hover:text-workspace-text lg:pl-11'
                        }
                      >
                        <span className="truncate">{child.label}</span>
                      </NavLink>
                    ) : (
                      <button
                        type="button"
                        disabled
                        aria-label={`${child.label} (준비 중)`}
                        className="flex h-9 w-full items-center rounded-md px-3 text-left text-xs font-medium text-workspace-muted lg:pl-11"
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
                  : `${inactiveItemClassName} transition-colors hover:bg-workspace-raised hover:text-workspace-text`
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

      <div className="mt-auto space-y-1.5 border-t border-workspace-line pt-4">
        <div className="flex w-full items-center justify-center gap-3 lg:justify-start">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary text-xs font-bold text-white">
            {user?.name.charAt(0) ?? '활'}
          </div>
          <div className="hidden min-w-0 text-left lg:block">
            <p className="truncate text-xs font-bold text-workspace-text">
              {user?.name ?? '사용자'}
            </p>
            <p className="mt-1 truncate text-[10px] text-workspace-muted">
              {user?.roles.map((role) => ROLE_LABELS[role] ?? role).join(', ')}
            </p>
          </div>
        </div>
        <button
          type="button"
          onClick={() => void handleLogout()}
          aria-label="로그아웃"
          className="group flex w-full items-center justify-center gap-3 rounded-lg px-3 py-2 transition-colors hover:bg-workspace-raised lg:justify-start"
        >
          <LogOut
            aria-hidden="true"
            className="h-4 w-4 shrink-0 text-workspace-muted transition-colors group-hover:text-workspace-text"
            strokeWidth={1.8}
          />
          <span className="hidden text-xs font-medium text-workspace-muted transition-colors group-hover:text-workspace-text lg:block">
            로그아웃
          </span>
        </button>
      </div>
    </aside>
  );
}

export default Sidebar;
