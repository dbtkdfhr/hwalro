import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from '../../../api/client';
import { zoneApi } from './zoneApi';

vi.mock('../../../api/client', () => ({
  apiClient: {
    get: vi.fn(),
  },
}));

describe('zoneApi', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
  });

  it('uses the shared drawing evacuation-routes endpoint with the shared timeout', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: [] });

    await zoneApi.evacuationRoutes(91);

    expect(apiClient.get).toHaveBeenCalledWith('/api/drawings/91/evacuation-routes');
  });
});
