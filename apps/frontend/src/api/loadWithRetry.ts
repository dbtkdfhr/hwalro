import axios, { CanceledError } from 'axios';

const DEFAULT_DELAYS_MS = [300, 900];
const TRANSIENT_STATUS = new Set([408, 425, 429, 500, 502, 503, 504]);

interface LoadWithRetryOptions {
  signal?: AbortSignal;
  delaysMs?: number[];
}

export function isCancelledRequest(error: unknown): boolean {
  return axios.isCancel(error);
}

export function isTransientLoadError(error: unknown): boolean {
  if (!axios.isAxiosError(error) || isCancelledRequest(error)) return false;
  if (!error.response) return true;
  return TRANSIENT_STATUS.has(error.response.status);
}

function waitForRetry(delayMs: number, signal?: AbortSignal): Promise<void> {
  if (signal?.aborted) return Promise.reject(new CanceledError('navigation changed'));
  if (delayMs <= 0) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const timerId = window.setTimeout(resolve, delayMs);
    signal?.addEventListener(
      'abort',
      () => {
        window.clearTimeout(timerId);
        reject(new CanceledError('navigation changed'));
      },
      { once: true },
    );
  });
}

export async function loadWithRetry<T>(
  request: () => Promise<T>,
  options: LoadWithRetryOptions = {},
): Promise<T> {
  const delaysMs = options.delaysMs ?? DEFAULT_DELAYS_MS;
  for (let attempt = 0; ; attempt += 1) {
    if (options.signal?.aborted) throw new CanceledError('navigation changed');
    try {
      return await request();
    } catch (error) {
      const delayMs = delaysMs[attempt];
      if (delayMs === undefined || !isTransientLoadError(error)) throw error;
      await waitForRetry(delayMs, options.signal);
    }
  }
}
