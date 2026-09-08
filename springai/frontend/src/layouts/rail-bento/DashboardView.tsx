import { useState } from "react";
import { api } from "../../api/client";
import type { CategoryTotal, ExpenseFilters, ExpenseView, MonthTotal, Page } from "../../api/types";
import { useDashboardData } from "../../design/useDashboardData";
import { useRules } from "../../design/useRules";
import { categorySeriesIndex } from "../../design/categorySeriesIndex";

const MONTH_NAMES = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

function seriesColor(index: number) {
  return index === -1 ? "var(--ink-3)" : `var(--c${index + 1})`;
}

export default function DashboardView() {
  const d = useDashboardData();
  const rules = useRules();

  return (
    <section className="rb-view">
      <div className="rb-page-head">
        <div>
          <h1>Dashboard</h1>
          <p>Everything about your spend, at a glance.</p>
        </div>
        <ReapplyButton onDone={d.refresh} />
      </div>
      <div className="rb-bento">
        <TotalCell totalSpend={d.totalSpend} count={d.page?.totalElements ?? 0} loading={d.initialLoading} />
        <DonutCell data={d.aggregates?.totalsByCategory ?? []} allCategories={d.categories} loading={d.initialLoading} />
        <TrendCell data={d.aggregates?.totalsByMonth ?? []} loading={d.initialLoading} />
        <FiltersCell filters={d.filters} categories={d.categories} onChange={d.onFilterChange} />
        <RulesCell rules={rules} />
        <TableCell
          items={d.page?.content ?? []}
          onUpdated={d.onExpenseUpdated}
          page={d.page}
          pageNumber={d.pageNumber}
          setPageNumber={d.setPageNumber}
          loading={d.initialLoading}
        />
      </div>
    </section>
  );
}

function TotalCell({ totalSpend, count, loading }: { totalSpend: number; count: number; loading: boolean }) {
  return (
    <div className="rb-cell rb-c-total">
      <h3>Total (filtered)</h3>
      {loading ? (
        <p className="rb-empty">Loading…</p>
      ) : (
        <>
          <div className="rb-total-figure rb-mono">{totalSpend.toFixed(2)}</div>
          <div className="rb-total-sub">
            across {count} expense{count === 1 ? "" : "s"}
          </div>
        </>
      )}
    </div>
  );
}

function DonutCell({
  data,
  allCategories,
  loading
}: {
  data: CategoryTotal[];
  allCategories: string[];
  loading: boolean;
}) {
  const total = data.reduce((s, c) => s + Number(c.total), 0);
  let acc = 0;
  const stops = data.map((c) => {
    const start = total ? (acc / total) * 360 : 0;
    acc += Number(c.total);
    const end = total ? (acc / total) * 360 : 0;
    return `${seriesColor(categorySeriesIndex(c.category, allCategories))} ${start}deg ${end}deg`;
  });

  return (
    <div className="rb-cell rb-c-donut">
      <h3>By category</h3>
      {loading ? (
        <p className="rb-empty">Loading…</p>
      ) : data.length === 0 ? (
        <p className="rb-empty">No data for this range.</p>
      ) : (
        <div className="rb-donut-wrap">
          <div className="rb-donut" style={{ background: `conic-gradient(${stops.join(",")})` }}>
            <span className="rb-mono">{total.toFixed(0)}</span>
          </div>
          <div className="rb-legend">
            {data.map((c) => (
              <div className="rb-legend-row" key={c.category}>
                <span
                  className="rb-legend-dot"
                  style={{ background: seriesColor(categorySeriesIndex(c.category, allCategories)) }}
                />
                <span className="rb-legend-name">{c.category}</span>
                <span className="rb-legend-val rb-mono">{Number(c.total).toFixed(2)}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function TrendCell({ data, loading }: { data: MonthTotal[]; loading: boolean }) {
  if (loading || data.length === 0) {
    return (
      <div className="rb-cell rb-c-trend">
        <h3>By month</h3>
        <p className="rb-empty">{loading ? "Loading…" : "No data for this range."}</p>
      </div>
    );
  }

  const sorted = [...data].sort((a, b) => a.year - b.year || a.month - b.month);
  const values = sorted.map((v) => Number(v.total));
  const max = Math.max(...values, 1);
  const W = 260;
  const H = 110;
  const pad = 12;
  const step = sorted.length > 1 ? (W - pad * 2) / (sorted.length - 1) : 0;
  const points = sorted.map((v, i) => ({
    x: pad + step * i,
    y: H - pad - (Number(v.total) / max) * (H - pad * 2 - 14),
    label: MONTH_NAMES[v.month - 1],
    value: Number(v.total)
  }));
  const linePath = points.map((p, i) => `${i === 0 ? "M" : "L"}${p.x},${p.y}`).join(" ");
  const areaPath = `${linePath} L${points[points.length - 1].x},${H - pad} L${points[0].x},${H - pad} Z`;

  return (
    <div className="rb-cell rb-c-trend">
      <h3>By month</h3>
      <svg className="rb-trend" viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Monthly spend trend">
        <defs>
          <linearGradient id="rbAreaGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--accent)" stopOpacity={0.5} />
            <stop offset="100%" stopColor="var(--accent)" stopOpacity={0} />
          </linearGradient>
        </defs>
        <path d={areaPath} fill="url(#rbAreaGrad)" opacity={0.35} />
        <path
          d={linePath}
          fill="none"
          stroke="var(--accent)"
          strokeWidth={2.4}
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        {points.map((p, i) => (
          <g key={i}>
            <circle cx={p.x} cy={p.y} r={2.8} fill="var(--accent)">
              <title>{`${p.label}: ${p.value.toFixed(2)}`}</title>
            </circle>
            <text x={p.x} y={H - 1} textAnchor="middle">
              {p.label}
            </text>
          </g>
        ))}
      </svg>
    </div>
  );
}

function FiltersCell({
  filters,
  categories,
  onChange
}: {
  filters: ExpenseFilters;
  categories: string[];
  onChange: (f: ExpenseFilters) => void;
}) {
  function toggleCategory(cat: string) {
    const current = filters.categories ?? categories;
    const next = current.includes(cat) ? current.filter((c) => c !== cat) : [...current, cat];
    const isAll = next.length === categories.length;
    onChange({ ...filters, categories: isAll ? undefined : next });
  }

  return (
    <div className="rb-cell rb-c-filters">
      <h3>Filters</h3>
      <div className="rb-toggle-row">
        {categories.map((c) => (
          <button
            key={c}
            type="button"
            className={`rb-tgl ${!filters.categories || filters.categories.includes(c) ? "active" : ""}`}
            onClick={() => toggleCategory(c)}
          >
            {c}
          </button>
        ))}
      </div>
      <input
        type="text"
        className="rb-mini-search"
        placeholder="Search description or category…"
        value={filters.search ?? ""}
        onChange={(e) => onChange({ ...filters, search: e.target.value || undefined })}
      />
    </div>
  );
}

function RulesCell({ rules }: { rules: ReturnType<typeof useRules> }) {
  const [text, setText] = useState("");

  function submit() {
    if (!text.trim()) return;
    rules.addRule(text);
    setText("");
  }

  return (
    <div className="rb-cell rb-c-rules">
      <h3>Auto-categorize rules</h3>
      <div>
        {rules.rules.length === 0 && <p className="rb-empty">No custom rules yet.</p>}
        {rules.rules.map((r) => (
          <div className="rb-rule-row" key={r.id}>
            <span className={r.active ? "" : "rb-inactive"}>{r.instructionText}</span>
            {r.active && (
              <button
                type="button"
                className="rb-rule-deactivate"
                title="Deactivate"
                onClick={() => rules.deactivateRule(r.id)}
              >
                ✕
              </button>
            )}
          </div>
        ))}
      </div>
      <div className="rb-add-rule">
        <input
          value={text}
          placeholder="e.g. fast food purchases should be Dining"
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && submit()}
        />
        <button type="button" className="rb-btn rb-btn-small" disabled={rules.busy} onClick={submit}>
          Add
        </button>
      </div>
    </div>
  );
}

function ReapplyButton({ onDone }: { onDone: () => void }) {
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<string | null>(null);

  async function run() {
    setBusy(true);
    setResult(null);
    try {
      const { updatedCount } = await api.reapplyRules();
      setResult(`Updated ${updatedCount} expense(s) this month.`);
      onDone();
    } catch (e) {
      setResult((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="rb-reapply">
      <button
        type="button"
        className="rb-btn"
        disabled={busy}
        onClick={run}
        title="Re-runs your active categorization rules against this calendar month's confirmed expenses only"
      >
        {busy ? (
          <>
            <span className="rb-spin" />
            Reapplying…
          </>
        ) : (
          "Reapply rules"
        )}
      </button>
      {result && <span className="rb-reapply-result">{result}</span>}
    </div>
  );
}

function TableCell({
  items,
  onUpdated,
  page,
  pageNumber,
  setPageNumber,
  loading
}: {
  items: ExpenseView[];
  onUpdated: (item: ExpenseView) => void;
  page: Page<ExpenseView> | null;
  pageNumber: number;
  setPageNumber: (updater: (p: number) => number) => void;
  loading: boolean;
}) {
  return (
    <div className="rb-cell" style={{ gridColumn: "span 12" }}>
      <h3>Recent expenses</h3>
      {loading ? (
        <p className="rb-empty">Loading…</p>
      ) : items.length === 0 ? (
        <p className="rb-empty">No expenses match these filters.</p>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table className="rb-expenses">
            <thead>
              <tr>
                <th>Date</th>
                <th>Category</th>
                <th>Description</th>
                <th>Source</th>
                <th style={{ textAlign: "right" }}>Amount</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <TableRow key={item.id} item={item} onUpdated={onUpdated} />
              ))}
            </tbody>
          </table>
        </div>
      )}
      {page && page.totalPages > 1 && (
        <div className="rb-pagination">
          <button
            type="button"
            className="rb-btn rb-btn-small"
            disabled={pageNumber === 0}
            onClick={() => setPageNumber((p) => p - 1)}
          >
            Prev
          </button>
          <span>
            {pageNumber + 1} / {page.totalPages}
          </span>
          <button
            type="button"
            className="rb-btn rb-btn-small"
            disabled={pageNumber + 1 >= page.totalPages}
            onClick={() => setPageNumber((p) => p + 1)}
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}

function TableRow({ item, onUpdated }: { item: ExpenseView; onUpdated: (item: ExpenseView) => void }) {
  const [editing, setEditing] = useState(false);
  const [category, setCategory] = useState(item.category);
  const [busy, setBusy] = useState(false);

  async function save() {
    if (category === item.category) {
      setEditing(false);
      return;
    }
    setBusy(true);
    try {
      const updated = await api.editExpense(item.id, { category });
      onUpdated(updated);
    } finally {
      setBusy(false);
      setEditing(false);
    }
  }

  return (
    <tr>
      <td className="rb-mono">{item.expenseDate}</td>
      <td>
        {editing ? (
          <input
            autoFocus
            className="rb-cat-select"
            value={category}
            disabled={busy}
            onChange={(e) => setCategory(e.target.value)}
            onBlur={save}
            onKeyDown={(e) => e.key === "Enter" && save()}
          />
        ) : (
          <button type="button" className="rb-cat-edit" onClick={() => setEditing(true)} title="Click to recategorize">
            {item.category}
          </button>
        )}
        {item.recategorizedAt && (
          <span className="rb-edited" title={`Originally ${item.originalCategory}`}>
            {" "}
            (edited)
          </span>
        )}
      </td>
      <td>{item.description ?? "—"}</td>
      <td>
        <span className="rb-src-tag">{item.sourceMode}</span>
      </td>
      <td className="rb-amount-cell rb-mono">{item.amount}</td>
    </tr>
  );
}
