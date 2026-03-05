"use client";

import { useState } from "react";
import { CartItem, updateQty, removeItem } from "@/lib/cart";
import { useCart } from "@/lib/CartContext";

interface CartItemRowProps {
  item: CartItem;
  onUpdate: () => void;
}

export default function CartItemRow({ item, onUpdate }: CartItemRowProps) {
  const { refreshCartCount } = useCart();
  const [loading, setLoading] = useState(false);

  async function handleQtyChange(delta: number) {
    const newQty = item.quantity + delta;
    if (newQty < 1) return;
    setLoading(true);
    try {
      await updateQty(item.userId, item.productId, newQty);
      onUpdate();
      refreshCartCount();
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  }

  async function handleRemove() {
    setLoading(true);
    try {
      await removeItem(item.userId, item.productId);
      onUpdate();
      refreshCartCount();
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  }

  const addedDate = new Date(item.addedAt).toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
  });

  const lineTotal = item.price * item.quantity;

  return (
    <div
      className="flex items-center gap-4 py-4 border-b last:border-0 transition-colors duration-150"
      style={{ borderColor: "var(--cc-border)" }}
    >
      {/* Product icon placeholder */}
      <div
        className="hidden sm:flex w-12 h-12 rounded-xl items-center justify-center shrink-0"
        style={{
          background: "linear-gradient(135deg, var(--cc-surface-3), var(--cc-surface-2))",
          border: "1px solid var(--cc-border-vivid)",
        }}
        aria-hidden="true"
      >
        <svg
          className="w-6 h-6"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.5"
          viewBox="0 0 24 24"
          style={{ color: "var(--cc-violet)" }}
        >
          <path strokeLinecap="round" strokeLinejoin="round" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10" />
        </svg>
      </div>

      {/* Title & date */}
      <div className="flex-1 min-w-0">
        <p
          className="font-semibold text-sm leading-snug truncate"
          style={{ color: "var(--cc-text-primary)" }}
        >
          {item.title || item.productId}
        </p>
        <p className="text-xs mt-0.5" style={{ color: "var(--cc-text-muted)" }}>
          Added {addedDate} &middot; ${item.price.toFixed(2)} each
        </p>
      </div>

      {/* Qty stepper */}
      <div
        className="flex items-center rounded-xl overflow-hidden"
        style={{
          border: "1.5px solid var(--cc-border-vivid)",
          background: "var(--cc-surface-2)",
          opacity: loading ? 0.6 : 1,
          transition: "opacity 150ms",
        }}
        aria-label="Quantity stepper"
      >
        <button
          onClick={() => handleQtyChange(-1)}
          disabled={loading || item.quantity <= 1}
          className="w-8 h-8 flex items-center justify-center font-black text-base transition-all duration-100 disabled:opacity-30"
          style={{ color: "var(--cc-violet-dark)" }}
          onMouseEnter={(e) => {
            if (!(e.currentTarget as HTMLButtonElement).disabled) {
              (e.currentTarget as HTMLElement).style.background = "var(--cc-surface-3)";
              (e.currentTarget as HTMLElement).style.color = "var(--cc-violet)";
            }
          }}
          onMouseLeave={(e) => {
            (e.currentTarget as HTMLElement).style.background = "transparent";
            (e.currentTarget as HTMLElement).style.color = "var(--cc-violet-dark)";
          }}
          aria-label="Decrease quantity"
        >
          −
        </button>
        <span
          className="w-9 h-8 flex items-center justify-center text-sm font-black border-x"
          style={{
            borderColor: "var(--cc-border-vivid)",
            background: "var(--cc-surface-3)",
            color: "var(--cc-violet-dark)",
          }}
          aria-live="polite"
          aria-label={`Quantity: ${item.quantity}`}
        >
          {item.quantity}
        </span>
        <button
          onClick={() => handleQtyChange(1)}
          disabled={loading}
          className="w-8 h-8 flex items-center justify-center font-black text-base transition-all duration-100 disabled:opacity-30"
          style={{ color: "var(--cc-violet-dark)" }}
          onMouseEnter={(e) => {
            if (!(e.currentTarget as HTMLButtonElement).disabled) {
              (e.currentTarget as HTMLElement).style.background = "var(--cc-surface-3)";
              (e.currentTarget as HTMLElement).style.color = "var(--cc-violet)";
            }
          }}
          onMouseLeave={(e) => {
            (e.currentTarget as HTMLElement).style.background = "transparent";
            (e.currentTarget as HTMLElement).style.color = "var(--cc-violet-dark)";
          }}
          aria-label="Increase quantity"
        >
          +
        </button>
      </div>

      {/* Line total */}
      <p
        className="font-black text-base w-20 text-right"
        style={{ color: "var(--cc-violet-dark)" }}
      >
        ${lineTotal.toFixed(2)}
      </p>

      {/* Remove button — vivid red by default, not muted grey */}
      <button
        onClick={handleRemove}
        disabled={loading}
        className="p-2 rounded-lg transition-all duration-150 disabled:opacity-40"
        style={{
          color: "#dc2626",
          background: "#fef2f2",
          border: "1px solid #fecaca",
        }}
        onMouseEnter={(e) => {
          if (!(e.currentTarget as HTMLButtonElement).disabled) {
            (e.currentTarget as HTMLElement).style.background = "linear-gradient(135deg, #dc2626, #ef4444)";
            (e.currentTarget as HTMLElement).style.color = "#ffffff";
            (e.currentTarget as HTMLElement).style.borderColor = "#dc2626";
            (e.currentTarget as HTMLElement).style.boxShadow = "0 4px 12px rgba(220,38,38,0.40)";
            (e.currentTarget as HTMLElement).style.transform = "scale(1.05)";
          }
        }}
        onMouseLeave={(e) => {
          (e.currentTarget as HTMLElement).style.background = "#fef2f2";
          (e.currentTarget as HTMLElement).style.color = "#dc2626";
          (e.currentTarget as HTMLElement).style.borderColor = "#fecaca";
          (e.currentTarget as HTMLElement).style.boxShadow = "none";
          (e.currentTarget as HTMLElement).style.transform = "scale(1)";
        }}
        aria-label={`Remove ${item.title || item.productId} from cart`}
        title="Remove item"
      >
        <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
        </svg>
      </button>
    </div>
  );
}
