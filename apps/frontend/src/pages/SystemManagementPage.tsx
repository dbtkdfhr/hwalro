import { FormEvent, useCallback, useEffect, useRef, useState } from 'react';
import { isAxiosError } from 'axios';
import { apiClient } from '../api/client';
import { PageHeader, buttonClassName } from '../components/ui';
import './SystemManagementPage.css';

interface UserSummary {
  userId: number;
  loginId: string;
  name: string;
  enabled: boolean;
  createdAt: string;
  roles: string[];
}

interface RoleSummary {
  roleId: number;
  roleName: string;
  description: string | null;
}

interface SystemManagementData {
  users: UserSummary[];
  roles: RoleSummary[];
}

interface InviteForm {
  loginId: string;
  password: string;
  name: string;
  roleIds: number[];
}

const emptyInviteForm: InviteForm = {
  loginId: '',
  password: '',
  name: '',
  roleIds: [],
};

const focusableElementSelector = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

const roleLabels: Record<string, string> = {
  ADMIN: '관리자',
  OPERATOR: '운영 담당자',
  SAFETY_REVIEWER: '안전 검토자',
  GENERAL_EMPLOYEE: '매장 직원',
};

const roleScopeSummaries: Record<string, string> = {
  ADMIN: '사용자와 역할을 관리하고 전체 업무 데이터를 확인합니다.',
  OPERATOR: '담당 도면과 시뮬레이션을 운영하고 보고서를 작성합니다.',
  SAFETY_REVIEWER: '전체 검토 데이터를 확인하고 주의 항목, 안전 점검 및 보고서를 검토합니다.',
  GENERAL_EMPLOYEE: '담당 구역의 비상구와 대피 경로를 확인하고 안전 체크리스트를 점검합니다.',
};

function roleTone(roleName: string) {
  if (roleName === 'SAFETY_REVIEWER') return 'reviewer';
  if (roleName === 'ADMIN') return 'admin';
  if (roleName === 'OPERATOR') return 'operator';
  return 'default';
}

function roleLabel(roleName: string) {
  return roleLabels[roleName] ?? roleName;
}

function roleDescription(role: RoleSummary) {
  return roleScopeSummaries[role.roleName] ?? role.description ?? '등록된 권한 범위를 사용합니다.';
}

function formatCreatedAt(createdAt: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(createdAt));
}

function SystemManagementPage() {
  const [data, setData] = useState<SystemManagementData>({ users: [], roles: [] });
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [isInviteOpen, setIsInviteOpen] = useState(false);
  const [inviteForm, setInviteForm] = useState<InviteForm>(emptyInviteForm);
  const [inviteError, setInviteError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [updatingUserId, setUpdatingUserId] = useState<number | null>(null);
  const [statusError, setStatusError] = useState('');
  const inviteButtonRef = useRef<HTMLButtonElement>(null);
  const inviteModalRef = useRef<HTMLElement>(null);
  const firstInviteFieldRef = useRef<HTMLInputElement>(null);

  const loadSystemManagementData = useCallback(async () => {
    setIsLoading(true);
    setLoadError('');

    try {
      const response = await apiClient.get<SystemManagementData>('/api/admin/system-management');
      setData(response.data);
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : '사용자 정보를 불러오지 못했습니다.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadSystemManagementData();
  }, [loadSystemManagementData]);

  const resetInviteModal = useCallback(() => {
    setIsInviteOpen(false);
    setInviteForm(emptyInviteForm);
    setInviteError('');
    window.requestAnimationFrame(() => inviteButtonRef.current?.focus());
  }, []);

  const closeInviteModal = useCallback(() => {
    if (isSubmitting) return;
    resetInviteModal();
  }, [isSubmitting, resetInviteModal]);

  useEffect(() => {
    if (isInviteOpen) firstInviteFieldRef.current?.focus();
  }, [isInviteOpen]);

  useEffect(() => {
    if (!isInviteOpen) return;

    const manageModalKeyboard = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        closeInviteModal();
        return;
      }

      if (event.key !== 'Tab' || !inviteModalRef.current) return;

      const focusableElements = Array.from(
        inviteModalRef.current.querySelectorAll<HTMLElement>(focusableElementSelector),
      );
      if (focusableElements.length === 0) {
        event.preventDefault();
        inviteModalRef.current.focus();
        return;
      }

      const firstElement = focusableElements[0];
      const lastElement = focusableElements[focusableElements.length - 1];
      const activeElement = document.activeElement;

      if (!inviteModalRef.current.contains(activeElement)) {
        event.preventDefault();
        (event.shiftKey ? lastElement : firstElement).focus();
      } else if (event.shiftKey && activeElement === firstElement) {
        event.preventDefault();
        lastElement.focus();
      } else if (!event.shiftKey && activeElement === lastElement) {
        event.preventDefault();
        firstElement.focus();
      }
    };
    window.addEventListener('keydown', manageModalKeyboard);
    return () => window.removeEventListener('keydown', manageModalKeyboard);
  }, [closeInviteModal, isInviteOpen]);

  const toggleRole = (roleId: number) => {
    setInviteForm((current) => ({
      ...current,
      roleIds: current.roleIds.includes(roleId)
        ? current.roleIds.filter((id) => id !== roleId)
        : [...current.roleIds, roleId],
    }));
  };

  const submitInvite = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setInviteError('');

    if (inviteForm.roleIds.length === 0) {
      setInviteError('역할을 하나 이상 선택해 주세요.');
      return;
    }

    setIsSubmitting(true);
    try {
      await apiClient.post('/api/admin/system-management/users', inviteForm);

      resetInviteModal();
      await loadSystemManagementData();
    } catch (error) {
      if (isAxiosError(error) && error.response?.status === 409) {
        setInviteError('이미 사용 중인 로그인 ID입니다.');
      } else if (isAxiosError(error) && error.response?.status === 400) {
        setInviteError('입력한 사용자 정보를 확인해 주세요.');
      } else {
        setInviteError('사용자를 만들지 못했습니다. 잠시 후 다시 시도해 주세요.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const changeUserEnabled = async (user: UserSummary) => {
    if (updatingUserId !== null) return;

    const nextEnabled = !user.enabled;
    setStatusError('');
    setUpdatingUserId(user.userId);

    try {
      await apiClient.patch(`/api/admin/system-management/users/${user.userId}/enabled`, {
        enabled: nextEnabled,
      });

      setData((current) => ({
        ...current,
        users: current.users.map((currentUser) =>
          currentUser.userId === user.userId
            ? { ...currentUser, enabled: nextEnabled }
            : currentUser,
        ),
      }));
    } catch (error) {
      setStatusError(error instanceof Error ? error.message : '사용자 상태를 변경하지 못했습니다.');
    } finally {
      setUpdatingUserId(null);
    }
  };

  return (
    <main className="system-management-page">
      <div className="system-management-content">
        <div className="border-b border-line pb-6">
          <PageHeader
            eyebrow="시스템 설정"
            title="시스템 관리"
            description="사용자와 역할을 관리합니다."
            actions={
              <button
                ref={inviteButtonRef}
                type="button"
                onClick={() => setIsInviteOpen(true)}
                disabled={isLoading || data.roles.length === 0}
                className={buttonClassName({ variant: 'primary', size: 'lg' })}
              >
                사용자 초대
              </button>
            }
          />
        </div>

        <section className="management-card mt-5" aria-labelledby="users-heading">
          <div className="card-heading">
            <h2 id="users-heading">사용자·권한</h2>
            <span>{`전체 역할 ${data.roles.length}개`}</span>
          </div>

          {isLoading && <p className="notice-state">사용자 정보를 불러오는 중입니다.</p>}
          {!isLoading && loadError && (
            <div className="notice-state error-state" role="alert">
              <p>{loadError}</p>
              <button type="button" onClick={() => void loadSystemManagementData()}>
                다시 시도
              </button>
            </div>
          )}
          {statusError && (
            <p className="status-error" role="alert">
              {statusError}
            </p>
          )}
          {!isLoading && !loadError && (
            <div className="table-wrap">
              <table>
                <caption className="sr-only">사용자별 역할과 계정 상태</caption>
                <thead>
                  <tr>
                    <th scope="col">사용자</th>
                    <th scope="col">역할</th>
                    <th scope="col">생성일</th>
                    <th scope="col">상태</th>
                  </tr>
                </thead>
                <tbody>
                  {data.users.map((user) => (
                    <tr key={user.userId}>
                      <th scope="row">
                        <span className="user-identity">
                          {user.name}
                          <small>{user.loginId}</small>
                        </span>
                      </th>
                      <td>{user.roles.map(roleLabel).join(', ') || '역할 없음'}</td>
                      <td>{formatCreatedAt(user.createdAt)}</td>
                      <td>
                        <button
                          type="button"
                          className={`status ${user.enabled ? '' : 'stopped'}`}
                          aria-label={`${user.name} 계정 ${user.enabled ? '비활성화' : '활성화'}`}
                          aria-pressed={user.enabled}
                          disabled={updatingUserId !== null}
                          onClick={() => void changeUserEnabled(user)}
                        >
                          {updatingUserId === user.userId
                            ? '변경 중'
                            : user.enabled
                              ? '활성'
                              : '중지'}
                        </button>
                      </td>
                    </tr>
                  ))}
                  {data.users.length === 0 && (
                    <tr>
                      <td className="empty-row" colSpan={4}>
                        등록된 사용자가 없습니다.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          )}

          <div className="role-section">
            <div className="role-section-heading">
              <div>
                <h3>역할별 주요 권한</h3>
                <p>현재 서비스에서 역할별로 사용할 수 있는 주요 업무 범위입니다.</p>
              </div>
              <span>현재 기준</span>
            </div>
            {!isLoading &&
              !loadError &&
              (data.roles.length === 0 ? (
                <p className="role-empty-state">
                  등록된 역할이 없습니다. 사용자 초대 전에 역할을 등록해 주세요.
                </p>
              ) : (
                <div className="role-list">
                  {data.roles.map((role) => {
                    const assignedUserCount = data.users.filter((user) =>
                      user.roles.includes(role.roleName),
                    ).length;

                    return (
                      <article className={`role-card ${roleTone(role.roleName)}`} key={role.roleId}>
                        <div className="role-card-heading">
                          <div>
                            <h4>{roleLabel(role.roleName)}</h4>
                            <span className="role-code">{role.roleName}</span>
                          </div>
                          <span className="role-user-count">
                            {assignedUserCount.toLocaleString()}명
                          </span>
                        </div>
                        <p>{roleDescription(role)}</p>
                      </article>
                    );
                  })}
                </div>
              ))}
          </div>
        </section>
      </div>

      {isInviteOpen && (
        <div className="app-modal-backdrop modal-backdrop" onMouseDown={closeInviteModal}>
          <section
            ref={inviteModalRef}
            className="invite-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="invite-title"
            tabIndex={-1}
            onMouseDown={(event) => event.stopPropagation()}
          >
            <div className="modal-heading">
              <div>
                <h2 id="invite-title">사용자 초대</h2>
                <p>사용자가 로그인할 계정과 역할을 지정합니다.</p>
              </div>
              <button
                type="button"
                className="close-button"
                aria-label="닫기"
                onClick={closeInviteModal}
              >
                ×
              </button>
            </div>

            <form onSubmit={submitInvite}>
              <label>
                이름
                <input
                  ref={firstInviteFieldRef}
                  required
                  maxLength={100}
                  value={inviteForm.name}
                  onChange={(event) => setInviteForm({ ...inviteForm, name: event.target.value })}
                />
              </label>
              <label>
                로그인 ID
                <input
                  required
                  maxLength={100}
                  autoComplete="username"
                  value={inviteForm.loginId}
                  onChange={(event) =>
                    setInviteForm({ ...inviteForm, loginId: event.target.value })
                  }
                />
              </label>
              <label>
                임시 비밀번호
                <input
                  required
                  type="password"
                  minLength={8}
                  maxLength={72}
                  autoComplete="new-password"
                  value={inviteForm.password}
                  onChange={(event) =>
                    setInviteForm({ ...inviteForm, password: event.target.value })
                  }
                />
                <small>8자 이상 입력해 주세요.</small>
              </label>

              <fieldset>
                <legend>역할</legend>
                <div className="role-options">
                  {data.roles.map((role) => (
                    <label key={role.roleId}>
                      <input
                        type="checkbox"
                        checked={inviteForm.roleIds.includes(role.roleId)}
                        onChange={() => toggleRole(role.roleId)}
                      />
                      <span>
                        {roleLabel(role.roleName)}
                        <small>{role.description || '설명 없음'}</small>
                      </span>
                    </label>
                  ))}
                </div>
              </fieldset>

              {inviteError && (
                <p className="form-error" role="alert">
                  {inviteError}
                </p>
              )}

              <div className="modal-actions">
                <button type="button" className="secondary-button" onClick={closeInviteModal}>
                  취소
                </button>
                <button type="submit" className="primary-button" disabled={isSubmitting}>
                  {isSubmitting ? '생성 중...' : '사용자 생성'}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </main>
  );
}

export default SystemManagementPage;
