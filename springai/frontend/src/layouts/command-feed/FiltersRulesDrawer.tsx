import { useState } from "react";
import { api } from "../../api/client";
import type { ExpenseFilters } from "../../api/types";
import { useRules } from "../../design/useRules";
import { categorySeriesIndex } from "../../design/categorySeriesIndex";
import { logout } from "../../design/auth";

function seriesColor(index: number) {
  return index === -1 ? "var(--ink-3)" : `var(--c${index + 1})`;
}

interface Props {
  open: boolean;
  onClose: () => void;
  filters: ExpenseFilters;
  categories: string[];
  onFilterChange: (f: ExpenseFilters) => void;
  onReapplyDone: () => void;
}

export default function FiltersRulesDrawer({
  open,
  onClose,
  filters,
  categories,
  onFilterChange,
  onReapplyDone
}: Props) {
  const rules = useRules();
  const [ruleText, setRuleText] = useState("");
  const [reapplyBusy, setReapplyBusy] = useState(false);
  const [reapplyResult, setReapplyResult] = useState<string | null>(null);

  function toggleCategory(cat: string) {
    const current = filters.categories ?? categories;
    const next = current.includes(cat) ? current.filter((c) => c !== cat) : [...current, cat];
    const isAll = next.length === categories.length;
    onFilterChange({ ...filters, categories: isAll ? undefined : next });
  }

  async function reapply() {
    setReapplyBusy(true);
    setReapplyResult(null);
    try {
      const { updatedCount } = await api.reapplyRules();
      setReapplyResult(`Updated ${updatedCount} expense(s) this month.`);
      onReapplyDone();
    } catch (e) {
      setReapplyResult((e as Error).message);
    } finally {
      setReapplyBusy(false);
    }
  }

  function submitRule() {
    if (!ruleText.trim()) return;
    rules.addRule(ruleText);
    setRuleText("");
  }

  return (
    <>
      <div className={`cf-scrim ${open ? "cf-open" : ""}`} onClick={onClose} />
      <aside className={`cf-drawer ${open ? "cf-open" : ""}`}>
        <h2>Filters &amp; rules</h2>
        <h3>Search</h3>
        <div className="cf-field">
          <input
            type="text"
            placeholder="Search description or category…"
            value={filters.search ?? ""}
            onChange={(e) => onFilterChange({ ...filters, search: e.target.value || undefined })}
          />
        </div>
        <h3>Categories</h3>
        <div className="cf-chip-row">
          {categories.map((c) => {
            const active = !filters.categories || filters.categories.includes(c);
            return (
              <button key={c} type="button" className={`cf-chip ${active ? "cf-active" : ""}`} onClick={() => toggleCategory(c)}>
                <span className="cf-dot" style={{ background: seriesColor(categorySeriesIndex(c, categories)) }} />
                {c}
              </button>
            );
          })}
        </div>
        <h3>Auto-categorize rules</h3>
        <div>
          {rules.rules.length === 0 && (
            <p className="cf-empty" style={{ padding: 0 }}>
              No custom rules yet.
            </p>
          )}
          {rules.rules.map((r) => (
            <div className="cf-rule-row" key={r.id}>
              <span className={r.active ? "" : "cf-inactive"}>{r.instructionText}</span>
              {r.active && (
                <button type="button" className="cf-rule-deactivate" onClick={() => rules.deactivateRule(r.id)}>
                  ✕
                </button>
              )}
            </div>
          ))}
        </div>
        <div className="cf-add-rule">
          <input
            value={ruleText}
            placeholder="e.g. fast food should be Dining"
            onChange={(e) => setRuleText(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && submitRule()}
          />
          <button type="button" className="cf-btn cf-btn-small" disabled={rules.busy} onClick={submitRule}>
            Add
          </button>
        </div>
        <button
          type="button"
          className="cf-btn"
          disabled={reapplyBusy}
          onClick={reapply}
          title="Re-runs your active categorization rules against this calendar month's confirmed expenses only"
        >
          {reapplyBusy ? (
            <>
              <span className="cf-spin" />
              Reapplying…
            </>
          ) : (
            "Reapply rules to this month"
          )}
        </button>
        {reapplyResult && <span className="cf-reapply-result">{reapplyResult}</span>}
        <h3>Account</h3>
        <button type="button" className="cf-btn cf-btn-small" onClick={logout}>
          Log out
        </button>
      </aside>
    </>
  );
}
