import { useState } from "react";
import { api } from "../../api/client";
import type { ExpenseView } from "../../api/types";

export default function PendingCard({ item, onDone }: { item: ExpenseView; onDone: () => void }) {
  const [category, setCategory] = useState(item.category);
  const [amount, setAmount] = useState(item.amount);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function confirm() {
    setBusy(true);
    setError(null);
    try {
      await api.confirmExpense(item.id, { category, amount });
      onDone();
    } catch (e) {
      setError((e as Error).message);
      setBusy(false);
    }
  }

  async function discard() {
    setBusy(true);
    setError(null);
    try {
      await api.discardExpense(item.id);
      onDone();
    } catch (e) {
      setError((e as Error).message);
      setBusy(false);
    }
  }

  return (
    <div className="cf-pending">
      <div className="cf-head">Confirm expense</div>
      <div className="cf-grid">
        <label className="cf-f">
          <span>Category</span>
          <input value={category} disabled={busy} onChange={(e) => setCategory(e.target.value)} />
        </label>
        <label className="cf-f">
          <span>Amount</span>
          <input value={amount} disabled={busy} inputMode="decimal" onChange={(e) => setAmount(e.target.value)} />
        </label>
      </div>
      <p className="cf-pending-date">{item.expenseDate}</p>
      {error && <p className="cf-error">{error}</p>}
      <div className="cf-pending-actions">
        <button type="button" className="cf-btn cf-btn-small" disabled={busy} onClick={discard}>
          Discard
        </button>
        <button type="button" className="cf-btn cf-btn-small cf-primary" disabled={busy} onClick={confirm}>
          Confirm
        </button>
      </div>
    </div>
  );
}
