import { useState } from "react";
import { streamChat } from "../api/client";

export interface ChatMessage {
  role: "user" | "assistant";
  text: string;
}

/**
 * Streaming chat turns. `onTurnComplete` fires once per turn (success or error) -- callers use
 * it to re-sync drafts, since a chat-mode message may have driven a tool call that created one.
 */
export function useChat(onTurnComplete?: () => void) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [streaming, setStreaming] = useState(false);

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
      onTurnComplete?.();
    }
  }

  return { messages, streaming, sendChat };
}
