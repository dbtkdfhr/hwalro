import { Check } from 'lucide-react';
import type { ReviewStep } from '../types/home';
import { ReviewProgressScene } from './ReviewProgressScene';

const STATE_LABELS: Record<ReviewStep['state'], string> = {
  done: '완료',
  current: '진행 중',
  upcoming: '예정',
};

export function ReviewProgressStepper({ steps }: { steps: ReviewStep[] }) {
  return (
    <div className="home-review-progress">
      <ReviewProgressScene steps={steps} />
      <ol className="home-review-progress__labels" aria-label="검토 진행 단계">
        {steps.map((step, index) => (
          <li
            key={step.key}
            className={`home-review-progress__step home-review-progress__step--${step.state}`}
            aria-current={step.state === 'current' ? 'step' : undefined}
          >
            <span
              aria-hidden="true"
              className={`home-review-progress__status home-review-progress__status--${step.state}`}
            >
              {step.state === 'done' ? (
                <Check className="h-3.5 w-3.5" strokeWidth={3} />
              ) : (
                index + 1
              )}
            </span>
            <span className="home-review-progress__copy">
              <span className="home-review-progress__label">{step.label}</span>
              <span className="home-review-progress__state">{STATE_LABELS[step.state]}</span>
            </span>
          </li>
        ))}
      </ol>
    </div>
  );
}
