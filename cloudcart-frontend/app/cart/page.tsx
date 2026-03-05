"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { getCart, CartItem } from "@/lib/cart";
import CartItemRow from "@/components/CartItemRow";

export default function CartPage() {
  const [userId, setUserId] = useState<string | null | undefined>(undefined);
  const [items, setItems] = useState<CartItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const _uid = localStorage.getItem("cc_user_id") || "userid4"; localStorage.setItem("cc_user_id", _uid); setUserId(_uid);
  }, []);

  const fetchCart = useCallback(async () => {
    if (!userId) return;
    setLoading(true);
    setError(null);
    try {
      const data = await getCart(userId);
      setItems(data);
    } catch {
      setError("Failed to load cart.");
    } finally {
      setLoading(false);
    }
  }, [userId]);

  useEffect(() => {
    if (userId) fetchCart();
  }, [userId, fetchCart]);

  const total = items.reduce((sum, i) => sum + i.price * i.quantity, 0);
  const totalQty = items.reduce((s, i) => s + i.quantity, 0);

  // Still hydrating localStorage
  if (userId === undefined) return null;

  // No user set
  if (userId === null) {
    return (
      <div
        className="flex flex-col items-center justify-center py-24 rounded-3xl border cc-fade-up"
        style={{ borderColor: "var(--cc-border-vivid)", background: "var(--cc-surface-3)" }}
      >
        <div
          className="w-16 h-16 rounded-2xl flex items-center justify-center mb-5"
          style={{ background: "var(--cc-grad-brand)" }}
          aria-hidden="true"
        >
          <svg className="w-8 h-8 text-white" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z" />
          </svg>
        </div>
        <p className="font-bold text-lg mb-1" style={{ color: "var(--cc-text-primary)" }}>
          No session active
        </p>
        <p className="text-sm mb-6" style={{ color: "var(--cc-text-secondary)" }}>
          Please set a user ID on the home page to view your cart.
        </p>
        <Link
          href="/"
          className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-bold text-white transition-all duration-150 hover:scale-105"
          style={{ background: "var(--cc-grad-brand)", boxShadow: "var(--cc-shadow-md)" }}
        >
          Go to products
        </Link>
      </div>
    );
  }

  return (
    <div className="cc-fade-up">
      {/* Page header */}
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-3xl font-black" style={{ color: "var(--cc-text-primary)" }}>
            Your Cart
          </h1>
          {!loading && items.length > 0 && (
            <p className="text-sm mt-0.5" style={{ color: "var(--cc-text-muted)" }}>
              {totalQty} item{totalQty !== 1 ? "s" : ""} in your cart
            </p>
          )}
        </div>
        <Link
          href="/"
          className="inline-flex items-center gap-1.5 text-sm font-semibold transition-colors duration-150"
          style={{ color: "var(--cc-violet)" }}
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2.5" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
          </svg>
          Continue shopping
        </Link>
      </div>

      {/* Loading state */}
      {loading && (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <div
              key={i}
              className="h-20 rounded-2xl cc-skeleton"
              style={{ borderRadius: "1rem" }}
            />
          ))}
        </div>
      )}

      {/* Error state */}
      {error && !loading && (
        <div
          className="flex items-start gap-3 px-5 py-4 rounded-2xl border text-sm"
          style={{ background: "#fef2f2", borderColor: "#fecaca", color: "#991b1b" }}
          role="alert"
        >
          <svg className="w-5 h-5 mt-0.5 shrink-0" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="12" cy="12" r="10" />
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4m0 4h.01" />
          </svg>
          <p>{error}</p>
        </div>
      )}

      {/* Empty state */}
      {!loading && !error && items.length === 0 && (
        <div
          className="flex flex-col items-center justify-center py-24 rounded-3xl border"
          style={{ borderColor: "var(--cc-border-vivid)", background: "var(--cc-surface-3)" }}
        >
          <div
            className="w-20 h-20 rounded-2xl flex items-center justify-center mb-6"
            style={{ background: "var(--cc-grad-brand)" }}
            aria-hidden="true"
          >
            <svg className="w-10 h-10 text-white" fill="none" stroke="currentColor" strokeWidth="1.5" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z" />
            </svg>
          </div>
          <p className="text-xl font-black mb-2" style={{ color: "var(--cc-text-primary)" }}>
            Your cart is empty
          </p>
          <p className="text-sm mb-6" style={{ color: "var(--cc-text-secondary)" }}>
            Looks like you have not added anything yet.
          </p>
          <Link
            href="/"
            className="inline-flex items-center gap-2 px-6 py-3 rounded-xl text-sm font-bold text-white transition-all duration-150 hover:scale-105 active:scale-95"
            style={{ background: "var(--cc-grad-brand)", boxShadow: "var(--cc-shadow-md)" }}
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2.5" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10" />
            </svg>
            Browse products
          </Link>
        </div>
      )}

      {/* Cart items + summary — two-column on desktop */}
      {!loading && !error && items.length > 0 && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">

          {/* Items list */}
          <div className="lg:col-span-2">
            <div
              className="rounded-2xl px-6 py-2"
              style={{
                background: "var(--cc-surface-card)",
                border: "1px solid var(--cc-border)",
                boxShadow: "var(--cc-shadow-sm)",
              }}
            >
              {items.map((item) => (
                <CartItemRow
                  key={item.productId}
                  item={item}
                  onUpdate={fetchCart}
                />
              ))}
            </div>
          </div>

          {/* Order summary sidebar */}
          <div className="lg:col-span-1">
            <div
              className="rounded-2xl overflow-hidden sticky top-24"
              style={{
                border: "1px solid var(--cc-border-vivid)",
                boxShadow: "var(--cc-shadow-md)",
              }}
            >
              {/* Header band */}
              <div
                className="px-6 py-4 border-b"
                style={{
                  background: "var(--cc-surface-3)",
                  borderColor: "var(--cc-border-vivid)",
                }}
              >
                <h2
                  className="text-xs font-bold uppercase tracking-widest"
                  style={{ color: "var(--cc-violet)" }}
                >
                  Order Summary
                </h2>
              </div>

              <div className="px-6 py-5 space-y-3" style={{ background: "var(--cc-surface-card)" }}>
                {/* Item breakdown */}
                {items.map((item) => (
                  <div key={item.productId} className="flex justify-between text-sm">
                    <span
                      className="truncate max-w-[160px]"
                      style={{ color: "var(--cc-text-secondary)" }}
                    >
                      {item.title || item.productId}{" "}
                      <span style={{ color: "var(--cc-text-muted)" }}>×{item.quantity}</span>
                    </span>
                    <span className="font-semibold" style={{ color: "var(--cc-text-primary)" }}>
                      ${(item.price * item.quantity).toFixed(2)}
                    </span>
                  </div>
                ))}

                {/* Divider */}
                <div className="pt-2 border-t" style={{ borderColor: "var(--cc-border)" }}>
                  <div className="flex justify-between items-center">
                    <span className="font-bold text-sm" style={{ color: "var(--cc-text-primary)" }}>
                      Total ({totalQty} items)
                    </span>
                    <span
                      className="text-2xl font-black cc-gradient-text"
                    >
                      ${total.toFixed(2)}
                    </span>
                  </div>
                </div>

                {/* Checkout CTA */}
                <Link
                  href="/checkout"
                  className="mt-2 flex items-center justify-center gap-2 w-full py-3.5 rounded-xl text-sm font-bold text-white transition-all duration-150 hover:scale-[1.02] hover:-translate-y-0.5 active:scale-[0.98]"
                  style={{
                    background: "var(--cc-grad-brand)",
                    boxShadow: "var(--cc-shadow-btn)",
                  }}
                >
                  Proceed to Checkout
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2.5" viewBox="0 0 24 24" aria-hidden="true">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M14 5l7 7m0 0l-7 7m7-7H3" />
                  </svg>
                </Link>

                <p
                  className="text-center text-xs mt-2"
                  style={{ color: "var(--cc-text-muted)" }}
                >
                  Secure checkout powered by CloudCart
                </p>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
