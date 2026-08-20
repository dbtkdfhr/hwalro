import type { Dispatch } from 'react';
import type { EditorState } from '../types';
import type { EditorAction } from '../state/editorReducer';
import { clampFitZoom, clampPan, PX_PER_METER, zoomAtPoint } from '../utils/geometry';

interface ZoomControlProps {
  state: EditorState;
  dispatch: Dispatch<EditorAction>;
  size: { w: number; h: number };
  className?: string;
}

export function ZoomControl({ state, dispatch, size, className }: ZoomControlProps) {
  const { camera, doc } = state;

  const zoomAtCenter = (factor: number) => {
    if (size.w === 0 || size.h === 0) {
      return;
    }
    const zoomed = zoomAtPoint(
      camera,
      { x: size.w / 2, y: size.h / 2 },
      { left: 0, top: 0 },
      factor,
    );
    const viewW = size.w / (zoomed.zoom * PX_PER_METER);
    const viewH = size.h / (zoomed.zoom * PX_PER_METER);
    dispatch({ type: 'setCamera', camera: clampPan(zoomed, doc.width, doc.height, viewW, viewH) });
  };

  const fit = () => {
    if (size.w === 0 || size.h === 0) {
      return;
    }
    const zoom = (Math.min(size.w / doc.width, size.h / doc.height) * 0.95) / PX_PER_METER;
    const clamped = clampFitZoom(zoom);
    dispatch({
      type: 'setCamera',
      camera: {
        zoom: clamped,
        panX: (doc.width - size.w / (clamped * PX_PER_METER)) / 2,
        panY: (doc.height - size.h / (clamped * PX_PER_METER)) / 2,
      },
    });
  };

  return (
    <div className={`layout-zoom-control ${className ?? ''}`}>
      <button
        type="button"
        onClick={() => zoomAtCenter(1 / 1.25)}
        aria-label="축소"
        className="layout-zoom-control__icon"
      >
        −
      </button>
      <span className="layout-zoom-control__value">{Math.round(camera.zoom * 100)}%</span>
      <button
        type="button"
        onClick={() => zoomAtCenter(1.25)}
        aria-label="확대"
        className="layout-zoom-control__icon"
      >
        +
      </button>
      <button type="button" onClick={fit} className="layout-zoom-control__fit">
        맞춤
      </button>
    </div>
  );
}
