/**
 * Category colours come from utility locate marking, the paint code used to
 * mark buried services on a road: red electric, blue water, green sewer,
 * orange lighting, grey for a proposed excavation.
 */
const COLORS = {
  ELECTRICITY: 'var(--electricity)',
  WATER: 'var(--water)',
  ROADS: 'var(--roads)',
  DRAINAGE: 'var(--drainage)',
  GARBAGE: 'var(--garbage)',
  STREETLIGHT: 'var(--streetlight)',
  OTHER: 'var(--other)',
};

export function categoryColor(name) {
  return COLORS[name] ?? 'var(--ink-faint)';
}
