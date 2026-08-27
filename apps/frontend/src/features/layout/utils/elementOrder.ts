import type { Fabric, Pillar, Wall } from '../types';

/** 순서를 사용자가 정할 수 있는 요소 종류. 외곽벽·비상구·텍스트는 고정 위치를 유지한다. */
export type OrderedKind = 'wall' | 'pillar' | 'fabric';

export interface OrderedElement {
  kind: OrderedKind;
  element: Wall | Pillar | Fabric;
}

/** displayOrder가 없던 시절 도면을 위한 폴백 순서. 저장 한 번이면 실제 값으로 대체된다. */
const KIND_RANK: Record<OrderedKind, number> = { wall: 0, pillar: 1, fabric: 2 };

interface Sortable extends OrderedElement {
  order: number;
  rank: number;
  index: number;
}

function toSortable(kind: OrderedKind, elements: Array<Wall | Pillar | Fabric>): Sortable[] {
  return elements.map((element, index) => ({
    kind,
    element,
    // 아직 순서가 없는 요소는 방금 그린 것이므로 맨 위에 둔다.
    order: element.displayOrder ?? Number.POSITIVE_INFINITY,
    rank: KIND_RANK[kind],
    index,
  }));
}

/**
 * 벽·기둥·구조물을 하나의 순서 축으로 병합한다. 앞이 아래(먼저 그려짐), 뒤가 위다.
 *
 * 종류마다 따로 매기던 순서를 도면 버전 전체의 단일 순서로 합치기 때문에, 벽을 구조물 위로
 * 올리는 것처럼 종류를 가로지르는 배치가 가능해진다.
 */
export function orderedElements(
  walls: Wall[],
  pillars: Pillar[],
  fabrics: Fabric[],
): OrderedElement[] {
  return [
    ...toSortable('wall', walls),
    ...toSortable('pillar', pillars),
    ...toSortable('fabric', fabrics),
  ]
    .sort(
      (left, right) =>
        left.order - right.order || left.rank - right.rank || left.index - right.index,
    )
    .map(({ kind, element }) => ({ kind, element }));
}

/**
 * 병합된 순서를 각 요소의 displayOrder로 확정한다. 순서를 바꾼 뒤 반드시 호출해야
 * 종류 간 상대 순서가 저장·재적재를 견딘다.
 */
export function withAssignedOrder(ordered: OrderedElement[]): {
  walls: Wall[];
  pillars: Pillar[];
  fabrics: Fabric[];
} {
  const walls: Wall[] = [];
  const pillars: Pillar[] = [];
  const fabrics: Fabric[] = [];
  ordered.forEach(({ kind, element }, index) => {
    const next = { ...element, displayOrder: index };
    if (kind === 'wall') walls.push(next as Wall);
    else if (kind === 'pillar') pillars.push(next as Pillar);
    else fabrics.push(next as Fabric);
  });
  return { walls, pillars, fabrics };
}
