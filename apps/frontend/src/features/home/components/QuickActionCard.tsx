import { Link } from 'react-router-dom';

export function QuickActionCard() {
  return (
    <section
      aria-label="빠른 실행"
      className="flex min-h-64 flex-col rounded-2xl bg-ink p-6 text-white shadow-sm shadow-ink/10"
    >
      <span className="flex h-12 w-12 items-center justify-center rounded-xl bg-lime text-ink">
        <svg
          aria-hidden="true"
          className="h-6 w-6"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2.5"
          strokeLinecap="round"
        >
          <path d="M5 12h14M12 5v14" />
        </svg>
      </span>
      <h2 className="mt-5 text-2xl font-black tracking-tight">빠른 실행</h2>
      <p className="mt-3 text-sm leading-6 text-white/60">
        도면 등록부터 시뮬레이션 결과 비교까지 하나의 검토 흐름으로 진행합니다.
      </p>
      <Link
        to="/drawings/new"
        className="mt-auto inline-flex h-11 w-fit items-center rounded-lg bg-lime px-5 text-sm font-bold text-ink hover:opacity-90"
      >
        새 도면 등록
      </Link>
    </section>
  );
}
