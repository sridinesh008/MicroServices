import { useEffect, useState } from "react";
import { api } from "../api/client";
import type { AggregateView, ExpenseFilters, ExpenseView, Page } from "../api/types";
import FilterPanel from "../components/dashboard/FilterPanel";
import ExpenseTable from "../components/dashboard/ExpenseTable";
import CategoryBarChart from "../components/dashboard/CategoryBarChart";
import MonthlyTrendChart from "../components/dashboard/MonthlyTrendChart";
import ReapplyRulesButton from "../components/dashboard/ReapplyRulesButton";
import RulesPanel from "../components/dashboard/RulesPanel";

export default function DashboardPage() {
  const [filters, setFilters] = useState<ExpenseFilters>({});
  const [categories, setCategories] = useState<string[]>([]);
  const [page, setPage] = useState<Page<ExpenseView> | null>(null);
  const [pageNumber, setPageNumber] = useState(0);
  const [aggregates, setAggregates] = useState<AggregateView | null>(null);

  useEffect(() => {
    api.categories().then(setCategories).catch(() => undefined);
  }, []);

  useEffect(() => {
    const handle = setTimeout(() => {
      api.searchExpenses(filters, pageNumber).then(setPage).catch(() => undefined);
      api.aggregates(filters).then(setAggregates).catch(() => undefined);
    }, 300);
    return () => clearTimeout(handle);
  }, [filters, pageNumber]);

  function refresh() {
    api.searchExpenses(filters, pageNumber).then(setPage).catch(() => undefined);
    api.aggregates(filters).then(setAggregates).catch(() => undefined);
    api.categories().then(setCategories).catch(() => undefined);
  }

  function onFilterChange(next: ExpenseFilters) {
    setFilters(next);
    setPageNumber(0);
  }

  function onExpenseUpdated(updated: ExpenseView) {
    setPage((prev) =>
      prev ? { ...prev, content: prev.content.map((e) => (e.id === updated.id ? updated : e)) } : prev
    );
  }

  const totalSpend = aggregates?.totalsByCategory.reduce((sum, c) => sum + Number(c.total), 0) ?? 0;

  return (
    <div className="grid grid-cols-1 lg:grid-cols-[260px_1fr] gap-4">
      <div className="space-y-4">
        <FilterPanel filters={filters} categories={categories} onChange={onFilterChange} />
        <RulesPanel />
      </div>

      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-xs text-ink-muted">Total (filtered)</p>
            <p className="text-3xl font-semibold" style={{ fontVariantNumeric: "tabular-nums" }}>
              {totalSpend.toFixed(2)}
            </p>
          </div>
          <ReapplyRulesButton onDone={refresh} />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="border border-border rounded-xl bg-surface p-4">
            <h3 className="text-sm font-medium mb-3">By category</h3>
            <CategoryBarChart data={aggregates?.totalsByCategory ?? []} allCategories={categories} />
          </div>
          <div className="border border-border rounded-xl bg-surface p-4">
            <h3 className="text-sm font-medium mb-3">By month</h3>
            <MonthlyTrendChart data={aggregates?.totalsByMonth ?? []} />
          </div>
        </div>

        <ExpenseTable items={page?.content ?? []} onUpdated={onExpenseUpdated} />

        {page && page.totalPages > 1 && (
          <div className="flex justify-center gap-2 text-sm">
            <button
              disabled={pageNumber === 0}
              onClick={() => setPageNumber((p) => p - 1)}
              className="px-2 py-1 rounded-md border border-border disabled:opacity-40"
            >
              Prev
            </button>
            <span className="text-ink-muted">
              {pageNumber + 1} / {page.totalPages}
            </span>
            <button
              disabled={pageNumber + 1 >= page.totalPages}
              onClick={() => setPageNumber((p) => p + 1)}
              className="px-2 py-1 rounded-md border border-border disabled:opacity-40"
            >
              Next
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
