import { useState } from "react";
import type { AggregateView, ExpenseView } from "../../api/types";
import { categorySeriesIndex } from "../../design/categorySeriesIndex";
import PhotoPanel from "./PhotoPanel";

function seriesColor(index: number) {
  return index === -1 ? "var(--ink-3)" : `var(--c${index + 1})`;
}

interface Props {
  onDrawerToggle: () => void;
  onSend: (message: string) => void;
  sending: boolean;
  aggregates: AggregateView | null;
  categories: string[];
  onDraft: (items: ExpenseView[]) => void;
}

export default function TopBar({ onDrawerToggle, onSend, sending, aggregates, categories, onDraft }: Props) {
  const [text, setText] = useState("");
  const [photoOpen, setPhotoOpen] = useState(false);

  function submit() {
    const value = text.trim();
    if (!value || sending) return;
    setText("");
    onSend(value);
  }

  const totals = aggregates?.totalsByCategory ?? [];
  const max = Math.max(...totals.map((t) => Number(t.total)), 1);

  return (
    <div className="cf-topbar">
      <div className="cf-topbar-inner">
        <div className="cf-brand-row">
          <span className="cf-brand">FinTrack</span>
          <div className="cf-brand-actions">
            <button
              type="button"
              className="cf-icon-btn"
              title="Attach receipt photo"
              onClick={() => setPhotoOpen((v) => !v)}
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M4 7h3l2-3h6l2 3h3v13H4z" />
                <circle cx="12" cy="13" r="4" />
              </svg>
            </button>
            <button type="button" className="cf-icon-btn" title="Filters & rules" onClick={onDrawerToggle}>
              <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <line x1="4" y1="6" x2="20" y2="6" />
                <line x1="4" y1="12" x2="20" y2="12" />
                <line x1="4" y1="18" x2="20" y2="18" />
                <circle cx="9" cy="6" r="1.6" fill="currentColor" stroke="none" />
                <circle cx="15" cy="12" r="1.6" fill="currentColor" stroke="none" />
                <circle cx="11" cy="18" r="1.6" fill="currentColor" stroke="none" />
              </svg>
            </button>
          </div>
        </div>
        <form
          className="cf-command"
          onSubmit={(e) => {
            e.preventDefault();
            submit();
          }}
        >
          <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="11" cy="11" r="7" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <input
            type="text"
            placeholder="Ask FinTrack, or log a purchase…"
            autoComplete="off"
            value={text}
            disabled={sending}
            onChange={(e) => setText(e.target.value)}
          />
          <button type="submit" title="Send" disabled={sending}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.6">
              <path d="M22 2 11 13M22 2l-7 20-4-9-9-4 20-7Z" />
            </svg>
          </button>
        </form>
        {photoOpen && (
          <PhotoPanel
            onClose={() => setPhotoOpen(false)}
            onDraft={(items) => {
              onDraft(items);
              setPhotoOpen(false);
            }}
          />
        )}
        {totals.length > 0 && (
          <div className="cf-strip">
            {totals.map((t) => (
              <div className="cf-pill" key={t.category}>
                <div className="cf-name">
                  <span
                    className="cf-dot"
                    style={{ background: seriesColor(categorySeriesIndex(t.category, categories)) }}
                  />
                  {t.category}
                </div>
                <div className="cf-amt cf-mono">{Number(t.total).toFixed(2)}</div>
                <div className="cf-bar">
                  <i
                    style={{
                      width: `${(Number(t.total) / max) * 100}%`,
                      background: seriesColor(categorySeriesIndex(t.category, categories))
                    }}
                  />
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
