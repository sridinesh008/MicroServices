import { useState } from "react";
import "./railBento.css";
import RailNav from "./RailNav";
import ChatView from "./ChatView";
import DashboardView from "./DashboardView";
import QuickLogModal from "./QuickLogModal";
import { useDrafts } from "../../design/useDrafts";

type View = "chat" | "dashboard";

export default function RailBentoApp() {
  const [view, setView] = useState<View>("chat");
  const [modalOpen, setModalOpen] = useState(false);
  const drafts = useDrafts();

  return (
    <div className="skin-rail-bento">
      <RailNav view={view} onChange={setView} />
      <main className="rb-main">
        {view === "chat" ? <ChatView drafts={drafts} /> : <DashboardView />}
      </main>
      <button type="button" className="rb-fab" title="Quick log" onClick={() => setModalOpen(true)}>
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4">
          <path d="M12 5v14M5 12h14" />
        </svg>
      </button>
      {modalOpen && (
        <QuickLogModal
          onClose={() => setModalOpen(false)}
          onDraft={(items) => {
            drafts.addDrafts(items);
            setView("chat");
          }}
        />
      )}
    </div>
  );
}
