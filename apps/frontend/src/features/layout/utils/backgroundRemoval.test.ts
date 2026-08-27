import { describe, expect, it } from 'vitest';
import { fromSerialized, toSerialized } from './serialization';

describe('도면 배경 기능 제거', () => {
  it('기존 배경 데이터는 무시하고 다시 저장하지 않는다', () => {
    const document = fromSerialized({
      name: '기존 도면',
      width: 10,
      height: 10,
      background: {
        image: 'data:image/png;base64,legacy',
        x: 0,
        y: 0,
        width: 10,
        height: 10,
        opacity: 0.5,
        aspect: 1,
      },
    });

    expect(document).not.toHaveProperty('background');
    expect(toSerialized(document)).not.toHaveProperty('background');
  });
});
