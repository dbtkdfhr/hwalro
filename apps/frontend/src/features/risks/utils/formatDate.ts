export function formatDate(iso: string, withTime = false): string {
  const [datePart, timePart] = iso.split('T');
  const date = datePart.replace(/-/g, '.');
  return withTime && timePart ? `${date} ${timePart.slice(0, 5)}` : date;
}
