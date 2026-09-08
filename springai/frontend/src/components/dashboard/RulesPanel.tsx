import { useEffect, useState } from "react";
import { api } from "../../api/client";
import type { RuleView } from "../../api/types";

export default function RulesPanel() {
  const [rules, setRules] = useState<RuleView[]>([]);
  const [newRule, setNewRule] = useState("");
  const [busy, setBusy] = useState(false);

  function load() {
    api.listRules().then(setRules).catch(() => undefined);
  }

  useEffect(load, []);

  async function create() {
    if (!newRule.trim()) return;
    setBusy(true);
    try {
      await api.createRule(newRule);
      setNewRule("");
      load();
    } finally {
      setBusy(false);
    }
  }

  async function deactivate(id: number) {
    await api.deactivateRule(id);
    load();
  }

  return (
    <div className="border border-border rounded-xl bg-surface p-4 space-y-3">
      <h3 className="font-medium text-sm">Categorization rules</h3>
      <div className="flex gap-2">
        <input
          className="flex-1 rounded-md border border-border bg-transparent px-2 py-1 text-sm"
          placeholder="e.g. fast food purchases should be Dining"
          value={newRule}
          onChange={(e) => setNewRule(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && create()}
        />
        <button
          onClick={create}
          disabled={busy}
          className="px-3 py-1 rounded-md bg-accent text-white text-sm disabled:opacity-50"
        >
          Add
        </button>
      </div>
      <ul className="space-y-1">
        {rules.map((rule) => (
          <li key={rule.id} className="flex items-center justify-between text-sm gap-2">
            <span className={rule.active ? "" : "line-through text-ink-muted"}>{rule.instructionText}</span>
            {rule.active && (
              <button onClick={() => deactivate(rule.id)} className="text-xs text-ink-muted underline shrink-0">
                deactivate
              </button>
            )}
          </li>
        ))}
        {rules.length === 0 && <li className="text-sm text-ink-muted">No custom rules yet.</li>}
      </ul>
    </div>
  );
}
