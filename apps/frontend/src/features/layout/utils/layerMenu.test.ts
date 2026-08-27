import { describe, expect, it, vi } from 'vitest';
import type { LayoutZone } from '../api/layoutMetadataApi';
import { buildLayerMenu, canGroupSelection } from './layerMenu';
import type { LayerElement } from './zoneMembership';

const zone = (zoneId: number, name: string, members: LayoutZone['members']): LayoutZone => ({
  zoneId,
  name,
  zoneType: 'WORK',
  rect: { x: 0, y: 0, width: 1, height: 1 },
  assignedUserId: null,
  defaultExitId: null,
  displayOrder: 0,
  members,
});

const saved: LayerElement = { kind: 'WALL', id: 'w1', backendId: 11, name: '벽 1' };
const unsaved: LayerElement = { kind: 'WALL', id: 'w2', backendId: null, name: '벽 2' };

const options = {
  zones: [zone(1, '구역 1', [{ kind: 'WALL' as const, id: 11 }]), zone(2, '구역 2', [])],
  canGroupSelected: true,
  onChangeMembership: vi.fn(),
  onGroupSelectionIntoZone: vi.fn(),
};

const labels = (entries: ReturnType<typeof buildLayerMenu>) => entries.map((entry) => entry.label);

describe('buildLayerMenu', () => {
  it('소속된 구역은 이동 대상에서 비활성화하고 빼기를 제공한다', () => {
    const [move] = buildLayerMenu({ ...options, element: saved });
    expect(move.label).toBe('구역으로 이동');
    const children = 'children' in move ? move.children : [];
    expect(children.map((child) => child.label)).toEqual(['구역 1', '구역 2', '구역에서 빼기']);
    expect(children[0].disabled).toBe(true);
    expect(children[1].disabled).toBe(false);
  });

  it('저장 전 요소에는 구역 이동을 제공하지 않는다', () => {
    expect(labels(buildLayerMenu({ ...options, element: unsaved }))).toEqual([
      '선택 요소를 구역으로 묶기',
    ]);
  });

  it('구역이 하나도 없으면 이동 항목을 내지 않는다', () => {
    expect(labels(buildLayerMenu({ ...options, zones: [], element: saved }))).toEqual([
      '선택 요소를 구역으로 묶기',
    ]);
  });
});

describe('canGroupSelection', () => {
  const doc = {
    walls: [
      { id: 'w1', backendId: 11 },
      { id: 'w2', backendId: null },
    ],
    pillars: [],
    fabrics: [],
  };

  it('선택이 비어 있으면 묶을 수 없다', () => {
    expect(
      canGroupSelection({ doc, selection: { wallIds: [], pillarIds: [], fabricIds: [] } }),
    ).toBe(false);
  });

  it('저장 전 요소가 섞이면 묶을 수 없다', () => {
    expect(
      canGroupSelection({
        doc,
        selection: { wallIds: ['w1', 'w2'], pillarIds: [], fabricIds: [] },
      }),
    ).toBe(false);
  });

  it('모두 저장된 요소면 묶을 수 있다', () => {
    expect(
      canGroupSelection({ doc, selection: { wallIds: ['w1'], pillarIds: [], fabricIds: [] } }),
    ).toBe(true);
  });
});
