import { useEffect, useRef, useState } from "react";
import { useChat } from "../../design/useChat";
import type { useDrafts } from "../../design/useDrafts";
import ConfirmCard from "./ConfirmCard";

export default function ChatView({ drafts }: { drafts: ReturnType<typeof useDrafts> }) {
  const { messages, streaming, sendChat } = useChat(drafts.refresh);
  const [text, setText] = useState("");
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, drafts.drafts]);

  function send() {
    const value = text.trim();
    if (!value || streaming) return;
    setText("");
    sendChat(value);
  }

  return (
    <section className="rb-view">
      <div className="rb-page-head">
        <div>
          <h1>Chat</h1>
          <p>Ask about spending or log a purchase.</p>
        </div>
      </div>
      <div className="rb-chat-shell">
        <div className="rb-thread">
          {messages.length === 0 && drafts.drafts.length === 0 && (
            <p className="rb-hint">
              Try: "How much did I spend on dining this month?" or "Log $42.50 at Trader Joe's".
            </p>
          )}
          {messages.map((m, i) => (
            <div key={i} className={`rb-bubble ${m.role === "user" ? "rb-bubble-user" : "rb-bubble-assistant"}`}>
              {m.text || "…"}
            </div>
          ))}
          {drafts.drafts.map((item) => (
            <ConfirmCard key={item.id} item={item} onDone={() => drafts.removeDraft(item.id)} />
          ))}
          <div ref={bottomRef} />
        </div>
        <form
          className="rb-composer"
          onSubmit={(e) => {
            e.preventDefault();
            send();
          }}
        >
          <textarea
            rows={1}
            placeholder="Message FinTrack…"
            value={text}
            disabled={streaming}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                send();
              }
            }}
          />
          <button type="submit" className="rb-send-btn" disabled={streaming} title="Send">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4">
              <path d="M22 2 11 13M22 2l-7 20-4-9-9-4 20-7Z" />
            </svg>
          </button>
        </form>
      </div>
    </section>
  );
}
