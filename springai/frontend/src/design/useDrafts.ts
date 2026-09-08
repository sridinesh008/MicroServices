import { useCallback, useEffect, useState } from "react";
import { api } from "../api/client";
import type { ExpenseView } from "../api/types";

/** Pending (PENDING_CONFIRMATION) expenses awaiting review -- shared between both layout skins. */
export function useDrafts() {
  const [drafts, setDrafts] = useState<ExpenseView[]>([]);

  const refresh = useCallback(() => {
    api.drafts().then(setDrafts).catch(() => undefined);
  }, []);

  useEffect(refresh, [refresh]);

  function addDrafts(items: ExpenseView[]) {
    setDrafts((prev) => [...prev, ...items]);
  }

  function removeDraft(id: number) {
    setDrafts((prev) => prev.filter((d) => d.id !== id));
  }

  return { drafts, refresh, addDrafts, removeDraft, setDrafts };
}
