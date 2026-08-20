import { useCallback, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Chart } from '@tanstack/charts/react/tooltip';
import { areaY, d3Curve, defineChart, lineY } from '@tanstack/charts';
import { scaleLinear } from '@tanstack/charts/scales/linear';
import { tooltip } from '@tanstack/charts/tooltip';
import { curveMonotoneX } from 'd3-shape';
import type { ChartBounds, ChartRenderContext } from '@tanstack/charts';
import type { ChartTooltipBodyRenderContext } from '@tanstack/charts/react/tooltip';
import type { EvacuationPoint } from '../types';
import { calculateEvacuationRate } from '../utils/evacuationRate';
import { getEvacuationProgressDisplay } from '../utils/evacuationProgressDisplay';
import { formatDuration } from '../utils/playback';

const GRADIENT_ID = 'evacuation-gradient';

export function EvacuationProgressChart({
  points,
  currentTime,
  duration,
  totalPeople,
  isPlaybackDataStale = false,
  isCollapsing = false,
  isExpanding = false,
  onCollapseEnd,
  onExpandEnd,
}: {
  points: EvacuationPoint[];
  currentTime: number;
  duration: number;
  totalPeople: number;
  isPlaybackDataStale?: boolean;
  isCollapsing?: boolean;
  isExpanding?: boolean;
  onCollapseEnd?: () => void;
  onExpandEnd?: () => void;
}) {
  const safeDuration = duration > 0 ? duration : 1;
  const safeTotalPeople = totalPeople > 0 ? totalPeople : 1;

  const visibleCount = useMemo(() => {
    let count = 0;
    for (const point of points) {
      if (point.timeSeconds <= currentTime) count += 1;
    }
    return count;
  }, [points, currentTime]);

  const visible = useMemo(() => points.slice(0, visibleCount), [points, visibleCount]);

  const current = visible.length > 0 ? visible[visible.length - 1].evacuatedCount : 0;
  const ratePercent = calculateEvacuationRate(current, totalPeople);
  const evacuationProgress = getEvacuationProgressDisplay(
    current,
    ratePercent,
    isPlaybackDataStale,
  );

  const definition = useMemo(() => {
    const curve = d3Curve(curveMonotoneX);
    return defineChart({
      marks: [
        areaY(visible, {
          x: 'timeSeconds',
          y: 'evacuatedCount',
          key: 'timeSeconds',
          fill: `url(#${GRADIENT_ID})`,
          fillOpacity: 1,
          curve,
        }),
        lineY(visible, {
          x: 'timeSeconds',
          y: 'evacuatedCount',
          key: 'timeSeconds',
          stroke: 'var(--color-primary)',
          strokeWidth: 2.25,
          curve,
        }),
      ],
      x: {
        scale: scaleLinear([0, safeDuration], [0, 1]),
        axis: {
          line: false,
          ticks: {
            count: 5,
            format: (value) => formatDuration(Number(value)),
          },
          tickLabels: { fontSize: 9, dy: 5, thin: true },
        },
      },
      y: {
        scale: scaleLinear([0, safeTotalPeople], [0, 1]),
        grid: true,
        axis: {
          line: false,
          ticks: {
            count: 3,
            format: (value) => Math.round(Number(value)).toLocaleString('ko-KR'),
          },
          tickLabels: { fontSize: 9, dx: -2, thin: true },
        },
      },
      gradients: [
        {
          id: GRADIENT_ID,
          stops: [
            { offset: 0, color: 'var(--color-primary)', opacity: 0.02 },
            { offset: 0.6, color: 'var(--color-primary)', opacity: 0.24 },
            { offset: 1, color: 'var(--color-primary)', opacity: 0.55 },
          ],
        },
      ],
      tooltip: {
        use: tooltip,
        className: 'evacuation-chart-tooltip',
        placement: 'top',
        offset: 10,
      },
      margin: { top: 8 },
      clip: true,
      theme: {
        foreground: 'var(--color-text-strong)',
        muted: 'var(--color-text-muted)',
        grid: 'var(--color-line)',
        background: 'transparent',
      },
    });
  }, [visible, safeDuration, safeTotalPeople]);

  const [plotBounds, setPlotBounds] = useState<ChartBounds | null>(null);
  const plotRef = useRef<HTMLDivElement | null>(null);
  const [plotWidth, setPlotWidth] = useState<number | null>(null);

  useLayoutEffect(() => {
    const plot = plotRef.current;
    if (!plot) return;
    const updateWidth = () => {
      const next = plot.offsetWidth;
      setPlotWidth((previous) => (next > 0 && next !== previous ? next : previous));
    };
    updateWidth();
    const observer = new ResizeObserver(updateWidth);
    observer.observe(plot);
    return () => observer.disconnect();
  }, []);

  const handleRender = useCallback(
    (context: ChartRenderContext<EvacuationPoint, number, number>) => {
      const next = context.scene.chart;
      setPlotBounds((previous) =>
        previous &&
        previous.x === next.x &&
        previous.y === next.y &&
        previous.width === next.width &&
        previous.height === next.height
          ? previous
          : next,
      );
    },
    [],
  );

  const renderTooltipBody = useCallback(
    ({
      points,
    }: {
      points: ChartTooltipBodyRenderContext<EvacuationPoint, number, number>['points'];
    }) => {
      const point = points[0];
      if (!point) return null;
      return (
        <>
          <span>{formatDuration(Number(point.xValue))}</span>
          <strong>{Math.round(Number(point.yValue)).toLocaleString('ko-KR')}명</strong>
        </>
      );
    },
    [],
  );

  const playheadX =
    plotBounds === null
      ? 0
      : plotBounds.x + (Math.min(currentTime, safeDuration) / safeDuration) * plotBounds.width;

  return (
    <section
      className={`evacuation-chart ${isCollapsing ? 'is-collapsing' : ''} ${isExpanding ? 'is-expanding' : ''}`}
      aria-label="시간별 대피 인원"
      onAnimationEnd={(event) => {
        if (event.currentTarget !== event.target) return;
        if (isCollapsing) onCollapseEnd?.();
        if (isExpanding) onExpandEnd?.();
      }}
    >
      <div className="floating-title-row">
        <strong>시간별 대피 인원</strong>
        <span aria-busy={isPlaybackDataStale} aria-live="polite">
          {evacuationProgress.countLabel} · {evacuationProgress.rateLabel}
        </span>
      </div>
      <div className="evacuation-chart-plot" ref={plotRef}>
        <Chart
          definition={definition}
          ariaLabel="시간별 대피 인원"
          height={150}
          width={plotWidth ?? undefined}
          onRender={handleRender}
          renderTooltipBody={renderTooltipBody}
        />
        {plotBounds !== null && (
          <div
            className="evacuation-chart-playhead"
            style={{
              transform: `translateX(${playheadX}px)`,
              top: plotBounds.y,
              height: plotBounds.height,
            }}
            aria-hidden="true"
          >
            <span className="evacuation-chart-playhead-dot" />
          </div>
        )}
      </div>
    </section>
  );
}
