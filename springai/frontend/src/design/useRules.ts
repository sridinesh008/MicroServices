import { useEffect, useState } from "react";
import { api } from "../api/client";
import type { RuleView } from "../api/types";

/** Categorization rules CRUD -- shared between both layout skins (extracted from the old RulesPanel). */
export function useRules() {
  const [rules, setRules] = useState<RuleView[]>([]);
  const [busy, setBusy] = useState(false);

  function load() {
    api.listRules().then(setRules).catch(() => undefined);
  }

  useEffect(load, []);

  async function addRule(instructionText: string) {
    if (!instructionText.trim()) return;
    setBusy(true);
    try {
      await api.createRule(instructionText);
      load();
    } finally {
      setBusy(false);
    }
  }

  async function deactivateRule(id: number) {
    await api.deactivateRule(id);
    load();
  }

  return { rules, busy, addRule, deactivateRule, reload: load };
}
