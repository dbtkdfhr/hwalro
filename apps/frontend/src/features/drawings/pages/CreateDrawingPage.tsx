import { useEffect, useEffectEvent, useRef, useState } from 'react';
import type { PointerEvent as ReactPointerEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Building2, FileText } from 'lucide-react';
import { BUILDING, LINKED_FLOOR_IDS, type FloorId } from '../config/buildingFloors';
import { createBuildingScene, type BuildingSceneHandle } from '../three/createBuildingScene';
import { useRecordLastActivity } from '../../home/hooks/useRecordLastActivity';
import { useCreateDrawing } from '../hooks/useDrawingMutations';
import { getDrawingErrorMessage } from '../utils/getDrawingErrorMessage';
import '../floorPicker.css';

type Phase = 'picking' | 'diving' | 'creating' | 'transitioning';

const TRANSITION_DURATION_MS = 460;

function CreateDrawingPage() {
  const navigate = useNavigate();
  const createDrawing = useCreateDrawing();
  const recordLastActivity = useRecordLastActivity();
  const sceneHostRef = useRef<HTMLDivElement>(null);
  const sceneHandleRef = useRef<BuildingSceneHandle | null>(null);
  const cancelledRef = useRef(false);
  const phaseRef = useRef<Phase>('picking');
  const [phase, setPhase] = useState<Phase>('picking');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const enterPhase = (next: Phase) => {
    phaseRef.current = next;
    setPhase(next);
  };

  const openDrawing = async (drawingId: number) => {
    recordLastActivity('LAYOUT_EDIT', drawingId);
    enterPhase('transitioning');
    if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      await new Promise((resolve) => window.setTimeout(resolve, TRANSITION_DURATION_MS));
    }
    if (cancelledRef.current) return;
    navigate(`/layout/${drawingId}`, { state: { fadeInLayout: true } });
  };

  const startWithLinkedFloor = async (floorId: FloorId) => {
    if (phaseRef.current !== 'picking') return;
    enterPhase('diving');
    setErrorMessage(null);
    try {
      const [drawing] = await Promise.all([
        createDrawing.mutateAsync({
          title: null,
          description: null,
          withDefaultData: true,
        }),
        sceneHandleRef.current?.diveToFloor(floorId) ?? Promise.resolve(),
      ]);
      if (cancelledRef.current) return;
      await openDrawing(drawing.id);
    } catch (error) {
      if (cancelledRef.current) return;
      sceneHandleRef.current?.cancelDive();
      enterPhase('picking');
      setErrorMessage(getDrawingErrorMessage(error));
    }
  };

  const handleSceneConfirm = useEffectEvent((floorId: FloorId) => {
    void startWithLinkedFloor(floorId);
  });

  useEffect(() => {
    cancelledRef.current = false;
    const host = sceneHostRef.current;
    if (!host) return;
    const handle = createBuildingScene(host, {
      selectableIds: LINKED_FLOOR_IDS,
      onConfirm: handleSceneConfirm,
    });
    sceneHandleRef.current = handle;
    return () => {
      cancelledRef.current = true;
      handle.dispose();
      sceneHandleRef.current = null;
    };
  }, []);

  const startWithEmptyCanvas = async () => {
    if (phaseRef.current !== 'picking') return;
    enterPhase('creating');
    setErrorMessage(null);
    try {
      const drawing = await createDrawing.mutateAsync({
        title: null,
        description: null,
        withDefaultData: false,
      });
      if (cancelledRef.current) return;
      await openDrawing(drawing.id);
    } catch (error) {
      if (cancelledRef.current) return;
      enterPhase('picking');
      setErrorMessage(getDrawingErrorMessage(error));
    }
  };

  const handleOverlayPointerDown = (event: ReactPointerEvent<HTMLElement>) => {
    event.stopPropagation();
  };

  const pending = phase !== 'picking';
  const focusingScene = phase === 'diving' || phase === 'transitioning';

  return (
    <div
      className={`floor-picker-page relative h-[100dvh] overflow-hidden bg-background text-ink ${focusingScene ? 'floor-picker-page--focused' : ''}`}
    >
      <h1 className="sr-only">새 도면 등록</h1>
      <div
        ref={sceneHostRef}
        className="absolute inset-0"
        role="img"
        aria-label={`${BUILDING.name} 건물 3D 뷰. 도면이 연결된 지하 2층(B2) 슬래브를 클릭하면 해당 층으로 확대되며 도면 등록이 시작됩니다.`}
      />

      <header
        className="floor-picker-chrome absolute top-5 left-5 z-10 max-w-[300px]"
        onPointerDown={handleOverlayPointerDown}
      >
        <button
          type="button"
          onClick={() => navigate(-1)}
          disabled={pending}
          className="inline-flex items-center gap-1.5 rounded-lg border border-line bg-white/85 px-3 py-1.5 text-xs font-bold text-text-muted shadow-sm backdrop-blur-md transition hover:border-line-strong hover:text-ink disabled:cursor-not-allowed disabled:opacity-50"
        >
          <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
          나가기
        </button>
        <div className="mt-3 rounded-2xl border border-line bg-white/85 p-5 shadow-sm backdrop-blur-md max-md:p-4">
          <p className="text-[11px] font-bold tracking-[0.22em] text-primary">새 도면 등록</p>
          <p className="mt-1.5 text-xl font-black tracking-tight text-ink">
            검토할 층을 선택하세요
          </p>
          <p className="mt-2 text-xs leading-5 text-text-muted">
            건물에서 층을 클릭하면 해당 층으로 확대되며 도면이 생성됩니다.
          </p>
          <p className="mt-3 flex items-start gap-1.5 border-t border-line pt-3 text-[11px] leading-4 text-text-muted">
            <span className="mt-1 h-1.5 w-1.5 shrink-0 rounded-full bg-lime" aria-hidden="true" />
            현재 지하 2층(B2)에만 기본 도면이 연결되어 있습니다.
          </p>
        </div>
      </header>

      <div
        className="floor-picker-chrome pointer-events-none absolute bottom-5 left-5 z-10 hidden flex-wrap items-center gap-2 md:flex"
        aria-hidden="true"
      >
        <span className="rounded-full border border-line bg-white/80 px-3 py-1.5 text-[11px] text-text-muted backdrop-blur-md">
          드래그 — 회전
        </span>
        <span className="rounded-full border border-line bg-white/80 px-3 py-1.5 text-[11px] text-text-muted backdrop-blur-md">
          휠 — 확대·축소
        </span>
        <span className="rounded-full border border-line bg-white/80 px-3 py-1.5 text-[11px] font-bold text-primary backdrop-blur-md">
          지하 2층 클릭 — 도면 등록
        </span>
      </div>

      <div
        className="floor-picker-chrome absolute right-5 bottom-5 z-10 flex flex-col items-stretch gap-2 max-md:right-4 max-md:bottom-4 max-md:left-4"
        onPointerDown={handleOverlayPointerDown}
      >
        <button
          type="button"
          onClick={() => void startWithLinkedFloor('B2')}
          disabled={pending}
          className="flex h-11 items-center justify-center gap-2 rounded-lg bg-lime px-5 text-sm font-bold text-ink transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
        >
          <Building2 className="h-4 w-4" aria-hidden="true" />
          지하 2층(B2)으로 시작
        </button>
        <button
          type="button"
          onClick={() => void startWithEmptyCanvas()}
          disabled={pending}
          className="flex h-11 items-center justify-center gap-2 rounded-lg border border-line bg-white/85 px-5 text-sm font-bold text-ink/75 shadow-sm backdrop-blur-md transition hover:border-line-strong hover:text-ink disabled:cursor-not-allowed disabled:opacity-50"
        >
          <FileText className="h-4 w-4" aria-hidden="true" />빈 도면으로 시작
        </button>
      </div>

      <div aria-live="polite">
        {phase === 'diving' && (
          <div
            role="status"
            className="pointer-events-none absolute inset-x-0 bottom-28 z-10 flex justify-center"
          >
            <span className="flex items-center gap-2 rounded-full border border-line-strong bg-white/90 px-4 py-2 text-sm font-bold text-primary shadow-raised backdrop-blur-md">
              <span className="h-2 w-2 animate-pulse rounded-full bg-lime" aria-hidden="true" />
              선택한 층으로 이동하는 중...
            </span>
          </div>
        )}
        {phase === 'creating' && (
          <div
            role="status"
            className="pointer-events-none absolute inset-x-0 bottom-28 z-10 flex justify-center"
          >
            <span className="flex items-center gap-2 rounded-full border border-line bg-white/90 px-4 py-2 text-sm font-bold text-ink shadow-raised backdrop-blur-md">
              <span className="h-2 w-2 animate-pulse rounded-full bg-lime" aria-hidden="true" />
              도면을 만들고 있는 중...
            </span>
          </div>
        )}
      </div>

      {errorMessage !== null && (
        <div
          role="alert"
          className="absolute bottom-24 left-1/2 z-20 flex max-w-[min(420px,calc(100vw-2rem))] -translate-x-1/2 items-center gap-2 rounded-lg border border-danger bg-white px-3 py-2 text-sm text-danger shadow-raised"
        >
          <span className="min-w-0">{errorMessage}</span>
          <button
            type="button"
            onClick={() => setErrorMessage(null)}
            aria-label="오류 메시지 닫기"
            className="shrink-0 transition-opacity hover:opacity-70"
          >
            ×
          </button>
        </div>
      )}

      {phase === 'transitioning' && (
        <div className="floor-picker-transition-cover" aria-hidden="true" />
      )}
    </div>
  );
}

export default CreateDrawingPage;
