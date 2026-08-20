import { Link } from 'react-router-dom';
import type { ActiveReview } from '../types/home';
import { ReviewProgressStepper } from './ReviewProgressStepper';

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}

interface ActiveReviewCardProps {
  review: ActiveReview | null;
  isPending: boolean;
  isError: boolean;
  errorMessage: string;
  onRetry: () => void;
}

function CardShell({ children }: { children: React.ReactNode }) {
  return (
    <section
      aria-label="진행 중인 안전 검토"
      className="flex min-h-64 flex-col rounded-2xl border border-line bg-white p-6 shadow-sm shadow-ink/5"
    >
      {children}
    </section>
  );
}

export function ActiveReviewCard({
  review,
  isPending,
  isError,
  errorMessage,
  onRetry,
}: ActiveReviewCardProps) {
  if (isPending) {
    return (
      <CardShell>
        <div className="flex flex-1 items-center justify-center text-sm text-text-muted">
          진행 중인 검토를 불러오는 중입니다.
        </div>
      </CardShell>
    );
  }

  if (isError) {
    return (
      <CardShell>
        <div className="flex flex-1 flex-col items-center justify-center gap-4 text-center">
          <p
            role="alert"
            className="rounded-xl border border-danger/25 bg-danger-soft px-5 py-3 text-sm text-danger-strong"
          >
            {errorMessage}
          </p>
          <button
            type="button"
            onClick={onRetry}
            className="rounded-lg border border-line bg-white px-4 py-2 text-sm font-bold text-text-strong hover:bg-surface"
          >
            다시 시도
          </button>
        </div>
      </CardShell>
    );
  }

  if (!review) {
    return (
      <CardShell>
        <p className="text-xs font-bold tracking-wide text-primary">진행 중인 안전 검토</p>
        <div className="flex flex-1 flex-col items-center justify-center gap-4 text-center">
          <p className="text-sm text-text-muted">아직 진행 중인 검토가 없습니다.</p>
          <Link
            to="/drawings"
            className="inline-flex h-10 items-center rounded-lg bg-primary px-4 text-sm font-bold text-white hover:opacity-90"
          >
            도면 목록으로 이동
          </Link>
        </div>
      </CardShell>
    );
  }

  return (
    <CardShell>
      <p className="text-xs font-bold tracking-wide text-primary">진행 중인 안전 검토</p>
      <h2 className="mt-2 truncate text-2xl font-black tracking-tight text-ink">{review.title}</h2>
      <p className="mt-2 text-sm text-text-muted">
        {review.subtitle} · 최근 작업 {formatDateTime(review.occurredAt)}
      </p>

      <div className="mt-5 border-t border-line pt-6" />

      <div className="flex flex-col gap-6 lg:flex-row lg:items-start">
        <div className="min-w-0 flex-1">
          <ReviewProgressStepper steps={review.steps} />
          <Link
            to={review.resumePath}
            className="mt-6 inline-flex h-11 items-center rounded-lg bg-primary px-5 text-sm font-bold text-white hover:opacity-90"
          >
            검토 이어가기
          </Link>
        </div>

        <div className="shrink-0 rounded-xl bg-surface p-4 lg:w-56">
          <p className="text-xs font-bold text-text-muted">현재 단계</p>
          <p className="mt-2 text-lg font-black text-ink">{review.currentStageLabel}</p>
        </div>
      </div>
    </CardShell>
  );
}
