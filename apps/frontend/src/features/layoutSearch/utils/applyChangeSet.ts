import type { SimulationDrawing } from '../../simulations/types';
import type { ChangeSet } from '../api/layoutSearchApi';

export type ApplyChangeSetResult =
  { drawing: SimulationDrawing; error: null } | { drawing: null; error: string };

export function applyChangeSet(
  drawing: SimulationDrawing,
  changeSet: ChangeSet,
): ApplyChangeSetResult {
  if (changeSet.schemaVersion !== 1 || changeSet.coordinateUnit !== 'METER') {
    return { drawing: null, error: '지원하지 않는 배치 변경 형식입니다.' };
  }

  const fabricsById = new Map<number, number>();
  const invalidFabricIds = new Set<number>();
  drawing.fabrics.forEach((fabric, index) => {
    if (!Number.isSafeInteger(fabric.id) || fabricsById.has(fabric.id)) {
      invalidFabricIds.add(fabric.id);
      return;
    }
    fabricsById.set(fabric.id, index);
  });

  const fabrics = drawing.fabrics.map((fabric) => ({ ...fabric }));
  for (const op of changeSet.ops) {
    if (op.type !== 'MOVE_FABRIC') {
      return { drawing: null, error: '지원하지 않는 변경 작업이 포함되어 있습니다.' };
    }
    const index = fabricsById.get(op.fabricId);
    if (
      index === undefined ||
      invalidFabricIds.has(op.fabricId) ||
      drawing.fabrics[index]?.id !== op.fabricId
    ) {
      return {
        drawing: null,
        error: `구조물 ${op.fabricId}번을 현재 도면에서 찾을 수 없습니다.`,
      };
    }
    const current = fabrics[index];
    if (
      !current ||
      !isValidTransform(op.before) ||
      !isValidTransform(op.after) ||
      !sameTransform(current, op.before)
    ) {
      return {
        drawing: null,
        error: `구조물 ${op.fabricId}번의 변경 정보가 현재 도면과 일치하지 않습니다.`,
      };
    }
    fabrics[index] = { ...current, ...op.after };
  }

  return {
    drawing: {
      ...drawing,
      fabrics,
    },
    error: null,
  };
}

function isValidTransform(transform: ChangeSet['ops'][number]['before']) {
  return (
    [transform.startX, transform.startY, transform.endX, transform.endY, transform.rotation].every(
      Number.isFinite,
    ) &&
    transform.startX < transform.endX &&
    transform.startY < transform.endY
  );
}

function sameTransform(
  fabric: SimulationDrawing['fabrics'][number],
  transform: ChangeSet['ops'][number]['before'],
) {
  return (
    fabric.startX === transform.startX &&
    fabric.startY === transform.startY &&
    fabric.endX === transform.endX &&
    fabric.endY === transform.endY &&
    fabric.rotation === transform.rotation
  );
}
