/**
 * Deterministic category -> palette-slot index (0-7, or -1 beyond 8 categories -- callers fall
 * back to an "other" swatch). Index comes from the category's alphabetical position across the
 * *full* category list, not whatever subset/order a given chart happens to render, so a filter
 * change never repaints a category still on screen. Each layout skin maps this index into its
 * own palette rather than sharing one hex set, keeping their distinct identities.
 */
export function categorySeriesIndex(category: string, allCategories: string[]): number {
  const sorted = [...allCategories].sort();
  const index = sorted.indexOf(category);
  return index >= 0 && index < 8 ? index : -1;
}
