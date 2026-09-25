"use client";

import { useRef, useState, useEffect } from "react";
import { sendChatMessage, ChatApiMessage } from "@/lib/api";

interface DisplayMessage {
  role: "user" | "assistant";
  content: string;
}

function toDisplayMessages(history: ChatApiMessage[]): DisplayMessage[] {
  return history
    .filter(
      (m): m is ChatApiMessage & { role: "user" | "assistant"; content: string } =>
        (m.role === "user" || m.role === "assistant") &&
        typeof m.content === "string" &&
        m.content.length > 0
    )
    .map((m) => ({ role: m.role as "user" | "assistant", content: m.content as string }));
}

export default function ChatWidget({ userId }: { userId: string | null }) {
  const [open, setOpen] = useState(false);
  const [history, setHistory] = useState<ChatApiMessage[]>([]);
  const [input, setInput] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const messages = toDisplayMessages(history);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages.length, pending, open]);

  if (!userId) return null;

  async function handleSend() {
    const text = input.trim();
    if (!text || pending) return;

    setInput("");
    setError(null);
    setPending(true);
    const optimisticHistory: ChatApiMessage[] = [...history, { role: "user", content: text }];
    setHistory(optimisticHistory);

    try {
      const res = await sendChatMessage(text, history);
      setHistory(res.history);
    } catch {
      setError("Sorry, the assistant is unavailable right now. Please try again.");
      setHistory(history);
      setInput(text);
    } finally {
      setPending(false);
    }
  }

  return (
    <>
      <button
        onClick={() => setOpen((v) => !v)}
        className="fixed bottom-6 right-6 z-40 w-14 h-14 rounded-full flex items-center justify-center text-white cc-scale-in"
        style={{ background: "var(--cc-grad-brand)", boxShadow: "var(--cc-shadow-lg)" }}
        aria-label={open ? "Close shopping assistant" : "Open shopping assistant"}
      >
        {open ? (
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M18 6 6 18M6 6l12 12" strokeLinecap="round" />
          </svg>
        ) : (
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path
              d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        )}
      </button>

      {open && (
        <div
          className="fixed bottom-24 right-6 z-40 w-[22rem] max-w-[calc(100vw-2rem)] h-[32rem] max-h-[calc(100vh-8rem)] rounded-2xl overflow-hidden flex flex-col cc-scale-in"
          style={{ background: "var(--cc-surface-card)", boxShadow: "var(--cc-shadow-lg)", border: "1px solid var(--cc-border)" }}
          role="dialog"
          aria-label="Shopping assistant chat"
        >
          <div className="px-4 py-3 flex-shrink-0" style={{ background: "var(--cc-grad-brand)" }}>
            <p className="text-sm font-bold text-white">CloudCart Assistant</p>
            <p className="text-xs text-white/80">Ask me to find products, manage your cart, or check orders</p>
          </div>

          <div className="flex-1 overflow-y-auto px-4 py-3 space-y-3">
            {messages.length === 0 && (
              <p className="text-sm" style={{ color: "var(--cc-text-muted)" }}>
                Try: &ldquo;find me a laptop under $800&rdquo;
              </p>
            )}
            {messages.map((m, i) => (
              <div key={i} className={`flex ${m.role === "user" ? "justify-end" : "justify-start"}`}>
                <div
                  className="max-w-[85%] px-3 py-2 rounded-xl text-sm whitespace-pre-wrap"
                  style={
                    m.role === "user"
                      ? { background: "var(--cc-grad-brand)", color: "#ffffff" }
                      : { background: "var(--cc-surface-2)", color: "var(--cc-text-primary)" }
                  }
                >
                  {m.content}
                </div>
              </div>
            ))}
            {pending && (
              <div className="flex justify-start">
                <div
                  className="px-3 py-2 rounded-xl text-sm flex items-center gap-2"
                  style={{ background: "var(--cc-surface-2)", color: "var(--cc-text-muted)" }}
                >
                  <span
                    className="inline-block w-3 h-3 rounded-full border-2 cc-spin"
                    style={{ borderColor: "var(--cc-violet)", borderTopColor: "transparent" }}
                    aria-hidden="true"
                  />
                  Thinking…
                </div>
              </div>
            )}
            {error && (
              <div
                className="px-3 py-2 rounded-xl text-sm"
                style={{ background: "#fee2e2", color: "#991b1b" }}
                role="alert"
              >
                {error}
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          <div className="p-3 flex-shrink-0 flex gap-2" style={{ borderTop: "1px solid var(--cc-border)" }}>
            <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && handleSend()}
              placeholder="Ask the shopping assistant…"
              disabled={pending}
              className="flex-1 px-3 py-2 text-sm rounded-xl border-2 transition-colors duration-150"
              style={{ borderColor: "var(--cc-border)", outline: "none", color: "var(--cc-text-primary)" }}
              aria-label="Chat message"
            />
            <button
              onClick={handleSend}
              disabled={pending || !input.trim()}
              className="cc-btn-primary"
            >
              Send
            </button>
          </div>
        </div>
      )}
    </>
  );
}
