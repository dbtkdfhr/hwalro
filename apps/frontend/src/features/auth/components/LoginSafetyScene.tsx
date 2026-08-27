import { useEffect, useRef, useState } from 'react';
import './LoginSafetyScene.css';

type SceneStatus = 'loading' | 'ready' | 'error';

export function LoginSafetyScene() {
  const hostRef = useRef<HTMLDivElement>(null);
  const [status, setStatus] = useState<SceneStatus>('loading');

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;

    const desktopMedia = window.matchMedia('(min-width: 1024px)');
    let active = true;
    let loading = false;
    let destroyScene: (() => void) | undefined;
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    const startScene = async () => {
      if (!desktopMedia.matches || loading || destroyScene) return;
      loading = true;
      setStatus('loading');
      try {
        const { createLoginSafetyScene } = await import('../rendering/loginSafetySceneRenderer');
        if (!active || !desktopMedia.matches) return;
        const scene = createLoginSafetyScene(host, reducedMotion);
        destroyScene = scene.destroy;
        setStatus('ready');
      } catch {
        if (active && desktopMedia.matches) setStatus('error');
      } finally {
        loading = false;
      }
    };

    const handleDesktopChange = () => {
      if (desktopMedia.matches) {
        void startScene();
        return;
      }
      destroyScene?.();
      destroyScene = undefined;
      setStatus('loading');
    };

    desktopMedia.addEventListener('change', handleDesktopChange);
    void startScene();

    return () => {
      active = false;
      desktopMedia.removeEventListener('change', handleDesktopChange);
      destroyScene?.();
    };
  }, []);

  return (
    <div className="login-safety-scene" aria-hidden="true">
      <div ref={hostRef} className="login-safety-scene__host" />
      {status !== 'ready' && (
        <div className="login-safety-scene__fallback">
          <span className="login-safety-scene__fallback-mark" />
          <p>
            {status === 'error'
              ? '공간 안전 시각화를 불러오지 못했습니다.'
              : '3D 공간을 준비하고 있습니다.'}
          </p>
        </div>
      )}
    </div>
  );
}
