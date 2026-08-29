import { apiClient } from '../../../api/client';

export interface ZoneRect {
  x: number;
  y: number;
  width: number;
  height: number;
}

/** EXCLUSION은 배치 개선안 탐색이 구조물을 놓지 못하는 영역이다. 사각형 편집기를 따로 두지 않고 구역으로 표현한다. */
export type ZoneType = 'WORK' | 'STORAGE' | 'PASSAGE' | 'EXCLUSION' | 'OTHER';

export type ZoneElementKind = 'WALL' | 'PILLAR' | 'FABRIC';
export type MovementPolicy = 'FREE' | 'WITHIN_ZONE' | 'FIXED';

export interface ZoneMember {
  kind: ZoneElementKind;
  id: number;
}

export interface LayoutZone {
  zoneId: number;
  name: string;
  zoneType: ZoneType;
  rect: ZoneRect;
  assignedUserId: number | null;
  defaultExitId: number | null;
  displayOrder: number;
  members: ZoneMember[];
}

export interface StructureConstraint {
  fabricId: number;
  zoneId: number | null;
  movementPolicy: MovementPolicy;
}

export interface LayoutMetadata {
  layoutId: number;
  layoutVersionId: number;
  zones: LayoutZone[];
  structureConstraints: StructureConstraint[];
}

export interface ZoneCreateRequest extends ZoneRect {
  name: string;
  zoneType: ZoneType;
  assignedUserId: number | null;
  defaultExitId: number | null;
  members: ZoneMember[] | null;
}

/**
 * 부분 갱신. null은 "변경 없음"이므로, 값을 비우려면 clear* 플래그를 쓴다. 서버 계약과 같은 모양이다.
 */
export interface ZoneUpdateRequest {
  name?: string;
  zoneType?: ZoneType;
  x?: number;
  y?: number;
  width?: number;
  height?: number;
  assignedUserId?: number | null;
  clearAssignedUser?: boolean;
  defaultExitId?: number | null;
  clearDefaultExit?: boolean;
  displayOrder?: number;
  members?: ZoneMember[];
}

export interface StructureConstraintUpdateRequest {
  movementPolicy: MovementPolicy;
}

export const layoutMetadataApi = {
  get: (drawingId: number) =>
    apiClient
      .get<LayoutMetadata>(`/api/drawings/${drawingId}/layout-metadata`)
      .then((response) => response.data),

  createZone: (drawingId: number, request: ZoneCreateRequest) =>
    apiClient
      .post<LayoutZone>(`/api/drawings/${drawingId}/zones`, request)
      .then((response) => response.data),

  updateZone: (drawingId: number, zoneId: number, request: ZoneUpdateRequest) =>
    apiClient
      .patch<LayoutZone>(`/api/drawings/${drawingId}/zones/${zoneId}`, request)
      .then((response) => response.data),

  deleteZone: (drawingId: number, zoneId: number) =>
    apiClient.delete<void>(`/api/drawings/${drawingId}/zones/${zoneId}`).then(() => undefined),

  updateStructureConstraints: (
    drawingId: number,
    fabricId: number,
    request: StructureConstraintUpdateRequest,
  ) =>
    apiClient
      .patch<void>(`/api/drawings/${drawingId}/structures/${fabricId}/constraints`, request)
      .then(() => undefined),
};
