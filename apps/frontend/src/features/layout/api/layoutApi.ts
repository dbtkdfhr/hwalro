import { AxiosError } from 'axios';
import type { DrawingDocument } from '../types';
import { fromSerialized, toSerialized } from '../utils/serialization';
import { drawingApi } from '../../drawings/api/drawingApi';
import type { DrawingLayoutVersionStatus } from '../../drawings/types/drawing';

export interface DrawingSession {
  doc: DrawingDocument;
  description: string | null;
  version: number;
  layoutVersionId: number;
  layoutVersionNumber: number;
  layoutVersionStatus: DrawingLayoutVersionStatus;
}

export async function fetchDrawing(id: string): Promise<DrawingSession | null> {
  try {
    const drawing = await drawingApi.get(Number(id));
    const doc = fromSerialized({
      name: drawing.title,
      width: drawing.width,
      height: drawing.height,
      walls: drawing.walls,
      outsideWalls: drawing.outsideWalls,
      exits: drawing.exits,
      pillars: drawing.pillars,
      fabrics: drawing.fabrics,
      layoutTexts: drawing.layoutTexts,
    });
    return {
      doc,
      description: drawing.description,
      version: drawing.version,
      layoutVersionId: drawing.layoutVersionId,
      layoutVersionNumber: drawing.layoutVersionNumber,
      layoutVersionStatus: drawing.layoutVersionStatus,
    };
  } catch (error) {
    if (error instanceof AxiosError && error.response?.status === 404) {
      return null;
    }
    throw error;
  }
}

export async function saveDrawing(id: string, session: DrawingSession): Promise<number> {
  const serialized = toSerialized(session.doc);
  const drawing = await drawingApi.update(Number(id), {
    title: serialized.name,
    description: session.description,
    walls: serialized.walls,
    outsideWalls: serialized.outsideWalls,
    exits: serialized.exits,
    pillars: serialized.pillars,
    fabrics: serialized.fabrics,
    layoutTexts: serialized.layoutTexts,
    expectedVersion: session.version,
  });
  return drawing.version;
}
