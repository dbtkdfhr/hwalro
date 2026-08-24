import { AlertTriangle, Ban } from 'lucide-react';
import { Button, Modal } from '../../../components/ui';
import type { SimulationExecution, SimulationOverview } from '../types';

interface Props {
  simulation: SimulationOverview | null;
  execution: SimulationExecution | null;
  isLoading: boolean;
  isRetrying: boolean;
  error: string | null;
  onClose: () => void;
  onRetry: () => void;
}

function formatPosition(position: { x: number; y: number }): string {
  return `x ${position.x.toFixed(2)}m, y ${position.y.toFixed(2)}m`;
}

export function SimulationStatusDialog({
  simulation,
  execution,
  isLoading,
  isRetrying,
  error,
  onClose,
  onRetry,
}: Props) {
  if (!simulation) return null;

  const failed = simulation.status === 'FAILED';
  const failureDetail = execution?.failureDetail;

  return (
    <Modal
      open
      onClose={() => {
        if (!isRetrying) onClose();
      }}
      title={failed ? '시뮬레이션 실행 실패' : '시뮬레이션 실행 취소'}
      description={`${simulation.title || simulation.layoutTitle} · 시뮬레이션 #${simulation.id}`}
      size="sm"
      footer={
        <>
          <Button type="button" variant="secondary" onClick={onClose} disabled={isRetrying}>
            닫기
          </Button>
          <Button type="button" onClick={onRetry} isLoading={isRetrying} disabled={isLoading}>
            {failed ? '동일 설정으로 재시도' : '동일 설정으로 재실행'}
          </Button>
        </>
      }
    >
      <div className="flex gap-3">
        <div
          className={`flex size-10 shrink-0 items-center justify-center rounded-full ${
            failed
              ? 'bg-danger-soft text-danger-strong shadow-neu-pressed'
              : 'bg-surface-sunken text-text-muted shadow-neu-pressed'
          }`}
        >
          {failed ? (
            <AlertTriangle aria-hidden="true" className="size-5" />
          ) : (
            <Ban aria-hidden="true" className="size-5" />
          )}
        </div>
        <div className="min-w-0 flex-1">
          {failed ? (
            isLoading ? (
              <p className="text-sm text-text-muted">실패 사유를 불러오는 중입니다.</p>
            ) : (
              <>
                <p className="text-sm font-bold leading-6 text-danger-strong">
                  {execution?.failureMessage ?? '시뮬레이션 실행에 실패했습니다.'}
                </p>
                {failureDetail && failureDetail.code === 'AGENT_ROUTE_UNREACHABLE' && (
                  <dl className="mt-4 space-y-2 rounded-xl border border-line bg-surface-sunken p-4 text-xs leading-5 text-text-strong shadow-neu-pressed">
                    <div>
                      <dt className="inline font-bold">대상 에이전트: </dt>
                      <dd className="inline">#{failureDetail.agentId}</dd>
                    </div>
                    <div>
                      <dt className="inline font-bold">현재 위치: </dt>
                      <dd className="inline">{formatPosition(failureDetail.currentPosition)}</dd>
                    </div>
                    {failureDetail.recommendedPosition && (
                      <div>
                        <dt className="inline font-bold">추천 위치: </dt>
                        <dd className="inline">
                          {formatPosition(failureDetail.recommendedPosition)}
                        </dd>
                      </div>
                    )}
                  </dl>
                )}
                {failureDetail && failureDetail.code === 'NO_REACHABLE_SELECTED_EXIT' && (
                  <dl className="mt-4 space-y-2 rounded-xl border border-line bg-surface-sunken p-4 text-xs leading-5 text-text-strong shadow-neu-pressed">
                    <div>
                      <dt className="inline font-bold">연결되지 않은 인원: </dt>
                      <dd className="inline">{failureDetail.affectedAgentCount}명</dd>
                    </div>
                    <div>
                      <dt className="inline font-bold">대상 출입구: </dt>
                      <dd className="inline">{failureDetail.selectedExitIds.join(', ')}</dd>
                    </div>
                  </dl>
                )}
              </>
            )
          ) : (
            <>
              <p className="text-sm font-bold text-text-strong">실행을 취소했습니다.</p>
              <p className="mt-2 text-sm leading-6 text-text-muted">
                필요하면 같은 설정으로 다시 실행할 수 있습니다.
              </p>
            </>
          )}
          {error && (
            <p
              role="alert"
              className="mt-4 rounded-lg border border-danger/25 bg-danger-soft px-3 py-2 text-sm text-danger-strong"
            >
              {error}
            </p>
          )}
        </div>
      </div>
    </Modal>
  );
}
