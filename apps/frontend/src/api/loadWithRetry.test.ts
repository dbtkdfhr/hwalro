import { AxiosError, CanceledError } from 'axios';
import { describe, expect, it, vi } from 'vitest';
import { loadWithRetry } from './loadWithRetry';

function axiosError(status?: number) {
  return new AxiosError(
    'request failed',
    status ? undefined : 'ERR_NETWORK',
    undefined,
    undefined,
    status ? ({ status } as never) : undefined,
  );
}

describe('loadWithRetry', () => {
  it('일시적인 조회 실패는 정해진 횟수 안에서 자동 복구한다', async () => {
    const request = vi
      .fn<() => Promise<string>>()
      .mockRejectedValueOnce(axiosError(503))
      .mockRejectedValueOnce(axiosError())
      .mockResolvedValue('loaded');

    await expect(loadWithRetry(request, { delaysMs: [0, 0] })).resolves.toBe('loaded');
    expect(request).toHaveBeenCalledTimes(3);
  });

  it('404처럼 재시도해도 바뀌지 않는 응답은 즉시 반환한다', async () => {
    const error = axiosError(404);
    const request = vi.fn<() => Promise<string>>().mockRejectedValue(error);

    await expect(loadWithRetry(request, { delaysMs: [0, 0] })).rejects.toBe(error);
    expect(request).toHaveBeenCalledTimes(1);
  });

  it('화면 이동으로 취소된 요청은 재시도하지 않는다', async () => {
    const error = new CanceledError('navigation changed');
    const request = vi.fn<() => Promise<string>>().mockRejectedValue(error);

    await expect(loadWithRetry(request, { delaysMs: [0, 0] })).rejects.toBe(error);
    expect(request).toHaveBeenCalledTimes(1);
  });
});
