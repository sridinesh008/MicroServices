import type { ExpenseFilters, ExpenseStatus, SourceMode } from "../../api/types";

interface Props {
  filters: ExpenseFilters;
  categories: string[];
  onChange: (filters: ExpenseFilters) => void;
}

export default function FilterPanel({ filters, categories, onChange }: Props) {
  function set<K extends keyof ExpenseFilters>(key: K, value: ExpenseFilters[K]) {
    onChange({ ...filters, [key]: value || undefined });
  }

  function toggleCategory(category: string) {
    const current = filters.categories ?? [];
    const next = current.includes(category) ? current.filter((c) => c !== category) : [...current, category];
    set("categories", next.length ? next : undefined);
  }

  return (
    <div className="border border-border rounded-xl bg-surface p-4 space-y-3">
      <div className="grid grid-cols-2 gap-2">
        <label className="text-xs text-ink-muted">
          From
          <input
            type="date"
            className="mt-1 w-full rounded-md border border-border bg-transparent px-2 py-1 text-sm"
            value={filters.dateFrom ?? ""}
            onChange={(e) => set("dateFrom", e.target.value)}
          />
        </label>
        <label className="text-xs text-ink-muted">
          To
          <input
            type="date"
            className="mt-1 w-full rounded-md border border-border bg-transparent px-2 py-1 text-sm"
            value={filters.dateTo ?? ""}
            onChange={(e) => set("dateTo", e.target.value)}
          />
        </label>
        <label className="text-xs text-ink-muted">
          Min amount
          <input
            type="number"
            className="mt-1 w-full rounded-md border border-border bg-transparent px-2 py-1 text-sm"
            value={filters.amountMin ?? ""}
            onChange={(e) => set("amountMin", e.target.value)}
          />
        </label>
        <label className="text-xs text-ink-muted">
          Max amount
          <input
            type="number"
            className="mt-1 w-full rounded-md border border-border bg-transparent px-2 py-1 text-sm"
            value={filters.amountMax ?? ""}
            onChange={(e) => set("amountMax", e.target.value)}
          />
        </label>
        <label className="text-xs text-ink-muted">
          Source
          <select
            className="mt-1 w-full rounded-md border border-border bg-transparent px-2 py-1 text-sm"
            value={filters.sourceMode ?? ""}
            onChange={(e) => set("sourceMode", (e.target.value || undefined) as SourceMode | undefined)}
          >
            <option value="">Any</option>
            <option value="TEXT">Text</option>
            <option value="IMAGE">Image</option>
          </select>
        </label>
        <label className="text-xs text-ink-muted">
          Status
          <select
            className="mt-1 w-full rounded-md border border-border bg-transparent px-2 py-1 text-sm"
            value={filters.status ?? ""}
            onChange={(e) => set("status", (e.target.value || undefined) as ExpenseStatus | undefined)}
          >
            <option value="">Confirmed (default)</option>
            <option value="CONFIRMED">Confirmed</option>
            <option value="PENDING_CONFIRMATION">Pending</option>
            <option value="DISCARDED">Discarded</option>
          </select>
        </label>
      </div>

      <label className="text-xs text-ink-muted block">
        Search
        <input
          type="text"
          placeholder="description or category"
          className="mt-1 w-full rounded-md border border-border bg-transparent px-2 py-1 text-sm"
          value={filters.search ?? ""}
          onChange={(e) => set("search", e.target.value)}
        />
      </label>

      {categories.length > 0 && (
        <div>
          <p className="text-xs text-ink-muted mb-1">Categories</p>
          <div className="flex flex-wrap gap-1">
            {categories.map((c) => {
              const active = filters.categories?.includes(c);
              return (
                <button
                  key={c}
                  onClick={() => toggleCategory(c)}
                  className={`px-2 py-0.5 rounded-full text-xs border ${
                    active ? "bg-accent text-white border-accent" : "border-border text-ink-secondary"
                  }`}
                >
                  {c}
                </button>
              );
            })}
          </div>
        </div>
      )}

      <button onClick={() => onChange({})} className="text-xs text-ink-muted underline">
        Clear filters
      </button>
    </div>
  );
}
