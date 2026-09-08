import { logout } from "../../design/auth";

type View = "chat" | "dashboard";

export default function RailNav({ view, onChange }: { view: View; onChange: (v: View) => void }) {
  return (
    <nav className="rb-rail">
      <div className="rb-mark">F</div>
      <button
        type="button"
        className="rb-rail-btn"
        aria-current={view === "chat" ? "true" : "false"}
        title="Chat"
        onClick={() => onChange("chat")}
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M21 11.5a8.4 8.4 0 0 1-8.9 8.4 8.9 8.9 0 0 1-3.5-.6L3 21l1.7-5.1A8.4 8.4 0 1 1 21 11.5Z" />
        </svg>
      </button>
      <button
        type="button"
        className="rb-rail-btn"
        aria-current={view === "dashboard" ? "true" : "false"}
        title="Dashboard"
        onClick={() => onChange("dashboard")}
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <rect x="3" y="3" width="7" height="9" rx="2" />
          <rect x="14" y="3" width="7" height="5" rx="2" />
          <rect x="14" y="12" width="7" height="9" rx="2" />
          <rect x="3" y="16" width="7" height="5" rx="2" />
        </svg>
      </button>
      <div className="rb-rail-spacer" />
      <button type="button" className="rb-avatar" title="Log out" onClick={logout}>
        Out
      </button>
    </nav>
  );
}
