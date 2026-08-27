import { apiClient } from '../../../api/client';
import type {
  ChecklistTemplate,
  ChecklistTemplateUpdateRequest,
  InspectionArea,
  InspectionAreaRequest,
  InspectionDetail,
  InspectionHistory,
  InspectionUpdateRequest,
} from '../types';

export const safetyCheckApi = {
  getAreas: () =>
    apiClient.get<InspectionArea[]>('/api/safety-checks/areas').then((response) => response.data),
  getArea: (areaId: number) =>
    apiClient
      .get<InspectionArea>(`/api/safety-checks/areas/${areaId}`)
      .then((response) => response.data),
  createArea: (body: InspectionAreaRequest) =>
    apiClient
      .post<InspectionArea>('/api/safety-checks/areas', body)
      .then((response) => response.data),
  updateArea: (areaId: number, body: InspectionAreaRequest) =>
    apiClient
      .put<InspectionArea>(`/api/safety-checks/areas/${areaId}`, body)
      .then((response) => response.data),
  deleteArea: (areaId: number) =>
    apiClient.delete(`/api/safety-checks/areas/${areaId}`).then(() => undefined),
  getHistory: (areaId: number) =>
    apiClient
      .get<InspectionHistory[]>(`/api/safety-checks/areas/${areaId}/inspections`)
      .then((response) => response.data),
  getInspection: (inspectionId: number) =>
    apiClient
      .get<InspectionDetail>(`/api/safety-checks/inspections/${inspectionId}`)
      .then((response) => response.data),
  createInspection: (areaId: number) =>
    apiClient
      .post<InspectionDetail>(`/api/safety-checks/areas/${areaId}/inspections`, {})
      .then((response) => response.data),
  getOrCreateCurrentInspection: (areaId: number) =>
    apiClient
      .post<InspectionDetail>(`/api/safety-checks/areas/${areaId}/inspections/current`)
      .then((response) => response.data),
  updateInspection: (inspectionId: number, body: InspectionUpdateRequest) =>
    apiClient
      .put<InspectionDetail>(`/api/safety-checks/inspections/${inspectionId}`, body)
      .then((response) => response.data),
  saveSnapshot: (
    inspectionId: number,
    image: Blob,
    layoutVersionId?: number,
    layoutId?: number,
  ) => {
    const params = {
      ...(layoutVersionId === undefined ? {} : { layoutVersionId }),
      ...(layoutId === undefined ? {} : { layoutId }),
    };
    return apiClient
      .put(`/api/safety-checks/inspections/${inspectionId}/snapshot`, image, {
        params,
        headers: { 'Content-Type': 'image/png' },
      })
      .then(() => undefined);
  },
  getSnapshot: (inspectionId: number) =>
    apiClient
      .get<Blob>(`/api/safety-checks/inspections/${inspectionId}/snapshot`, {
        responseType: 'blob',
      })
      .then((response) => response.data),
  deleteInspection: (inspectionId: number) =>
    apiClient.delete(`/api/safety-checks/inspections/${inspectionId}`).then(() => undefined),
  getChecklistTemplate: (areaId: number) =>
    apiClient
      .get<ChecklistTemplate>(`/api/safety-checks/areas/${areaId}/checklist-template`)
      .then((response) => response.data),
  updateChecklistTemplate: (areaId: number, body: ChecklistTemplateUpdateRequest) =>
    apiClient
      .put<ChecklistTemplate>(`/api/safety-checks/areas/${areaId}/checklist-template`, body)
      .then((response) => response.data),
};
