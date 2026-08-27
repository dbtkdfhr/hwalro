import { useCallback, useEffect, useRef, useState } from 'react';
import {
  layoutMetadataApi,
  type LayoutMetadata,
  type LayoutZone,
  type StructureConstraint,
  type StructureConstraintUpdateRequest,
  type ZoneCreateRequest,
  type ZoneUpdateRequest,
} from '../api/layoutMetadataApi';
import { getDrawingErrorMessage } from '../../drawings/utils/getDrawingErrorMessage';

const EMPTY: LayoutMetadata = {
  layoutId: 0,
  layoutVersionId: 0,
  zones: [],
  structureConstraints: [],
};

/**
 * 구역·배치 제약은 서버가 소유하는 상태다. 도면 기하와 생명주기가 달라 편집기 reducer의 undo/redo 문서에
 * 넣지 않는다 - Ctrl+Z로 구역 삭제가 되돌아가면 화면과 서버가 어긋난다.
 *
 * 갱신은 낙관적으로 반영하고 실패하면 직전 상태로 되돌린다.
 */
export function useLayoutMetadata(drawingId: string) {
  const [metadata, setMetadata] = useState<LayoutMetadata>(EMPTY);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const numericId = Number(drawingId);
  const metadataRef = useRef(metadata);
  metadataRef.current = metadata;

  const reload = useCallback(async () => {
    if (!Number.isFinite(numericId)) {
      setIsLoading(false);
      return;
    }
    try {
      setMetadata(await layoutMetadataApi.get(numericId));
      setErrorMessage(null);
    } catch (error) {
      setErrorMessage(getDrawingErrorMessage(error));
    } finally {
      setIsLoading(false);
    }
  }, [numericId]);

  useEffect(() => {
    void reload();
  }, [reload]);

  /** 낙관적 적용 후 실패하면 스냅샷으로 되돌린다. */
  const mutate = useCallback(
    async (
      optimistic: (current: LayoutMetadata) => LayoutMetadata,
      request: () => Promise<void>,
    ) => {
      const snapshot = metadataRef.current;
      setMetadata(optimistic(snapshot));
      setErrorMessage(null);
      try {
        await request();
        return true;
      } catch (error) {
        setMetadata(snapshot);
        setErrorMessage(getDrawingErrorMessage(error));
        return false;
      }
    },
    [],
  );

  const createZone = useCallback(
    async (request: ZoneCreateRequest): Promise<LayoutZone | null> => {
      setErrorMessage(null);
      try {
        const created = await layoutMetadataApi.createZone(numericId, request);
        setMetadata((current) => ({ ...current, zones: [...current.zones, created] }));
        return created;
      } catch (error) {
        setErrorMessage(getDrawingErrorMessage(error));
        return null;
      }
    },
    [numericId],
  );

  const updateZone = useCallback(
    (zoneId: number, request: ZoneUpdateRequest, preview: (zone: LayoutZone) => LayoutZone) =>
      mutate(
        (current) => ({
          ...current,
          zones: current.zones.map((zone) => (zone.zoneId === zoneId ? preview(zone) : zone)),
        }),
        async () => {
          const saved = await layoutMetadataApi.updateZone(numericId, zoneId, request);
          setMetadata((current) => ({
            ...current,
            zones: current.zones.map((zone) => (zone.zoneId === zoneId ? saved : zone)),
          }));
        },
      ),
    [mutate, numericId],
  );

  const deleteZone = useCallback(
    (zoneId: number) =>
      mutate(
        (current) => ({
          ...current,
          zones: current.zones.filter((zone) => zone.zoneId !== zoneId),
          // 구역이 사라지면 그 구역 구조물은 공용 구조물이 된다.
          structureConstraints: current.structureConstraints.map((constraint) =>
            constraint.zoneId === zoneId ? { ...constraint, zoneId: null } : constraint,
          ),
        }),
        () => layoutMetadataApi.deleteZone(numericId, zoneId),
      ),
    [mutate, numericId],
  );

  const updateStructureConstraints = useCallback(
    (
      fabricId: number,
      request: StructureConstraintUpdateRequest,
      preview: Partial<StructureConstraint>,
    ) =>
      mutate(
        (current) => ({
          ...current,
          structureConstraints: current.structureConstraints.map((constraint) =>
            constraint.fabricId === fabricId ? { ...constraint, ...preview } : constraint,
          ),
        }),
        () => layoutMetadataApi.updateStructureConstraints(numericId, fabricId, request),
      ),
    [mutate, numericId],
  );

  return {
    metadata,
    isLoading,
    errorMessage,
    dismissError: useCallback(() => setErrorMessage(null), []),
    reload,
    createZone,
    updateZone,
    deleteZone,
    updateStructureConstraints,
  };
}
