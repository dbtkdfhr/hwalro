import { useLoginForm } from '../features/auth/hooks/useLoginForm';
import LoginKineticMobile from '../features/auth/components/LoginKineticMobile';
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
      <div className="login-page__layout">
        <section className="login-page__showcase" aria-label="활로 통합 안전 관리 플랫폼">
          <header className="login-page__brand">
            <p className="login-page__wordmark">HWALRO</p>
            <p className="login-page__descriptor">통합 안전 관리 플랫폼</p>
          </header>

          <div className="login-page__art">
            <LoginKineticMobile />
          </div>
        </section>

        <section className="login-page__form-region">
          <form className="login-page__form" onSubmit={handleSubmit}>
            <h2>환영합니다</h2>
            <p className="login-page__form-intro">사내 계정으로 로그인해 주세요.</p>

            <label className="login-page__label" htmlFor="username">
              아이디
            </label>
            <input
              id="username"
              name="username"
              autoComplete="username"
              value={loginId}
              onChange={(event) => setLoginId(event.target.value)}
              placeholder="safety manager1234"
              className="login-page__input"
            />

            <label className="login-page__label" htmlFor="password">
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
              className="login-page__input"
            />

            <label className="login-page__remember">
              <input
                type="checkbox"
                checked={rememberMe}
                onChange={(event) => setRememberMe(event.target.checked)}
              />
              로그인 상태 유지
            </label>

            {errorMessage && <p className="login-page__error">{errorMessage}</p>}

            <button
              type="submit"
              disabled={isPending || !loginId || !password}
              className="login-page__submit"
            >
              {isPending ? '로그인 중...' : '로그인'}
            </button>

            <div className="login-page__divider" />

            <p className="login-page__help">권한이 필요한 경우 관리자에게 문의하세요.</p>
          </form>
        </section>
      </div>
    </main>
  );
}

export default LoginPage;
