import { useRef, useState } from "react";
import { api } from "../../api/client";
import type { ExpenseView } from "../../api/types";

const MAX_IMAGES = 3;

interface Props {
  onClose: () => void;
  onDraft: (items: ExpenseView[]) => void;
}

export default function PhotoPanel({ onClose, onDraft }: Props) {
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
    } catch (e) {
      setError((e as Error).message);
      setBusy(false);
    }
  }

  return (
    <div className="cf-photo-panel">
      <div className="cf-photo-strip">
        {images.map((file, i) => (
          <div className="cf-photo-thumb" key={i}>
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
          <button type="button" className="cf-photo-add" onClick={() => fileInput.current?.click()}>
            + Add
          </button>
        )}
      </div>
      <input ref={fileInput} type="file" accept="image/*" multiple hidden onChange={(e) => addImages(e.target.files)} />
      <input
        type="text"
        placeholder="Optional caption (e.g. #newCategorizationRule ...)"
        value={caption}
        onChange={(e) => setCaption(e.target.value)}
      />
      {error && <p className="cf-error">{error}</p>}
      <div className="cf-photo-panel-actions">
        <button type="button" className="cf-btn cf-btn-small" onClick={onClose}>
          Cancel
        </button>
        <button type="button" className="cf-btn cf-btn-small cf-primary" disabled={busy} onClick={save}>
          {busy ? "Saving…" : "Save"}
        </button>
      </div>
    </div>
  );
}
