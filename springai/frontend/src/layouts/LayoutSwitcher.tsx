import { useState } from "react";
import RailBentoApp from "./rail-bento/RailBentoApp";
import CommandFeedApp from "./command-feed/CommandFeedApp";
import LoadingBar from "./LoadingBar";
import "./switcher.css";

type Layout = "rail-bento" | "command-feed";
const STORAGE_KEY = "fintrack:layout";

function readStoredLayout(): Layout {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored === "command-feed" ? "command-feed" : "rail-bento";
  } catch {
    return "rail-bento";
  }
}

export default function LayoutSwitcher() {
  const [layout, setLayout] = useState<Layout>(readStoredLayout);

  function choose(next: Layout) {
    setLayout(next);
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // private-mode/blocked storage -- selection just won't persist across reloads
    }
  }

  return (
    <>
      <LoadingBar />
      {layout === "rail-bento" ? <RailBentoApp /> : <CommandFeedApp />}
      <div className="ft-switcher" role="tablist" aria-label="Choose FinTrack layout">
        <button
          type="button"
          className={layout === "rail-bento" ? "active" : ""}
          role="tab"
          aria-selected={layout === "rail-bento"}
          onClick={() => choose("rail-bento")}
        >
          Rail Bento
        </button>
        <button
          type="button"
          className={layout === "command-feed" ? "active" : ""}
          role="tab"
          aria-selected={layout === "command-feed"}
          onClick={() => choose("command-feed")}
        >
          Command Feed
        </button>
      </div>
    </>
  );
}
