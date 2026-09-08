const SLOTS = [
  "var(--series-1)",
  "var(--series-2)",
  "var(--series-3)",
  "var(--series-4)",
  "var(--series-5)",
  "var(--series-6)",
  "var(--series-7)",
  "var(--series-8)"
];
const OTHER = "var(--text-muted)";

/**
 * Stable category -> color mapping so a filter change never repaints a category that's still
 * on screen. Index comes from the *alphabetical position in the full category list*, not from
 * whatever subset/order the current chart happens to render.
 */
export function categoryColor(category: string, allCategories: string[]): string {
  const sorted = [...allCategories].sort();
  const index = sorted.indexOf(category);
  if (index === -1 || index >= SLOTS.length) {
    return OTHER;
  }
  return SLOTS[index];
}
