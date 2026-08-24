import { lazy, Suspense } from 'react';
import { useLoginForm } from '../features/auth/hooks/useLoginForm';

const LoginScene = lazy(() => import('../features/auth/components/LoginScene'));

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
    <main className="relative min-h-[100dvh] overflow-hidden bg-background">
      <section aria-hidden className="absolute inset-0">
        <Suspense fallback={null}>
          <LoginScene />
        </Suspense>
      </section>

      <header className="absolute left-8 top-9 z-10 lg:left-12 lg:top-11">
        <p className="text-2xl font-black tracking-tight text-ink">HWALRO</p>
        <p className="mt-2 text-sm font-medium text-ink/55">통합 안전 관리 플랫폼</p>
      </header>

      <section className="relative z-10 flex min-h-[100dvh] items-center justify-center px-5 py-14 lg:justify-end lg:pr-[7vw]">
        <form
          className="w-full max-w-[400px] rounded-3xl border border-white/70 bg-white/80 p-8 shadow-floating backdrop-blur-xl lg:p-10"
          onSubmit={handleSubmit}
        >
          <h2 className="text-2xl font-black text-ink">환영합니다</h2>
          <p className="mt-2 text-sm text-ink/55">사내 계정으로 로그인해 주세요.</p>

          <label className="mt-8 block text-sm font-bold text-ink" htmlFor="username">
            아이디
          </label>
          <input
            id="username"
            name="username"
            autoComplete="username"
            value={loginId}
            onChange={(event) => setLoginId(event.target.value)}
            placeholder="safety manager1234"
            className="mt-2 w-full rounded-2xl border border-ink/15 bg-white px-5 py-4 text-ink placeholder:text-ink/30 outline-none transition focus-visible:border-ink focus-visible:ring-4 focus-visible:ring-ink"
          />

          <label className="mt-7 block text-sm font-bold text-ink" htmlFor="password">
            비밀번호
          </label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            placeholder="password"
            className="mt-2 w-full rounded-2xl border border-ink/15 bg-white px-5 py-4 text-ink placeholder:text-ink/30 outline-none transition focus-visible:border-ink focus-visible:ring-4 focus-visible:ring-ink"
          />

          <label className="mt-6 flex w-fit items-center gap-2 text-sm text-ink/70">
            <input
              type="checkbox"
              checked={rememberMe}
              onChange={(event) => setRememberMe(event.target.checked)}
              className="h-4 w-4 shrink-0 accent-lime"
            />
            로그인 상태 유지
          </label>

          {errorMessage && (
            <p className="mt-4 rounded-2xl border border-danger/25 bg-danger-soft px-5 py-3 text-sm text-danger-strong">
              {errorMessage}
            </p>
          )}

          <button
            type="submit"
            disabled={isPending || !loginId || !password}
            className="mt-9 w-full rounded-2xl bg-ink px-5 py-4 text-base font-black text-white transition hover:bg-ink/85 active:bg-ink/70 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {isPending ? '로그인 중...' : '로그인'}
          </button>

          <div className="mt-10 border-t border-ink/10" />

          <p className="mt-6 text-center text-sm text-ink/55">
            권한이 필요한 경우 관리자에게 문의하세요.
          </p>
        </form>
      </section>
    </main>
  );
}

export default LoginPage;
