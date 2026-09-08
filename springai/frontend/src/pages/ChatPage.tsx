import { useEffect, useRef, useState } from "react";
import { streamChat } from "../api/client";
import { api } from "../api/client";
import ChatComposer from "../components/ChatComposer";
import ExpenseConfirmationModal from "../components/ExpenseConfirmationModal";
import type { ExpenseView } from "../api/types";

interface Message {
  role: "user" | "assistant";
  text: string;
}

export default function ChatPage() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [streaming, setStreaming] = useState(false);
  const [pendingItems, setPendingItems] = useState<ExpenseView[]>([]);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    // Any draft left over from a previous session (e.g. a refresh before confirming) surfaces
    // here automatically -- it lives in the database, not browser state.
    api.drafts().then(setPendingItems).catch(() => undefined);
  }, []);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, pendingItems]);

  async function sendChat(message: string) {
    setMessages((m) => [...m, { role: "user", text: message }, { role: "assistant", text: "" }]);
    setStreaming(true);
    try {
      await streamChat(message, (chunk) => {
        setMessages((m) => {
          const copy = [...m];
          copy[copy.length - 1] = { role: "assistant", text: copy[copy.length - 1].text + chunk };
          return copy;
        });
      });
    } catch (e) {
      setMessages((m) => {
        const copy = [...m];
        copy[copy.length - 1] = { role: "assistant", text: `Error: ${(e as Error).message}` };
        return copy;
      });
    } finally {
      setStreaming(false);
      // A chat-mode message may have driven the assistant to call logTextExpense/editExpense
      // etc. via tool-calling -- re-sync drafts now so the confirmation card shows up right
      // here, instead of only appearing after a tab switch remounts this page.
      api.drafts().then(setPendingItems).catch(() => undefined);
    }
  }

  function onExpenseDraft(items: ExpenseView[]) {
    setPendingItems((prev) => [...prev, ...items]);
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="min-h-[50vh] flex flex-col gap-3">
        {messages.length === 0 && (
          <p className="text-ink-muted text-sm">
            Ask about your spending, or switch to "Log Expense" to record a purchase by text or photo.
          </p>
        )}
        {messages.map((m, i) => (
          <div key={i} className={`max-w-[80%] ${m.role === "user" ? "self-end" : "self-start"}`}>
            <div
              className={`rounded-xl px-3 py-2 text-sm whitespace-pre-wrap ${
                m.role === "user" ? "bg-accent text-white" : "bg-surface border border-border"
              }`}
            >
              {m.text || "…"}
            </div>
          </div>
        ))}
        {pendingItems.length > 0 && (
          <ExpenseConfirmationModal items={pendingItems} onChange={setPendingItems} onClose={() => setPendingItems([])} />
        )}
        <div ref={bottomRef} />
      </div>

      <ChatComposer onChatMessage={sendChat} onExpenseDraft={onExpenseDraft} disabled={streaming} />
    </div>
  );
}
