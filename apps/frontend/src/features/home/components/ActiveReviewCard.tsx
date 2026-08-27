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
    <section aria-label="진행 중인 안전 검토" className="home-active-review">
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
        <div className="home-active-review__loading" aria-busy="true">
          <span className="sr-only">진행 중인 검토를 불러오는 중입니다.</span>
          <div aria-hidden="true" className="home-active-review__loading-header">
            <span className="home-skeleton home-skeleton--kicker" />
            <span className="home-skeleton home-skeleton--title" />
            <span className="home-skeleton home-skeleton--meta" />
          </div>
          <div aria-hidden="true" className="home-active-review__loading-body">
            <span className="home-skeleton home-skeleton--scene" />
            <span className="home-skeleton home-skeleton--stage" />
          </div>
        </div>
      </CardShell>
    );
  }

  if (isError) {
    return (
      <CardShell>
        <div className="home-dashboard__state home-dashboard__state--stacked">
          <p
            role="alert"
            className="rounded-xl border border-danger/25 bg-danger-soft px-5 py-3 text-sm text-danger-strong"
          >
            {errorMessage}
          </p>
          <button type="button" onClick={onRetry} className="home-dashboard__retry">
            다시 시도
          </button>
        </div>
      </CardShell>
    );
  }

  if (!review) {
    return (
      <CardShell>
        <p className="home-active-review__kicker">
          <span aria-hidden="true" /> 진행 중인 안전 검토
        </p>
        <div className="home-active-review__empty">
          <p className="text-sm text-text-muted">아직 진행 중인 검토가 없습니다.</p>
          <Link to="/drawings" className="home-active-review__primary-link">
            도면 목록으로 이동
          </Link>
        </div>
      </CardShell>
    );
  }

  return (
    <CardShell>
      <p className="home-active-review__kicker">
        <span aria-hidden="true" /> 진행 중인 안전 검토
      </p>
      <h2 className="home-active-review__title">{review.title}</h2>
      <p className="home-active-review__meta">
        {review.subtitle} · 최근 작업 {formatDateTime(review.occurredAt)}
      </p>

      <div className="home-active-review__body">
        <div className="home-active-review__flow">
          <ReviewProgressStepper steps={review.steps} />
          <Link to={review.resumePath} className="home-active-review__primary-link">
            검토 이어가기
          </Link>
        </div>

        <div className="home-active-review__stage">
          <p>현재 단계</p>
          <strong>{review.currentStageLabel}</strong>
        </div>
      </div>
    </CardShell>
  );
}
