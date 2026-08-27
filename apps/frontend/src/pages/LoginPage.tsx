import { LoginSafetyScene } from '../features/auth/components/LoginSafetyScene';
import { useLoginForm } from '../features/auth/hooks/useLoginForm';
import './LoginPage.css';

function LoginPage() {
  const {
    loginId,
    setLoginId,
    password,
    setPassword,
    rememberMe,
    setRememberMe,
    isPending,
    errorMessage,
    handleSubmit,
  } = useLoginForm();

  return (
    <main className="login-page">
      <section className="login-visual">
        <div className="login-visual__brand">
          <p className="login-visual__wordmark">HWALRO</p>
          <p className="login-visual__tagline">통합 안전 검토 플랫폼</p>
        </div>
        <div className="login-visual__scene">
          <LoginSafetyScene />
        </div>
        <p className="login-visual__caption">
          <strong>도면에서 대피 결과까지</strong>
          <span>공간의 위험을 실행 전에 검토합니다.</span>
        </p>
      </section>

      <section className="login-panel">
        <form className="login-card" onSubmit={handleSubmit}>
          <p className="login-mobile-brand">HWALRO</p>

          <h1 className="mt-6 text-[28px] font-bold tracking-[-0.025em] text-ink lg:mt-0">
            로그인
          </h1>
          <p className="mt-2 text-sm text-text-muted">등록된 사내 계정으로 접속하세요.</p>

          <label className="mt-10 block text-sm font-semibold text-text-strong" htmlFor="username">
            아이디
          </label>
          <input
            id="username"
            name="username"
            autoComplete="username"
            value={loginId}
            onChange={(event) => setLoginId(event.target.value)}
            placeholder="아이디를 입력하세요"
            className="mt-2 h-12 w-full rounded-lg border border-line-strong bg-surface px-4 text-sm text-text-strong placeholder:text-text-faint outline-none transition-colors focus-visible:border-primary focus-visible:ring-2 focus-visible:ring-focus-ring"
          />

          <label className="mt-6 block text-sm font-semibold text-text-strong" htmlFor="password">
            비밀번호
          </label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            placeholder="비밀번호를 입력하세요"
            className="mt-2 h-12 w-full rounded-lg border border-line-strong bg-surface px-4 text-sm text-text-strong placeholder:text-text-faint outline-none transition-colors focus-visible:border-primary focus-visible:ring-2 focus-visible:ring-focus-ring"
          />

          <label className="mt-5 flex w-fit items-center gap-2 text-sm text-text-muted">
            <input
              type="checkbox"
              checked={rememberMe}
              onChange={(event) => setRememberMe(event.target.checked)}
              className="h-4 w-4 shrink-0 accent-primary"
            />
            로그인 상태 유지
          </label>

          {errorMessage && (
            <p className="mt-5 rounded-lg border border-danger/25 bg-danger-soft px-4 py-3 text-sm text-danger-strong">
              {errorMessage}
            </p>
          )}

          <button
            type="submit"
            disabled={isPending || !loginId || !password}
            className="mt-8 h-12 w-full rounded-lg bg-primary px-5 text-sm font-bold text-white transition-colors hover:bg-primary-hover active:bg-primary-active disabled:cursor-not-allowed disabled:bg-surface-subtle disabled:text-text-muted"
          >
            {isPending ? '로그인 중...' : '로그인'}
          </button>

          <div className="mt-10 border-t border-line" />

          <p className="mt-6 text-center text-sm text-text-muted">
            권한이 필요한 경우 관리자에게 문의하세요.
          </p>
        </form>
      </section>
    </main>
  );
}

export default LoginPage;
