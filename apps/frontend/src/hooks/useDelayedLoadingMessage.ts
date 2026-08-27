import { useEffect, useState } from 'react';

const LOADING_DELAY_NOTICE_MS = 5_000;

export function useDelayedLoadingMessage(isLoading: boolean, initialMessage: string): string {
  const [isDelayed, setIsDelayed] = useState(false);

  useEffect(() => {
    setIsDelayed(false);
    if (!isLoading) return;
    const timerId = window.setTimeout(() => setIsDelayed(true), LOADING_DELAY_NOTICE_MS);
    return () => window.clearTimeout(timerId);
  }, [isLoading]);

  return isDelayed
    ? `${initialMessage} 연결이 지연되어 자동으로 다시 확인하고 있습니다.`
    : initialMessage;
}
