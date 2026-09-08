import { useState } from "react";
import { api } from "../api/client";
import type { ExpenseView } from "../api/types";

interface Props {
  items: ExpenseView[];
  onChange: (items: ExpenseView[]) => void;
  onClose: () => void;
}

/** Human-in-the-loop review step: nothing here is a real expense until Confirm is clicked. */
export default function ExpenseConfirmationModal({ items, onChange, onClose }: Props) {
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  if (items.length === 0) {
    return null;
  }

  async function confirm(item: ExpenseView, category: string, amount: string) {
    setBusyId(item.id);
    setError(null);
    try {
      await api.confirmExpense(item.id, { category, amount });
      onChange(items.filter((i) => i.id !== item.id));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusyId(null);
    }
  }

  async function discard(item: ExpenseView) {
    setBusyId(item.id);
    setError(null);
    try {
      await api.discardExpense(item.id);
      onChange(items.filter((i) => i.id !== item.id));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className="bg-surface rounded-xl border border-border max-w-[80%] self-start">
      <div className="p-3 border-b border-border flex items-center justify-between">
        <h2 className="font-semibold text-sm">Review before saving</h2>
        <button onClick={onClose} className="text-ink-muted hover:text-ink-secondary text-xs">
          Dismiss
        </button>
      </div>
      {error && <p className="px-3 pt-2 text-sm text-[color:var(--status-critical)]">{error}</p>}
      <ul className="divide-y divide-[color:var(--gridline)]">
        {items.map((item) => (
          <DraftRow
            key={item.id}
            item={item}
            busy={busyId === item.id}
            onConfirm={confirm}
            onDiscard={discard}
          />
        ))}
      </ul>
    </div>
  );
}

function DraftRow({
  item,
  busy,
  onConfirm,
  onDiscard
}: {
  item: ExpenseView;
  busy: boolean;
  onConfirm: (item: ExpenseView, category: string, amount: string) => void;
  onDiscard: (item: ExpenseView) => void;
}) {
  const [category, setCategory] = useState(item.category);
  const [amount, setAmount] = useState(item.amount);

  return (
    <li className="p-4 flex flex-col gap-2">
      <div className="flex gap-2">
        <input
          className="flex-1 rounded-md border border-border bg-transparent px-2 py-1 text-sm"
          value={category}
          onChange={(e) => setCategory(e.target.value)}
          aria-label="Category"
        />
        <input
          className="w-28 rounded-md border border-border bg-transparent px-2 py-1 text-sm text-right"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          inputMode="decimal"
          aria-label="Amount"
        />
      </div>
      <p className="text-xs text-ink-muted">{item.expenseDate}</p>
      <div className="flex gap-2 justify-end">
        <button
          disabled={busy}
          onClick={() => onDiscard(item)}
          className="px-3 py-1 rounded-md text-sm border border-border text-ink-secondary hover:bg-plane disabled:opacity-50"
        >
          Discard
        </button>
        <button
          disabled={busy}
          onClick={() => onConfirm(item, category, amount)}
          className="px-3 py-1 rounded-md text-sm bg-accent text-white disabled:opacity-50"
        >
          Confirm
        </button>
      </div>
    </li>
  );
}
