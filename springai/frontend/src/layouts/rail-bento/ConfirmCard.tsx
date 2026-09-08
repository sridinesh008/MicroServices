import { useState } from "react";
import { api } from "../../api/client";
import type { ExpenseView } from "../../api/types";

export default function ConfirmCard({ item, onDone }: { item: ExpenseView; onDone: () => void }) {
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
    <div className="rb-confirm-card">
      <div className="rb-confirm-head">
        <span>Confirm expense</span>
      </div>
      <div className="rb-confirm-grid">
        <label className="rb-field">
          <span>Category</span>
          <input value={category} disabled={busy} onChange={(e) => setCategory(e.target.value)} />
        </label>
        <label className="rb-field">
          <span>Amount</span>
          <input value={amount} disabled={busy} inputMode="decimal" onChange={(e) => setAmount(e.target.value)} />
        </label>
      </div>
      <p className="rb-confirm-date">{item.expenseDate}</p>
      {error && <p className="rb-error">{error}</p>}
      <div className="rb-confirm-actions">
        <button type="button" className="rb-btn rb-btn-small" disabled={busy} onClick={discard}>
          Discard
        </button>
        <button type="button" className="rb-btn rb-btn-small rb-primary" disabled={busy} onClick={confirm}>
          Confirm
        </button>
      </div>
    </div>
  );
}
