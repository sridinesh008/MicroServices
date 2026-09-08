import { NavLink, Route, HashRouter, Routes } from "react-router-dom";
import ChatPage from "./pages/ChatPage";
import DashboardPage from "./pages/DashboardPage";

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  `px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
    isActive ? "bg-accent text-white" : "text-ink-secondary hover:bg-surface"
  }`;

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp("(?:^|; )" + name + "=([^;]*)"));
  return match ? decodeURIComponent(match[1]) : null;
}

async function logout() {
  const token = readCookie("XSRF-TOKEN");
  await fetch("/logout", {
    method: "POST",
    credentials: "include",
    headers: token ? { "X-XSRF-TOKEN": token } : {}
  });
  window.location.href = "/";
}

export default function App() {
  return (
    <HashRouter>
      <div className="min-h-screen flex flex-col">
        <header className="border-b border-border bg-surface">
          <div className="max-w-5xl mx-auto flex items-center justify-between px-4 py-3">
            <span className="font-semibold text-lg">FinTrack</span>
            <nav className="flex gap-1">
              <NavLink to="/" end className={navLinkClass}>
                Chat
              </NavLink>
              <NavLink to="/dashboard" className={navLinkClass}>
                Dashboard
              </NavLink>
            </nav>
            <button onClick={logout} className="text-sm text-ink-muted hover:text-ink-secondary">
              Log out
            </button>
          </div>
        </header>
        <main className="flex-1 max-w-5xl w-full mx-auto px-4 py-6">
          <Routes>
            <Route path="/" element={<ChatPage />} />
            <Route path="/dashboard" element={<DashboardPage />} />
          </Routes>
        </main>
      </div>
    </HashRouter>
  );
}
