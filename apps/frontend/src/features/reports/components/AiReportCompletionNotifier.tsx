import { useQuery } from '@tanstack/react-query';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { CompletionToast } from '../../../components/notifications/CompletionToast';
import { reportApi } from '../api/reportApi';
import type { AiReportDraftMonitorItem, ReportStatus } from '../types/report';

const POLL_INTERVAL_MS = 3000;

export function findNewlyCompletedAiReports(
  previousStatuses: ReadonlyMap<number, ReportStatus>,
  reports: AiReportDraftMonitorItem[],
): AiReportDraftMonitorItem[] {
  return reports.filter(
    (report) => report.status === '초안' && previousStatuses.get(report.id) !== '초안',
  );
}

function AiReportCompletionNotifier() {
  const [toasts, setToasts] = useState<AiReportDraftMonitorItem[]>([]);
  const previousStatusesRef = useRef<Map<number, ReportStatus> | null>(null);
  const notifiedIdsRef = useRef(new Set<number>());
  const query = useQuery({
    queryKey: ['reports', 'ai-draft-completion-monitor'],
    queryFn: reportApi.listAiDraftMonitor,
    refetchInterval: POLL_INTERVAL_MS,
  });

  const dismiss = useCallback((reportId: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== reportId));
  }, []);

  useEffect(() => {
    if (!query.data) return;

    const currentStatuses = new Map<number, ReportStatus>(
      query.data.map((report) => [report.id, report.status]),
    );
    const previousStatuses = previousStatusesRef.current;
    previousStatusesRef.current = currentStatuses;
    if (!previousStatuses) return;

    const completed = findNewlyCompletedAiReports(previousStatuses, query.data).filter(
      (report) => !notifiedIdsRef.current.has(report.id),
    );
    if (completed.length === 0) return;

    completed.forEach((report) => notifiedIdsRef.current.add(report.id));
    setToasts((current) => [...current, ...completed]);
  }, [query.data]);

  if (toasts.length === 0) return null;

  return (
    <>
      {toasts.map((report) => (
        <CompletionToast
          key={report.id}
          id={report.id}
          title="AI 보고서 초안이 완성되었습니다"
          description={`${report.title} · 보고서 #${report.id}`}
          dismissLabel="AI 보고서 완료 알림 닫기"
          onDismiss={dismiss}
          action={
            <Link
              to={`/reports/${report.id}`}
              onClick={() => dismiss(report.id)}
              className="mt-3 inline-flex text-sm font-bold text-primary hover:underline focus-visible:rounded focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
            >
              보고서 보기
            </Link>
          }
        />
      ))}
    </>
  );
}

export default AiReportCompletionNotifier;
