import { describe, expect, it } from 'vitest';
import { fromSerialized, toSerialized } from './serialization';

const base = {
  name: '도면',
  width: 100,
  height: 50,
};

describe('backendId round-trip', () => {
  it('서버가 준 구조물·비상구 ID를 왕복시켜도 잃지 않는다', () => {
    const doc = fromSerialized({
      ...base,
      fabrics: [{ id: 41, name: '구조물 1', startX: 1, startY: 1, endX: 2, endY: 2, rotation: 0 }],
      exits: [{ id: 77, name: '비상구 1', startX: 0, startY: 0, endX: 1, endY: 0 }],
    });

    expect(doc.fabrics[0].backendId).toBe(41);
    expect(doc.exits[0].backendId).toBe(77);

    const serialized = toSerialized(doc);
    expect(serialized.fabrics[0].id).toBe(41);
    expect(serialized.exits[0].id).toBe(77);
  });

  it('ID가 없는 입력은 새 요소로 취급해 null로 내보낸다', () => {
    const doc = fromSerialized({
      ...base,
      fabrics: [{ name: '구조물 1', startX: 1, startY: 1, endX: 2, endY: 2, rotation: 0 }],
      exits: [{ name: '비상구 1', startX: 0, startY: 0, endX: 1, endY: 0 }],
    });

    expect(doc.fabrics[0].backendId).toBeNull();
    expect(doc.exits[0].backendId).toBeNull();
    expect(toSerialized(doc).fabrics[0].id).toBeNull();
    expect(toSerialized(doc).exits[0].id).toBeNull();
  });

  it('숫자가 아니거나 양수가 아닌 ID는 신뢰하지 않는다', () => {
    const doc = fromSerialized({
      ...base,
      fabrics: [
        { id: '41', name: '구조물 1', startX: 1, startY: 1, endX: 2, endY: 2, rotation: 0 },
        { id: 0, name: '구조물 2', startX: 3, startY: 3, endX: 4, endY: 4, rotation: 0 },
        { id: 1.5, name: '구조물 3', startX: 5, startY: 5, endX: 6, endY: 6, rotation: 0 },
      ],
    });

    expect(doc.fabrics.map((fabric) => fabric.backendId)).toEqual([null, null, null]);
  });

  it('기둥·벽의 ID도 왕복시킨다', () => {
    const doc = fromSerialized({
      ...base,
      walls: [{ id: 5, name: '벽 1', startX: 0, startY: 0, endX: 3, endY: 0 }],
      pillars: [{ id: 9, name: '기둥 1', startX: 1, startY: 1, endX: 2, endY: 2, rotation: 0 }],
    });

    expect(doc.walls[0].backendId).toBe(5);
    expect(doc.pillars[0].backendId).toBe(9);
    expect(toSerialized(doc).walls[0].id).toBe(5);
    expect(toSerialized(doc).pillars[0].id).toBe(9);
  });
});
