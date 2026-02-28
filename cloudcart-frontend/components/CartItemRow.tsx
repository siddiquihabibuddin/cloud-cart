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

  return (
    <div className="flex items-center justify-between py-4 border-b border-gray-100 last:border-0">
      <div className="flex-1 min-w-0">
        <p className="font-medium text-gray-800 truncate">{item.title || item.productId}</p>
        <p className="text-xs text-gray-400 mt-0.5">Added {addedDate}</p>
      </div>

      <div className="flex items-center gap-3 ml-4">
        <p className="text-sm text-gray-500 w-20 text-right">
          ${item.price.toFixed(2)} ea
        </p>

        <div className="flex items-center gap-1 border border-gray-200 rounded-lg overflow-hidden">
          <button
            onClick={() => handleQtyChange(-1)}
            disabled={loading || item.quantity <= 1}
            className="px-2.5 py-1.5 text-gray-600 hover:bg-gray-100 disabled:opacity-40 text-sm font-bold"
          >
            −
          </button>
          <span className="px-3 py-1.5 text-sm font-semibold bg-gray-50 min-w-[2rem] text-center">
            {item.quantity}
          </span>
          <button
            onClick={() => handleQtyChange(1)}
            disabled={loading}
            className="px-2.5 py-1.5 text-gray-600 hover:bg-gray-100 disabled:opacity-40 text-sm font-bold"
          >
            +
          </button>
        </div>

        <p className="font-bold text-gray-900 w-20 text-right">
          ${(item.price * item.quantity).toFixed(2)}
        </p>

        <button
          onClick={handleRemove}
          disabled={loading}
          className="text-red-400 hover:text-red-600 disabled:opacity-40 transition p-1"
          title="Remove item"
        >
          🗑️
        </button>
      </div>
    </div>
  );
}
