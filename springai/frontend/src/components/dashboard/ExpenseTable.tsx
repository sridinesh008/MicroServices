import { useState } from "react";
import { api } from "../../api/client";
import type { ExpenseView } from "../../api/types";

interface Props {
  items: ExpenseView[];
  onUpdated: (item: ExpenseView) => void;
}

export default function ExpenseTable({ items, onUpdated }: Props) {
  if (items.length === 0) {
    return <p className="text-sm text-ink-muted py-6 text-center">No expenses match these filters.</p>;
  }

  return (
    <div className="overflow-x-auto border border-border rounded-xl bg-surface">
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-xs text-ink-muted border-b border-[color:var(--gridline)]">
            <th className="px-3 py-2 font-medium">Date</th>
            <th className="px-3 py-2 font-medium">Category</th>
            <th className="px-3 py-2 font-medium">Description</th>
            <th className="px-3 py-2 font-medium">Source</th>
            <th className="px-3 py-2 font-medium text-right">Amount</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <Row key={item.id} item={item} onUpdated={onUpdated} />
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Row({ item, onUpdated }: { item: ExpenseView; onUpdated: (item: ExpenseView) => void }) {
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
    <tr className="border-b border-[color:var(--gridline)] last:border-0">
      <td className="px-3 py-2 text-ink-secondary" style={{ fontVariantNumeric: "tabular-nums" }}>
        {item.expenseDate}
      </td>
      <td className="px-3 py-2">
        {editing ? (
          <input
            autoFocus
            className="rounded-md border border-border bg-transparent px-2 py-0.5 text-sm"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            onBlur={save}
            onKeyDown={(e) => e.key === "Enter" && save()}
            disabled={busy}
          />
        ) : (
          <button
            onClick={() => setEditing(true)}
            className="underline decoration-dotted underline-offset-2"
            title="Click to recategorize"
          >
            {item.category}
          </button>
        )}
        {item.recategorizedAt && (
          <span className="ml-1 text-[10px] text-ink-muted" title={`Originally ${item.originalCategory}`}>
            (edited)
          </span>
        )}
      </td>
      <td className="px-3 py-2 text-ink-secondary">{item.description ?? "—"}</td>
      <td className="px-3 py-2 text-ink-muted text-xs">{item.sourceMode}</td>
      <td className="px-3 py-2 text-right" style={{ fontVariantNumeric: "tabular-nums" }}>
        {item.amount}
      </td>
    </tr>
  );
}
