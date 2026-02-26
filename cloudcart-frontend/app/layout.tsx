"use client";

import "./globals.css";
import { useState, useEffect, useCallback } from "react";
import Header from "@/components/Header";
import { CartProvider } from "@/lib/CartContext";

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const [userId, setUserId] = useState<string | null>(null);
  const [inputValue, setInputValue] = useState("");
  const [showPrompt, setShowPrompt] = useState(false);

  useEffect(() => {
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
      <body className="bg-gray-50 min-h-screen">
        <CartProvider userId={userId}>
        <Header userId={userId} onChangeUser={handleChangeUser} />

        {showPrompt && (
          <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
            <div className="bg-white rounded-2xl shadow-xl p-8 max-w-sm w-full mx-4">
              <h2 className="text-xl font-bold text-gray-800 mb-2">
                Welcome to CloudCart
              </h2>
              <p className="text-gray-500 text-sm mb-6">
                Enter a user ID to get started. This is used to identify your
                cart (e.g.&nbsp;<strong>alice</strong>).
              </p>
              <input
                type="text"
                value={inputValue}
                onChange={(e) => setInputValue(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && handleSetUser()}
                placeholder="Your user ID…"
                className="w-full border border-gray-300 rounded-lg px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 mb-4"
                autoFocus
              />
              <button
                onClick={handleSetUser}
                disabled={!inputValue.trim()}
                className="w-full bg-indigo-600 text-white font-semibold py-2.5 rounded-lg hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed transition"
              >
                Continue
              </button>
            </div>
          </div>
        )}

        <main className="max-w-6xl mx-auto px-4 py-8">{children}</main>
        </CartProvider>
      </body>
    </html>
  );
}
