import { useEffect, useRef, useState } from 'react';
import type { Dispatch, RefObject } from 'react';
import type { Camera, DrawingDocument } from '../types';
import type { EditorAction } from '../state/editorReducer';
import { clamp, clampPan, fitCamera, PX_PER_METER, zoomAtPoint } from '../utils/geometry';

interface UseCanvasListenersOptions {
  dispatch: Dispatch<EditorAction>;
  onSizeChange: (size: { w: number; h: number }) => void;
  camera: Camera;
  doc: DrawingDocument;
  size: { w: number; h: number };
  cameraFitNonce: number;
}

export interface CanvasListeners {
  containerRef: RefObject<HTMLDivElement | null>;
  spaceDown: boolean;
  cameraReady: boolean;
}

export function useCanvasListeners({
  dispatch,
  onSizeChange,
  camera,
  doc,
  size,
  cameraFitNonce,
}: UseCanvasListenersOptions): CanvasListeners {
  const containerRef = useRef<HTMLDivElement>(null);
  const [spaceDown, setSpaceDown] = useState(false);
  const cameraRef = useRef(camera);
  const docRef = useRef(doc);
  const sizeRef = useRef(size);
  const fittedNonceRef = useRef(-1);
  const wheelRafRef = useRef<number | null>(null);
  const pendingWheelCameraRef = useRef<Camera | null>(null);

  useEffect(() => {
    cameraRef.current = camera;
  }, [camera]);
  useEffect(() => {
    docRef.current = doc;
  }, [doc]);
  useEffect(() => {
    sizeRef.current = size;
  }, [size]);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) {
      return;
    }
    const observer = new ResizeObserver((entries) => {
      const rect = entries[0].contentRect;
      onSizeChange({ w: Math.round(rect.width), h: Math.round(rect.height) });
    });
    observer.observe(el);
    return () => observer.disconnect();
  }, [onSizeChange]);

  useEffect(() => {
    if (size.w === 0 || size.h === 0) {
      return;
    }
    if (fittedNonceRef.current === cameraFitNonce) {
      return;
    }
    fittedNonceRef.current = cameraFitNonce;
    dispatch({ type: 'setCamera', camera: fitCamera(doc.width, doc.height, size.w, size.h) });
  }, [size, cameraFitNonce, doc.width, doc.height, dispatch]);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) {
      return;
    }
    const flushWheel = () => {
      const pending = pendingWheelCameraRef.current;
      if (pending !== null) {
        pendingWheelCameraRef.current = null;
        wheelRafRef.current = null;
        cameraRef.current = pending;
        dispatch({ type: 'setCamera', camera: pending });
      } else {
        wheelRafRef.current = null;
      }
    };
    const scheduleWheel = (camera: Camera) => {
      pendingWheelCameraRef.current = camera;
      if (wheelRafRef.current === null) {
        wheelRafRef.current = window.requestAnimationFrame(flushWheel);
      }
    };
    const onWheel = (event: WheelEvent) => {
      event.preventDefault();
      const rect = el.getBoundingClientRect();
      const base = pendingWheelCameraRef.current ?? cameraRef.current;
      const isPinchZoom = event.ctrlKey || event.metaKey;
      const isLineMode = event.deltaMode === 1;
      // 트랙패드 두 손가락 스와이프(작은 델타, ctrl 없음)는 패닝으로, 그 외 큰 델타나 ctrl 제스처는 줌으로 처리한다.
      // 마우스 휠(큰 델타/라인 모드)은 기존대로 휠 줌을 유지한다.
      let shouldZoom: boolean;
      if (isPinchZoom) {
        shouldZoom = true;
      } else if (isLineMode) {
        shouldZoom = true;
      } else if (Math.abs(event.deltaY) >= 40 || Math.abs(event.deltaX) >= 40) {
        shouldZoom = true;
      } else {
        shouldZoom = false;
      }

      if (shouldZoom) {
        // 핀치 줌(ctrl)은 델타가 작아도 체감이 나도록 스케일을 키운다. 마우스 휠은 기존 감도 유지.
        const pixelScale = isPinchZoom ? 0.01 : 0.0016;
        const step = clamp(-event.deltaY * (isLineMode ? 0.04 : pixelScale), -0.3, 0.3);
        const factor = Math.exp(step);
        const zoomed = zoomAtPoint(base, { x: event.clientX, y: event.clientY }, rect, factor);
        const viewW = sizeRef.current.w / (zoomed.zoom * PX_PER_METER);
        const viewH = sizeRef.current.h / (zoomed.zoom * PX_PER_METER);
        scheduleWheel(clampPan(zoomed, docRef.current.width, docRef.current.height, viewW, viewH));
      } else {
        const next: Camera = {
          zoom: base.zoom,
          panX: base.panX + event.deltaX / (base.zoom * PX_PER_METER),
          panY: base.panY + event.deltaY / (base.zoom * PX_PER_METER),
        };
        const viewW = sizeRef.current.w / (base.zoom * PX_PER_METER);
        const viewH = sizeRef.current.h / (base.zoom * PX_PER_METER);
        scheduleWheel(clampPan(next, docRef.current.width, docRef.current.height, viewW, viewH));
      }
    };
    el.addEventListener('wheel', onWheel, { passive: false });
    return () => {
      el.removeEventListener('wheel', onWheel);
      if (wheelRafRef.current !== null) {
        window.cancelAnimationFrame(wheelRafRef.current);
        wheelRafRef.current = null;
      }
      pendingWheelCameraRef.current = null;
    };
  }, [dispatch]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA')) {
        return;
      }
      if (event.code === 'Space' && !event.repeat) {
        setSpaceDown(true);
      }
    };
    const onKeyUp = (event: KeyboardEvent) => {
      if (event.code === 'Space') {
        setSpaceDown(false);
      }
    };
    const onBlur = () => {
      setSpaceDown(false);
    };
    window.addEventListener('keydown', onKeyDown);
    window.addEventListener('keyup', onKeyUp);
    window.addEventListener('blur', onBlur);
    return () => {
      window.removeEventListener('keydown', onKeyDown);
      window.removeEventListener('keyup', onKeyUp);
      window.removeEventListener('blur', onBlur);
    };
  }, []);

  const cameraReady =
    size.w > 0 && size.h > 0 && fittedNonceRef.current === cameraFitNonce;

  return { containerRef, spaceDown, cameraReady };
}
