"use client";

import "./globals.css";
import { useState, useEffect, useCallback } from "react";
import Header from "@/components/Header";
import Footer from "@/components/Footer";
import { CartProvider } from "@/lib/CartContext";

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const [userId, setUserId] = useState<string | null>(null);
  const [inputValue, setInputValue] = useState("");
  const [showPrompt, setShowPrompt] = useState(false);
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
    const stored = localStorage.getItem("cc_user_id");
    if (stored) {
      setUserId(stored);
    } else {
      setShowPrompt(true);
    }
  }, []);

  function handleSetUser() {
    const trimmed = inputValue.trim();
    if (!trimmed) return;
    localStorage.setItem("cc_user_id", trimmed);
    setUserId(trimmed);
    setShowPrompt(false);
    setInputValue("");
  }

  const handleChangeUser = useCallback(() => {
    localStorage.removeItem("cc_user_id");
    setUserId(null);
    setInputValue("");
    setShowPrompt(true);
  }, []);

  return (
    <html lang="en">
      <body>
        <CartProvider userId={userId}>
          <Header userId={userId} onChangeUser={handleChangeUser} />

          {/* Welcome modal */}
          {mounted && showPrompt && (
            <div
              className="fixed inset-0 flex items-center justify-center z-50 p-4"
              style={{ background: "rgba(15,10,30,0.7)", backdropFilter: "blur(8px)" }}
              role="dialog"
              aria-modal="true"
              aria-labelledby="welcome-heading"
            >
              <div
                className="w-full max-w-sm rounded-3xl overflow-hidden cc-scale-in"
                style={{ boxShadow: "var(--cc-shadow-lg)" }}
              >
                {/* Gradient header band */}
                <div
                  className="h-2 w-full"
                  style={{ background: "var(--cc-grad-brand)" }}
                  aria-hidden="true"
                />

                <div className="p-8" style={{ background: "var(--cc-surface-card)" }}>
                  {/* Icon */}
                  <div
                    className="w-14 h-14 rounded-2xl flex items-center justify-center text-white text-2xl font-black mb-5 shadow-lg"
                    style={{ background: "var(--cc-grad-brand)" }}
                    aria-hidden="true"
                  >
                    C
                  </div>

                  <h2
                    id="welcome-heading"
                    className="text-2xl font-black mb-2"
                    style={{ color: "var(--cc-text-primary)" }}
                  >
                    Welcome to{" "}
                    <span className="cc-gradient-text">CloudCart</span>
                  </h2>
                  <p className="text-sm mb-6" style={{ color: "var(--cc-text-secondary)" }}>
                    Enter a user ID to get started — this identifies your
                    shopping session (e.g.{" "}
                    <strong style={{ color: "var(--cc-violet)" }}>alice</strong>).
                  </p>

                  <div className="space-y-3">
                    <input
                      type="text"
                      value={inputValue}
                      onChange={(e) => setInputValue(e.target.value)}
                      onKeyDown={(e) => e.key === "Enter" && handleSetUser()}
                      placeholder="Your user ID…"
                      className="w-full px-4 py-3 text-sm rounded-xl border-2 transition-colors duration-150"
                      style={{
                        borderColor: inputValue ? "var(--cc-violet)" : "var(--cc-border)",
                        outline: "none",
                        color: "var(--cc-text-primary)",
                      }}
                      onFocus={(e) => {
                        e.currentTarget.style.borderColor = "var(--cc-violet)";
                        e.currentTarget.style.boxShadow = "var(--cc-shadow-glow)";
                      }}
                      onBlur={(e) => {
                        if (!inputValue) {
                          e.currentTarget.style.borderColor = "var(--cc-border)";
                        }
                        e.currentTarget.style.boxShadow = "none";
                      }}
                      autoFocus
                      aria-label="User ID"
                    />
                    <button
                      onClick={handleSetUser}
                      disabled={!inputValue.trim()}
                      className="w-full py-3 rounded-xl text-sm font-bold text-white transition-all duration-150 disabled:opacity-40 disabled:cursor-not-allowed hover:scale-[1.02] hover:-translate-y-0.5 active:scale-[0.98]"
                      style={{
                        background: inputValue.trim()
                          ? "var(--cc-grad-brand)"
                          : "linear-gradient(135deg, #9ca3af, #6b7280)",
                        boxShadow: inputValue.trim() ? "var(--cc-shadow-btn)" : "none",
                      }}
                    >
                      Start Shopping
                    </button>
                  </div>
                </div>
              </div>
            </div>
          )}

          <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
            {children}
          </main>

          <Footer />
        </CartProvider>
      </body>
    </html>
  );
}
