import type { ReviewStep } from '../types/home';

const STATE_LABELS: Record<ReviewStep['state'], string> = {
  done: '완료',
  current: '진행 중',
  upcoming: '예정',
};

function StepMarker({ state }: { state: ReviewStep['state'] }) {
  if (state === 'done') {
    return (
      <span className="relative z-10 flex h-7 w-7 items-center justify-center rounded-full bg-primary text-white">
        <svg
          aria-hidden="true"
          className="h-4 w-4"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="3"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="m5 13 4 4L19 7" />
        </svg>
      </span>
    );
  }
  return (
    <span
      className={`relative z-10 block h-7 w-7 rounded-full border-2 bg-white ${
        state === 'current' ? 'border-primary' : 'border-line'
      }`}
    />
  );
}

export function ReviewProgressStepper({ steps }: { steps: ReviewStep[] }) {
  return (
    <ol className="flex items-start" aria-label="검토 진행 단계">
      {steps.map((step, index) => (
        <li
          key={step.key}
          className="relative flex flex-1 flex-col items-center"
          aria-current={step.state === 'current' ? 'step' : undefined}
        >
          {/* 이전 단계 마커 중심에서 이 단계 마커 중심까지 잇는 연결선 */}
          {index > 0 && (
            <span
              aria-hidden="true"
              className={`absolute top-3.5 -left-1/2 h-0.5 w-full -translate-y-1/2 ${
                step.state === 'upcoming' ? 'bg-line' : 'bg-primary'
              }`}
            />
          )}
          <StepMarker state={step.state} />
          <span
            className={`mt-2 text-center text-xs ${
              step.state === 'upcoming' ? 'text-text-muted' : 'font-bold text-text-strong'
            }`}
          >
            {step.label}
          </span>
          <span className="sr-only">{STATE_LABELS[step.state]}</span>
        </li>
      ))}
    </ol>
  );
}
