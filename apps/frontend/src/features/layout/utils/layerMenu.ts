import type { LayoutZone } from '../api/layoutMetadataApi';
import type { MenuItem } from '../components/LayerContextMenu';
import type { LayerElement } from './zoneMembership';

interface Element {
  id: string;
  backendId: number | null;
}

export type LayerMenuEntry = MenuItem | { label: string; children: MenuItem[] };

export interface LayerMenuOptions {
  element: LayerElement;
  zones: LayoutZone[];
  /** 현재 선택을 구역으로 묶을 수 있는지. 저장 전 요소가 섞여 있으면 불가능하다. */
  canGroupSelected: boolean;
  onChangeMembership: (element: LayerElement, targetZoneId: number | null) => void;
  onGroupSelectionIntoZone: () => void;
}

/**
 * 요소 하나에 대한 컨텍스트 메뉴 구성. 계층 패널과 캔버스가 같은 메뉴를 써야 하므로 여기 한 곳에서만 만든다.
 *
 * 저장 전(backendId가 null) 요소는 구역 멤버십을 가질 수 없어 이동 항목을 내지 않는다.
 */
export function buildLayerMenu({
  element,
  zones,
  canGroupSelected,
  onChangeMembership,
  onGroupSelectionIntoZone,
}: LayerMenuOptions): LayerMenuEntry[] {
  const groupItem: MenuItem = {
    label: '선택 요소를 구역으로 묶기',
    disabled: !canGroupSelected,
    onSelect: onGroupSelectionIntoZone,
  };
  if (element.backendId === null) {
    return [groupItem];
  }

  const ownerZone = zones.find((zone) =>
    zone.members.some((member) => member.kind === element.kind && member.id === element.backendId),
  );
  const items: LayerMenuEntry[] = [];
  if (zones.length > 0) {
    items.push({
      label: '구역으로 이동',
      children: [
        ...zones.map((zone) => ({
          label: zone.name,
          disabled: ownerZone?.zoneId === zone.zoneId,
          onSelect: () => onChangeMembership(element, zone.zoneId),
        })),
        ...(ownerZone
          ? [{ label: '구역에서 빼기', onSelect: () => onChangeMembership(element, null) }]
          : []),
      ],
    });
  }
  items.push(groupItem);
  return items;
}

/** 현재 선택을 구역으로 묶을 수 있는지. 하나라도 저장 전 요소면 서버가 멤버로 받아주지 못한다. */
export function canGroupSelection(state: {
  doc: { walls: Element[]; pillars: Element[]; fabrics: Element[] };
  selection: { wallIds: string[]; pillarIds: string[]; fabricIds: string[] };
}): boolean {
  const { doc, selection } = state;
  const chosen = [
    ...doc.walls.filter((wall) => selection.wallIds.includes(wall.id)),
    ...doc.pillars.filter((pillar) => selection.pillarIds.includes(pillar.id)),
    ...doc.fabrics.filter((fabric) => selection.fabricIds.includes(fabric.id)),
  ];
  return chosen.length > 0 && chosen.every((element) => element.backendId !== null);
}
