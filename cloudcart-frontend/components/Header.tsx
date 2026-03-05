"use client";

import Link from "next/link";
import { useCart } from "@/lib/CartContext";

interface HeaderProps {
  userId: string | null;
  onChangeUser: () => void;
}

export default function Header({ userId, onChangeUser }: HeaderProps) {
  const { cartCount } = useCart();

  return (
    <header className="sticky top-0 z-40 w-full">
      {/* Main nav — solid deep brand gradient */}
      <div
        style={{
          background: "var(--cc-grad-header)",
          borderBottom: "1px solid rgba(99,102,241,0.35)",
          boxShadow: "0 4px 24px rgba(15,10,46,0.45)",
        }}
      >
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16">

            {/* Logo */}
            <Link
              href="/"
              className="flex items-center gap-2.5 group select-none"
              aria-label="CloudCart home"
            >
              {/* Icon mark */}
              <div
                className="w-9 h-9 rounded-xl flex items-center justify-center text-white text-lg font-black shadow-md group-hover:scale-105 transition-transform duration-150"
                style={{ background: "var(--cc-grad-brand)" }}
                aria-hidden="true"
              >
                C
              </div>
              <span
                className="text-xl font-black tracking-tight text-white"
              >
                CloudCart
              </span>
            </Link>

            {/* Right side controls */}
            <div className="flex items-center gap-2 sm:gap-4">

              {/* User chip */}
              {userId && (
                <div
                  className="hidden sm:flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-medium"
                  style={{
                    background: "rgba(255,255,255,0.12)",
                    border: "1px solid rgba(196,181,253,0.40)",
                    color: "#e0d9ff",
                  }}
                >
                  <span
                    className="w-2 h-2 rounded-full inline-block"
                    style={{ background: "var(--cc-violet-light)" }}
                    aria-hidden="true"
                  />
                  <span>{userId}</span>
                  <button
                    onClick={onChangeUser}
                    className="ml-1 text-xs underline underline-offset-2 opacity-70 hover:opacity-100 transition-opacity"
                    style={{ color: "#c4b5fd" }}
                    aria-label="Change user ID"
                  >
                    Change
                  </button>
                </div>
              )}

              {/* My Orders link */}
              <Link
                href="/orders"
                className="hidden sm:inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-semibold transition-all duration-150"
                style={{ color: "#c4b5fd" }}
                onMouseEnter={(e) => {
                  (e.currentTarget as HTMLElement).style.background = "rgba(255,255,255,0.12)";
                  (e.currentTarget as HTMLElement).style.color = "#ffffff";
                }}
                onMouseLeave={(e) => {
                  (e.currentTarget as HTMLElement).style.background = "transparent";
                  (e.currentTarget as HTMLElement).style.color = "#c4b5fd";
                }}
              >
                <svg
                  className="w-4 h-4"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"
                  />
                </svg>
                Orders
              </Link>

              {/* Cart button — orange gradient for high contrast on dark header */}
              <Link
                href="/cart"
                className="relative flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-bold text-white transition-all duration-150 hover:scale-105 active:scale-95"
                style={{
                  background: "var(--cc-grad-orange)",
                  boxShadow: "var(--cc-shadow-btn-orange)",
                }}
                aria-label={`Cart${cartCount > 0 ? `, ${cartCount} items` : ""}`}
              >
                <svg
                  className="w-4 h-4"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2.5"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z"
                  />
                </svg>
                <span className="hidden sm:inline">Cart</span>
                {cartCount > 0 && (
                  <span
                    className="absolute -top-2 -right-2 min-w-[1.25rem] h-5 px-1 flex items-center justify-center text-xs font-black rounded-full text-white cc-pulse-ring"
                    style={{ background: "#7c3aed", boxShadow: "0 2px 8px rgba(124,58,237,0.6)" }}
                    aria-hidden="true"
                  >
                    {cartCount > 99 ? "99+" : cartCount}
                  </span>
                )}
              </Link>
            </div>
          </div>
        </div>
      </div>
    </header>
  );
}
