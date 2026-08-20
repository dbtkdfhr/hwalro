export type InspectionStatus = 'DRAFT' | 'COMPLETED';
export type InspectionResult = 'PENDING' | 'PASS' | 'REVIEW_REQUIRED' | 'FAIL';

export interface InspectionArea {
  id: number;
  name: string;
  description: string | null;
  active: boolean;
  inspectionCount: number;
  lastInspectedAt: string | null;
  hasActiveTemplate: boolean;
}

export interface InspectionAreaRequest {
  name: string;
  description: string | null;
}

export interface InspectionHistory {
  id: number;
  inspectorId: number;
  status: InspectionStatus;
  completedItemCount: number;
  totalItemCount: number;
  failCount: number;
  reviewRequiredCount: number;
  createdAt: string;
}

export interface InspectionItem {
  id: number;
  title: string;
  criterion: string | null;
  category: string;
  displayOrder: number;
  result: InspectionResult;
  comment: string | null;
}

export interface InspectionDetail {
  id: number;
  inspectionAreaId: number;
  areaName: string;
  simulationResultId: number | null;
  inspectorId: number;
  status: InspectionStatus;
  comment: string | null;
  updatedAt: string;
  completedAt: string | null;
  items: InspectionItem[];
}

export interface InspectionUpdateRequest {
  status: InspectionStatus;
  comment: string | null;
  items: Array<Pick<InspectionItem, 'id' | 'result' | 'comment'>>;
}

export interface ChecklistTemplateItem {
  id: number;
  title: string;
  criterion: string | null;
  category: string;
  displayOrder: number;
}

export interface ChecklistTemplate {
  id: number | null;
  version: number;
  items: ChecklistTemplateItem[];
}

export interface ChecklistTemplateUpdateRequest {
  items: Array<Pick<ChecklistTemplateItem, 'title' | 'criterion' | 'category'>>;
}
