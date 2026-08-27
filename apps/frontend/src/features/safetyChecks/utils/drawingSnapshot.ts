import { apiClient } from '../../../api/client';
import { renderMinimapPlan } from '../../simulations/components/SimulationMinimap';
import type { MinimapDrawing } from '../../simulations/components/SimulationMinimap';

export interface LayoutDrawingContext {
  layoutId: number;
  layoutVersionId: number | null;
  layoutVersionNumber: number | null;
  layoutTitle: string;
  drawing: MinimapDrawing & {
    layoutTexts: Array<{ text: string; x: number; y: number }>;
  };
}

const SNAPSHOT_WIDTH = 1200;
const SNAPSHOT_HEIGHT = 900;

export async function fetchLayoutDrawingContext(layoutId: number): Promise<LayoutDrawingContext> {
  const contexts = await apiClient
    .post<LayoutDrawingContext[]>('/api/drawings/drawing-contexts', { layoutIds: [layoutId] })
    .then((response) => response.data);
  const context = contexts[0];
  if (!context) {
    throw new Error('도면을 찾을 수 없습니다.');
  }
  return context;
}

export async function renderDrawingSnapshot(
  drawing: MinimapDrawing,
  width = SNAPSHOT_WIDTH,
  height = SNAPSHOT_HEIGHT,
): Promise<Blob> {
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const context = canvas.getContext('2d');
  if (!context) {
    throw new Error('브라우저가 캔버스 렌더링을 지원하지 않습니다.');
  }
  renderMinimapPlan(context, drawing, width, height);
  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) {
        resolve(blob);
      } else {
        reject(new Error('도면 이미지를 생성하지 못했습니다.'));
      }
    }, 'image/png');
  });
}
