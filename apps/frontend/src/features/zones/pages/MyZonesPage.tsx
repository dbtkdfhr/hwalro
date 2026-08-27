import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { MapPin } from 'lucide-react';
import { Card, EmptyState, ErrorState, PageHeader, buttonClassName } from '../../../components/ui';
import { getDrawingErrorMessage } from '../../drawings/utils/getDrawingErrorMessage';
import { zoneApi, type MyZone } from '../api/zoneApi';

const ZONE_TYPE_LABELS: Record<string, string> = {
  WORK: '작업',
  STORAGE: '보관',
  PASSAGE: '통로',
  EXCLUSION: '배치 제외',
  OTHER: '기타',
};

function MyZonesPage() {
  const [zones, setZones] = useState<MyZone[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    zoneApi
      .myZones()
      .then((list) => {
        if (active) {
          setZones(list);
          setErrorMessage(null);
        }
      })
      .catch((error: unknown) => {
        if (active) setErrorMessage(getDrawingErrorMessage(error));
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <main className="bg-background">
      <div className="mx-auto w-full max-w-[1360px] px-1 pt-2 pb-10 sm:px-4 lg:pt-4">
        <div className="border-b border-line pb-6">
          <PageHeader
            eyebrow="내 구역"
            title="담당 구역"
            description="배정된 구역과 지정된 비상구를 확인하고, 대피 경로를 볼 수 있습니다."
          />
        </div>

        {isLoading ? (
          <Card className="mt-5">
            <p className="py-10 text-center text-sm text-text-muted">담당 구역을 불러오는 중...</p>
          </Card>
        ) : errorMessage !== null ? (
          <Card className="mt-5">
            <ErrorState message={errorMessage} className="w-full" />
          </Card>
        ) : zones.length === 0 ? (
          <Card className="mt-5">
            <EmptyState
              icon={MapPin}
              title="배정된 구역이 없습니다."
              description="안전 담당자에게 문의하세요."
            />
          </Card>
        ) : (
          <ul className="mt-5 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {zones.map((zone) => (
              <li key={zone.zoneId}>
                <Card className="flex h-full flex-col">
                  <p className="text-xs font-bold tracking-[0.08em] text-text-muted uppercase">
                    {ZONE_TYPE_LABELS[zone.zoneType] ?? zone.zoneType}
                  </p>
                  <h2 className="mt-1 truncate text-lg font-black text-text-strong">
                    {zone.zoneName}
                  </h2>
                  <p className="mt-1 truncate text-sm text-text-muted">{zone.drawingTitle}</p>

                  <dl className="mt-4 space-y-1.5 text-sm">
                    <div className="flex items-center justify-between gap-3">
                      <dt className="shrink-0 text-text-muted">기본 비상구</dt>
                      <dd className="truncate font-medium text-text-strong">
                        {zone.defaultExitName ?? '지정 안 함'}
                      </dd>
                    </div>
                  </dl>

                  <div className="mt-5">
                    <Link
                      to={`/my-zones/${zone.zoneId}/evacuation`}
                      className={buttonClassName({ variant: 'primary', size: 'sm' })}
                    >
                      대피 안내
                    </Link>
                  </div>
                </Card>
              </li>
            ))}
          </ul>
        )}
      </div>
    </main>
  );
}

export default MyZonesPage;
