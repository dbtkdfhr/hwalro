export interface ExitPresentation {
  color: number;
  emissive: number;
  emissiveIntensity: number;
  markerOpacity: number;
}

const ACTIVE_EXIT_PRESENTATION: ExitPresentation = {
  color: 0xabcf30,
  emissive: 0x526b10,
  emissiveIntensity: 0.28,
  markerOpacity: 0.32,
};

const INACTIVE_EXIT_PRESENTATION: ExitPresentation = {
  color: 0x0f766e,
  emissive: 0x063f3a,
  emissiveIntensity: 0.12,
  markerOpacity: 0.18,
};

export function getExitPresentation(active: boolean): ExitPresentation {
  return active ? ACTIVE_EXIT_PRESENTATION : INACTIVE_EXIT_PRESENTATION;
}
