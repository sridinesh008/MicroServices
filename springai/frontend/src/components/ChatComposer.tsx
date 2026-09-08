import { useRef, useState } from "react";
import { api } from "../api/client";
import type { ExpenseView } from "../api/types";

const MAX_IMAGES = 3;

interface Props {
  onChatMessage: (message: string) => void;
  onExpenseDraft: (items: ExpenseView[]) => void;
  disabled: boolean;
}

type Mode = "chat" | "expense";
type ExpenseMode = "text" | "image";

export default function ChatComposer({ onChatMessage, onExpenseDraft, disabled }: Props) {
  const [mode, setMode] = useState<Mode>("chat");
  const [expenseMode, setExpenseMode] = useState<ExpenseMode>("text");
  const [text, setText] = useState("");
  const [images, setImages] = useState<File[]>([]);
  const [caption, setCaption] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  function addImages(files: FileList | null) {
    if (!files) return;
    const next = [...images, ...Array.from(files)].slice(0, MAX_IMAGES);
    setImages(next);
  }

  function removeImage(index: number) {
    setImages(images.filter((_, i) => i !== index));
  }

  async function submit() {
    setError(null);
    if (mode === "chat") {
      if (!text.trim()) return;
      const message = text;
      setText("");
      onChatMessage(message);
      return;
    }

    setBusy(true);
    try {
      if (expenseMode === "text") {
        if (!text.trim()) return;
        const draft = await api.submitTextExpense(text);
        onExpenseDraft(draft.items);
        setText("");
      } else {
        if (images.length === 0) {
          setError("Add at least one receipt image.");
          return;
        }
        const draft = await api.submitImageExpense(images, caption);
        onExpenseDraft(draft.items);
        setImages([]);
        setCaption("");
      }
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="border border-border rounded-xl bg-surface p-3 space-y-3">
      <div className="flex gap-1">
        <ModeButton active={mode === "chat"} onClick={() => setMode("chat")} label="Chat" />
        <ModeButton active={mode === "expense"} onClick={() => setMode("expense")} label="Log Expense" />
      </div>

      {mode === "expense" && (
        <div className="flex gap-1">
          <ModeButton active={expenseMode === "text"} onClick={() => setExpenseMode("text")} label="Text" small />
          <ModeButton active={expenseMode === "image"} onClick={() => setExpenseMode("image")} label="Photo" small />
        </div>
      )}

      {mode === "expense" && expenseMode === "image" && (
        <div className="space-y-2">
          <div className="flex gap-2 flex-wrap">
            {images.map((file, i) => (
              <div key={i} className="relative">
                <img
                  src={URL.createObjectURL(file)}
                  alt=""
                  className="w-16 h-16 object-cover rounded-md border border-border"
                />
                <button
                  onClick={() => removeImage(i)}
                  className="absolute -top-2 -right-2 bg-surface border border-border rounded-full w-5 h-5 text-xs"
                  aria-label="Remove image"
                >
                  &times;
                </button>
              </div>
            ))}
            {images.length < MAX_IMAGES && (
              <button
                onClick={() => fileInput.current?.click()}
                className="w-16 h-16 rounded-md border border-dashed border-border text-ink-muted text-xs"
              >
                + Add
              </button>
            )}
          </div>
          <input
            ref={fileInput}
            type="file"
            accept="image/*"
            multiple
            hidden
            onChange={(e) => addImages(e.target.files)}
          />
          <input
            className="w-full rounded-md border border-border bg-transparent px-2 py-1 text-sm"
            placeholder="Optional caption (e.g. #newCategorizationRule ...)"
            value={caption}
            onChange={(e) => setCaption(e.target.value)}
          />
        </div>
      )}

      {(mode === "chat" || expenseMode === "text") && (
        <textarea
          className="w-full rounded-md border border-border bg-transparent px-3 py-2 text-sm resize-none"
          rows={2}
          placeholder={
            mode === "chat"
              ? "Ask about your spending, e.g. 'how much did I spend on fruits in the last 3 days?'"
              : "e.g. apple 1kg 100rs, milk 1lt 30rs"
          }
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              submit();
            }
          }}
        />
      )}

      {error && <p className="text-sm text-[color:var(--status-critical)]">{error}</p>}

      <div className="flex justify-end">
        <button
          onClick={submit}
          disabled={disabled || busy}
          className="px-4 py-1.5 rounded-md bg-accent text-white text-sm disabled:opacity-50"
        >
          {busy ? "Sending…" : mode === "chat" ? "Send" : "Submit"}
        </button>
      </div>
    </div>
  );
}

function ModeButton({
  active,
  onClick,
  label,
  small
}: {
  active: boolean;
  onClick: () => void;
  label: string;
  small?: boolean;
}) {
  return (
    <button
      onClick={onClick}
      className={`rounded-md font-medium transition-colors ${small ? "px-2 py-0.5 text-xs" : "px-3 py-1 text-sm"} ${
        active ? "bg-accent text-white" : "bg-plane text-ink-secondary"
      }`}
    >
      {label}
    </button>
  );
}
