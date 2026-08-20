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
    const onWheel = (event: WheelEvent) => {
      event.preventDefault();
      const rect = el.getBoundingClientRect();
      const current = cameraRef.current;
      const step = clamp(-event.deltaY * (event.deltaMode === 1 ? 0.04 : 0.0016), -0.3, 0.3);
      const factor = Math.exp(step);
      const zoomed = zoomAtPoint(current, { x: event.clientX, y: event.clientY }, rect, factor);
      const viewW = sizeRef.current.w / (zoomed.zoom * PX_PER_METER);
      const viewH = sizeRef.current.h / (zoomed.zoom * PX_PER_METER);
      dispatch({
        type: 'setCamera',
        camera: clampPan(zoomed, docRef.current.width, docRef.current.height, viewW, viewH),
      });
    };
    el.addEventListener('wheel', onWheel, { passive: false });
    return () => el.removeEventListener('wheel', onWheel);
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

  return { containerRef, spaceDown };
}
