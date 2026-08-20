const MIN_VISIBLE_HEATMAP_DENSITY = 2;

export function isVisibleHeatmapDensity(density: number) {
  return Number.isFinite(density) && density >= MIN_VISIBLE_HEATMAP_DENSITY;
}
