import { useState } from "react";
import { api } from "../../api/client";
import type { ExpenseView } from "../../api/types";
import type { ChatMessage } from "../../design/useChat";
import { categorySeriesIndex } from "../../design/categorySeriesIndex";

function seriesColor(index: number) {
  return index === -1 ? "var(--ink-3)" : `var(--c${index + 1})`;
}

function todayIso() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function dayLabel(dateIso: string, today: string) {
  if (dateIso === today) return "Today";
  const diffDays = Math.round((new Date(today).getTime() - new Date(dateIso).getTime()) / 86400000);
  if (diffDays === 1) return "Yesterday";
  if (diffDays > 0 && diffDays < 7) return "This week";
  return "Earlier";
}

type Item =
  | { type: "expense"; date: string; expense: ExpenseView }
  | { type: "exchange"; date: string; q: string; a: string };

interface Props {
  expenses: ExpenseView[];
  messages: ChatMessage[];
  allCategories: string[];
  onExpenseUpdated: (item: ExpenseView) => void;
  loading: boolean;
}

export default function Feed({ expenses, messages, allCategories, onExpenseUpdated, loading }: Props) {
  const today = todayIso();

  const exchanges: { q: string; a: string }[] = [];
  for (let i = 0; i < messages.length - 1; i++) {
    if (messages[i].role === "user" && messages[i + 1].role === "assistant") {
      exchanges.push({ q: messages[i].text, a: messages[i + 1].text });
    }
  }

  const items: Item[] = [
    ...expenses.map((e): Item => ({ type: "expense", date: e.expenseDate, expense: e })),
    ...exchanges.map((ex): Item => ({ type: "exchange", date: today, ...ex }))
  ].sort((a, b) => b.date.localeCompare(a.date));

  if (items.length === 0) {
    return <p className="cf-empty">{loading ? "Loading…" : "Nothing here yet -- ask a question or log a purchase above."}</p>;
  }

  let lastLabel: string | null = null;

  return (
    <div className="cf-feed-wrap">
      {items.map((item, i) => {
        const label = dayLabel(item.date, today);
        const showDivider = label !== lastLabel;
        lastLabel = label;
        return (
          <div key={i}>
            {showDivider && <div className="cf-date-divider">{label}</div>}
            {item.type === "expense" ? (
              <ExpenseRow item={item.expense} allCategories={allCategories} onUpdated={onExpenseUpdated} />
            ) : (
              <div className="cf-exchange">
                <div className="cf-q">{item.q}</div>
                <div className="cf-a">{item.a || "…"}</div>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}

function ExpenseRow({
  item,
  allCategories,
  onUpdated
}: {
  item: ExpenseView;
  allCategories: string[];
  onUpdated: (i: ExpenseView) => void;
}) {
  const [category, setCategory] = useState(item.category);
  const [busy, setBusy] = useState(false);
  const color = seriesColor(categorySeriesIndex(item.category, allCategories));
  const label = item.description ?? item.category;

  async function changeCategory(next: string) {
    setCategory(next);
    setBusy(true);
    try {
      const updated = await api.editExpense(item.id, { category: next });
      onUpdated(updated);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="cf-feed-item">
      <div className="cf-icon-avatar" style={{ background: color }}>
        {label[0]?.toUpperCase() ?? "?"}
      </div>
      <div className="cf-body">
        <div className="cf-merchant">{label}</div>
        <div className="cf-meta">
          <span className="cf-tag">{item.sourceMode}</span>
          <span>{item.expenseDate}</span>
        </div>
      </div>
      <select className="cf-cat-select" value={category} disabled={busy} onChange={(e) => changeCategory(e.target.value)}>
        {!allCategories.includes(category) && <option value={category}>{category}</option>}
        {allCategories.map((c) => (
          <option key={c} value={c}>
            {c}
          </option>
        ))}
      </select>
      <div className="cf-amt cf-mono">{item.amount}</div>
    </div>
  );
}
