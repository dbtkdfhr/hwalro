export function calculateEvacuationRate(evacuatedPeople: number, totalPeople: number): number {
  if (!Number.isFinite(totalPeople) || totalPeople <= 0) return 0;

  const safeEvacuatedPeople = Number.isFinite(evacuatedPeople)
    ? Math.min(totalPeople, Math.max(0, evacuatedPeople))
    : 0;

  if (safeEvacuatedPeople >= totalPeople) return 100;

  return Math.min(99, Math.round((safeEvacuatedPeople / totalPeople) * 100));
}
