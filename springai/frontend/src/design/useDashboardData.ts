import { useCallback, useEffect, useState } from "react";
import { api } from "../api/client";
import type { AggregateView, ExpenseFilters, ExpenseView, Page } from "../api/types";

/** Filtered/paginated expense search + category list + aggregates -- shared between both layout skins. */
export function useDashboardData() {
  const [filters, setFilters] = useState<ExpenseFilters>({});
  const [categories, setCategories] = useState<string[]>([]);
  const [page, setPage] = useState<Page<ExpenseView> | null>(null);
  const [pageNumber, setPageNumber] = useState(0);
  const [aggregates, setAggregates] = useState<AggregateView | null>(null);
  // True only until the very first search resolves -- lets views tell "still loading" apart
  // from "genuinely no expenses match", instead of showing an empty state while waiting.
  const [initialLoading, setInitialLoading] = useState(true);

  useEffect(() => {
    api.categories().then(setCategories).catch(() => undefined);
  }, []);

  useEffect(() => {
    const handle = setTimeout(() => {
      api
        .searchExpenses(filters, pageNumber)
        .then(setPage)
        .catch(() => undefined)
        .finally(() => setInitialLoading(false));
      api.aggregates(filters).then(setAggregates).catch(() => undefined);
    }, 300);
    return () => clearTimeout(handle);
  }, [filters, pageNumber]);

  const refresh = useCallback(() => {
    api.searchExpenses(filters, pageNumber).then(setPage).catch(() => undefined);
    api.aggregates(filters).then(setAggregates).catch(() => undefined);
    api.categories().then(setCategories).catch(() => undefined);
  }, [filters, pageNumber]);

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

  return {
    filters,
    categories,
    page,
    pageNumber,
    setPageNumber,
    aggregates,
    totalSpend,
    initialLoading,
    refresh,
    onFilterChange,
    onExpenseUpdated
  };
}
