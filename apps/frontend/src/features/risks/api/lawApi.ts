import { apiClient } from '../../../api/client';
import type { RegulationDetail, SearchResponse } from '../types/regulations';

export const lawApi = {
  searchRegulations: (query: string, page: number, size: number) =>
    apiClient
      .get<SearchResponse>('/api/regulations', { params: { query, page, size } })
      .then((res) => res.data),
  getRegulationDetail: (serialNumber: string) =>
    apiClient.get<RegulationDetail>(`/api/regulations/${serialNumber}`).then((res) => res.data),
};
