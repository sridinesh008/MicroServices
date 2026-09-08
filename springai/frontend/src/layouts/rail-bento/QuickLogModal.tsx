import { useRef, useState } from "react";
import { api } from "../../api/client";
import type { ExpenseView } from "../../api/types";

const MAX_IMAGES = 3;

interface Props {
  onClose: () => void;
  onDraft: (items: ExpenseView[]) => void;
}

type Tab = "manual" | "photo";

export default function QuickLogModal({ onClose, onDraft }: Props) {
  const [tab, setTab] = useState<Tab>("manual");

  return (
    <div
      className="rb-overlay"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="rb-modal">
        <h3>Quick log</h3>
        <div className="rb-toggle-row">
          <button type="button" className={`rb-tgl ${tab === "manual" ? "active" : ""}`} onClick={() => setTab("manual")}>
            Manual
          </button>
          <button type="button" className={`rb-tgl ${tab === "photo" ? "active" : ""}`} onClick={() => setTab("photo")}>
            Photo
          </button>
        </div>
        {tab === "manual" ? <ManualTab onClose={onClose} /> : <PhotoTab onClose={onClose} onDraft={onDraft} />}
      </div>
    </div>
  );
}

function ManualTab({ onClose }: { onClose: () => void }) {
  const [description, setDescription] = useState("");
  const [amount, setAmount] = useState("");
  const [category, setCategory] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function save() {
    if (!description.trim() || !amount.trim() || !category.trim()) {
      setError("Description, amount and category are all required.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api.submitManual(description, amount, category);
      onClose();
    } catch (e) {
      setError((e as Error).message);
      setBusy(false);
    }
  }

  return (
    <>
      <label className="rb-field">
        <span>Description</span>
        <input value={description} placeholder="e.g. Trader Joe's" onChange={(e) => setDescription(e.target.value)} />
      </label>
      <label className="rb-field">
        <span>Amount</span>
        <input value={amount} inputMode="decimal" placeholder="0.00" onChange={(e) => setAmount(e.target.value)} />
      </label>
      <label className="rb-field">
        <span>Category</span>
        <input value={category} placeholder="e.g. Groceries" onChange={(e) => setCategory(e.target.value)} />
      </label>
      {error && <p className="rb-error">{error}</p>}
      <div className="rb-modal-actions">
        <button type="button" className="rb-btn rb-btn-small" onClick={onClose}>
          Cancel
        </button>
        <button type="button" className="rb-btn rb-btn-small rb-primary" disabled={busy} onClick={save}>
          {busy ? "Saving…" : "Save"}
        </button>
      </div>
    </>
  );
}

function PhotoTab({ onClose, onDraft }: Props) {
  const [images, setImages] = useState<File[]>([]);
  const [caption, setCaption] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  function addImages(files: FileList | null) {
    if (!files) return;
    setImages((prev) => [...prev, ...Array.from(files)].slice(0, MAX_IMAGES));
  }

  async function save() {
    if (images.length === 0) {
      setError("Add at least one receipt image.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const draft = await api.submitImageExpense(images, caption);
      onDraft(draft.items);
      onClose();
    } catch (e) {
      setError((e as Error).message);
      setBusy(false);
    }
  }

  return (
    <>
      <div className="rb-photo-strip">
        {images.map((file, i) => (
          <div className="rb-photo-thumb" key={i}>
            <img src={URL.createObjectURL(file)} alt="" />
            <button
              type="button"
              onClick={() => setImages((prev) => prev.filter((_, idx) => idx !== i))}
              aria-label="Remove image"
            >
              ×
            </button>
          </div>
        ))}
        {images.length < MAX_IMAGES && (
          <button type="button" className="rb-photo-add" onClick={() => fileInput.current?.click()}>
            + Add
          </button>
        )}
      </div>
      <input ref={fileInput} type="file" accept="image/*" multiple hidden onChange={(e) => addImages(e.target.files)} />
      <label className="rb-field">
        <span>Caption (optional)</span>
        <input value={caption} placeholder="#newCategorizationRule ..." onChange={(e) => setCaption(e.target.value)} />
      </label>
      {error && <p className="rb-error">{error}</p>}
      <div className="rb-modal-actions">
        <button type="button" className="rb-btn rb-btn-small" onClick={onClose}>
          Cancel
        </button>
        <button type="button" className="rb-btn rb-btn-small rb-primary" disabled={busy} onClick={save}>
          {busy ? "Saving…" : "Save"}
        </button>
      </div>
    </>
  );
}
