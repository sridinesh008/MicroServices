import { useState } from "react";
import { api } from "../../api/client";

export default function ReapplyRulesButton({ onDone }: { onDone: () => void }) {
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
    <div className="flex items-center gap-2">
      <button
        onClick={run}
        disabled={busy}
        title="Re-runs your active categorization rules against this calendar month's confirmed expenses only"
        className="px-3 py-1.5 rounded-md border border-border text-sm text-ink-secondary hover:bg-plane disabled:opacity-50"
      >
        {busy ? "Reapplying…" : "Reapply rules to this month"}
      </button>
      {result && <span className="text-xs text-ink-muted">{result}</span>}
    </div>
  );
}
