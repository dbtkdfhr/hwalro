import { useId } from 'react';
import type { CSSProperties } from 'react';
import type { ReviewStep } from '../types/home';

const STATION_POINTS = [
  { x: 60, y: 78 },
  { x: 260, y: 42 },
  { x: 460, y: 78 },
  { x: 660, y: 42 },
] as const;

const ROUTE_PATH = 'M60 78C150 78 168 42 260 42S368 78 460 78S568 42 660 42';

export function ReviewProgressScene({ steps }: { steps: ReviewStep[] }) {
  const clipId = useId().replace(/:/g, '');
  const currentIndex = steps.findIndex((step) => step.state === 'current');
  const lastDoneIndex = steps.reduce(
    (lastIndex, step, index) => (step.state === 'done' ? index : lastIndex),
    -1,
  );
  const progressIndex = Math.max(currentIndex, lastDoneIndex, 0);
  const progressX = STATION_POINTS[Math.min(progressIndex, STATION_POINTS.length - 1)].x + 16;

  return (
    <div className="home-review-scene" aria-hidden="true">
      <svg className="home-review-scene__route" viewBox="0 0 720 120" preserveAspectRatio="none">
        <defs>
          <clipPath id={clipId}>
            <rect x="0" y="0" width={progressX} height="120" />
          </clipPath>
        </defs>

        <path className="home-review-scene__track" d={ROUTE_PATH} pathLength="100" />
        <path
          className="home-review-scene__track home-review-scene__track--active"
          d={ROUTE_PATH}
          pathLength="100"
          clipPath={`url(#${clipId})`}
        />
        <path
          className="home-review-scene__signal"
          d={ROUTE_PATH}
          pathLength="100"
          clipPath={`url(#${clipId})`}
        />
      </svg>

      {steps.map((step, index) => {
        const point = STATION_POINTS[index] ?? STATION_POINTS[STATION_POINTS.length - 1];
        const style = {
          '--station-x': `${(point.x / 720) * 100}%`,
          '--station-y': `${(point.y / 120) * 100}%`,
        } as CSSProperties;

        return (
          <span
            key={step.key}
            className={`home-review-scene__station home-review-scene__station--${step.state}`}
            style={style}
          >
            <span className="home-review-scene__station-core">{index + 1}</span>
          </span>
        );
      })}
    </div>
  );
}
