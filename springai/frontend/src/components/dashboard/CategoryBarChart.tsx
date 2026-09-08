import type { CategoryTotal } from "../../api/types";
import { categoryColor } from "./categoryColor";

interface Props {
  data: CategoryTotal[];
  allCategories: string[];
}

export default function CategoryBarChart({ data, allCategories }: Props) {
  if (data.length === 0) {
    return <p className="text-sm text-ink-muted py-6 text-center">No data for this range.</p>;
  }
  const sorted = [...data].sort((a, b) => Number(b.total) - Number(a.total));
  const max = Math.max(...sorted.map((d) => Number(d.total)));

  return (
    <div className="space-y-2">
      {sorted.map((row) => {
        const value = Number(row.total);
        const pct = max > 0 ? (value / max) * 100 : 0;
        return (
          <div key={row.category} className="flex items-center gap-2">
            <span className="w-28 shrink-0 text-xs text-ink-secondary truncate" title={row.category}>
              {row.category}
            </span>
            <div className="flex-1 h-5 relative">
              <div
                className="h-5 rounded-r-[4px]"
                style={{ width: `${pct}%`, backgroundColor: categoryColor(row.category, allCategories) }}
              />
            </div>
            <span
              className="w-16 shrink-0 text-xs text-right text-ink-secondary"
              style={{ fontVariantNumeric: "tabular-nums" }}
            >
              {value.toFixed(2)}
            </span>
          </div>
        );
      })}
    </div>
  );
}
