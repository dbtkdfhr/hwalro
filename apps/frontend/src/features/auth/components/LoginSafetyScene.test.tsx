import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { LoginSafetyScene } from './LoginSafetyScene';

describe('LoginSafetyScene', () => {
  it('WebGL이 준비되기 전에도 크기가 유지되는 대체 화면을 제공한다', () => {
    const html = renderToStaticMarkup(<LoginSafetyScene />);

    expect(html).toContain('login-safety-scene');
    expect(html).toContain('3D 공간을 준비하고 있습니다.');
    expect(html).toContain('aria-hidden="true"');
  });
});
