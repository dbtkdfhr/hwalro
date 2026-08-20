import { useLoginForm } from '../features/auth/hooks/useLoginForm';

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
    <main className="flex min-h-[100dvh] bg-background">
      <section className="hidden w-[38%] shrink-0 flex-col bg-ink px-16 py-14 text-white lg:flex">
        <div>
          <p className="text-3xl font-black tracking-tight">HWALRO</p>
          <p className="mt-4 text-sm font-medium text-white/50">통합 안전 관리 플랫폼</p>
        </div>

        <div className="mt-40">
          <h1 className="text-4xl font-black leading-snug">
            사람이 몰리기 전에
            <br />
            공간의 위험을 검토합니다.
          </h1>
          <p className="mt-11 text-base leading-relaxed text-white/70">
            도면 배치부터 대피 시뮬레이션, 위험 조치와
            <br />
            보고서까지 하나의 업무 흐름으로 연결합니다.
          </p>
        </div>
      </section>

      <section className="flex flex-1 items-center justify-center px-6 py-16">
        <form className="w-full max-w-[420px]" onSubmit={handleSubmit}>
          <p className="text-sm font-bold tracking-tight text-ink lg:hidden">HWALRO</p>

          <h2 className="mt-6 text-2xl font-black text-ink lg:mt-0">환영합니다</h2>
          <p className="mt-2 text-sm text-ink/55">사내 계정으로 로그인해 주세요.</p>

          <label className="mt-12 block text-sm font-bold text-ink" htmlFor="username">
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

          <label className="mt-9 block text-sm font-bold text-ink" htmlFor="password">
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

          <label className="mt-7 flex w-fit items-center gap-2 text-sm text-ink/70">
            <input
              type="checkbox"
              checked={rememberMe}
              onChange={(event) => setRememberMe(event.target.checked)}
              className="h-4 w-4 shrink-0 accent-lime"
            />
            로그인 상태 유지
          </label>

          {errorMessage && (
            <p className="mt-5 rounded-2xl border border-danger/25 bg-danger-soft px-5 py-3 text-sm text-danger-strong">
              {errorMessage}
            </p>
          )}

          <button
            type="submit"
            disabled={isPending || !loginId || !password}
            className="mt-11 w-full rounded-2xl bg-ink px-5 py-4 text-base font-black text-white transition hover:bg-ink/85 active:bg-ink/70 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {isPending ? '로그인 중...' : '로그인'}
          </button>

          <div className="mt-12 border-t border-ink/10" />

          <p className="mt-7 text-center text-sm text-ink/55">
            권한이 필요한 경우 관리자에게 문의하세요.
          </p>
        </form>
      </section>
    </main>
  );
}

export default LoginPage;
