import { selectTopBottlenecks } from '../../simulationResult/utils/bottleneckDisplay';
import { SimulationMinimap } from '../../simulations/components/SimulationMinimap';
import type { MinimapHighlight } from '../../simulations/components/SimulationMinimap';
import type { ReportVisualContext } from '../types/report';

type CardVariant = 'document' | 'settings' | 'preview';

interface Props {
  resultId: number;
  label: string;
  tone: 'primary' | 'compare';
  context?: ReportVisualContext;
  isLoading: boolean;
  error: string | null;
  variant: CardVariant;
}

const VARIANT_SIZE: Record<CardVariant, { width: number; height: number }> = {
  document: { width: 480, height: 280 },
  settings: { width: 240, height: 150 },
  preview: { width: 400, height: 240 },
};

export function ReportSimulationCard({
  resultId,
  label,
  tone,
  context,
  isLoading,
  error,
  variant,
}: Props) {
  const size = VARIANT_SIZE[variant];
  const cardPadding = variant === 'settings' ? 'p-3' : 'p-4';
  const toneClass =
    tone === 'primary' ? 'border-primary/20 bg-primary-soft' : 'border-line bg-surface';
  const topBottlenecks = context ? selectTopBottlenecks(context.bottlenecks) : [];
  const highlights: MinimapHighlight[] = context
    ? [
        ...topBottlenecks.map((bottleneck, index) => ({
          ...bottleneck.geometry,
          label: `B${index + 1}`,
          kind: 'bottleneck' as const,
        })),
        ...(context.riskZones ?? []).map((risk, index) => ({
          ...risk.geometry,
          label: `R${index + 1}`,
          kind: 'risk' as const,
        })),
      ]
    : [];

  return (
    <div
      className={`break-inside-avoid overflow-hidden rounded-xl border ${cardPadding} ${toneClass}`}
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="font-bold text-ink">결과 #{resultId}</p>
          <p className="mt-1 text-xs text-text-muted">{context?.layoutTitle ?? label}</p>
        </div>
        <span className="shrink-0 rounded-full bg-white/80 px-2 py-1 text-[11px] font-bold text-text-muted">
          {label}
        </span>
      </div>

      <div className="mt-3 overflow-hidden rounded-lg border border-line bg-white">
        {isLoading ? (
          <div className="flex aspect-[8/5] items-center justify-center px-3 text-xs text-text-muted">
            미니맵을 불러오는 중입니다.
          </div>
        ) : error ? (
          <div className="flex aspect-[8/5] items-center justify-center px-3 text-center text-xs text-danger">
            {error}
          </div>
        ) : context ? (
          <SimulationMinimap
            drawing={context.drawing}
            highlights={highlights}
            ariaLabel={`결과 ${resultId} 도면과 상위 병목 ${topBottlenecks.length}개, 주의 구역 ${context.riskZones?.length ?? 0}개 미니맵`}
            width={size.width}
            height={size.height}
            className="block h-auto w-full"
            responsive
          />
        ) : (
          <div className="flex aspect-[8/5] items-center justify-center px-3 text-center text-xs text-text-muted">
            도면 정보를 불러올 수 없습니다.
          </div>
        )}
      </div>

      {!isLoading && !error && context && (
        <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1 text-[11px] font-bold text-text-muted">
          <span>
            <span className="mr-1 inline-block h-2 w-2 rounded-sm bg-[#c94f47]" />
            {topBottlenecks.length > 0
              ? `상위 병목 ${topBottlenecks.length}개`
              : '감지된 병목 없음'}
          </span>
          <span>
            <span className="mr-1 inline-block h-2 w-2 rounded-sm bg-[#d97706]" />
            주의 구역 {context.riskZones?.length ?? 0}개
          </span>
        </div>
      )}
    </div>
  );
}
