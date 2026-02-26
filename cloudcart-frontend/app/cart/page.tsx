"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { getCart, CartItem } from "@/lib/cart";
import CartItemRow from "@/components/CartItemRow";

export default function CartPage() {
  // undefined = not yet read from localStorage; null = no user set
  const [userId, setUserId] = useState<string | null | undefined>(undefined);
  const [items, setItems] = useState<CartItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setUserId(localStorage.getItem("cc_user_id"));
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

  if (userId === undefined) {
    return null; // still reading localStorage, render nothing
  }

  if (userId === null) {
    return (
      <div className="text-center text-gray-400 py-16">
        <p>Please set a user ID on the home page first.</p>
        <Link href="/" className="text-indigo-600 underline mt-2 inline-block">
          Go to products
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-800">Your Cart</h1>
        <Link href="/" className="text-sm text-indigo-600 hover:underline">
          ← Continue shopping
        </Link>
      </div>

      {loading && (
        <div className="text-center text-gray-400 py-12">Loading cart…</div>
      )}

      {error && !loading && (
        <div className="bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-3 text-sm">
          {error}
        </div>
      )}

      {!loading && !error && items.length === 0 && (
        <div className="text-center text-gray-400 py-16">
          <p className="text-lg">Your cart is empty.</p>
          <Link
            href="/"
            className="text-indigo-600 underline mt-2 inline-block"
          >
            Browse products
          </Link>
        </div>
      )}

      {!loading && items.length > 0 && (
        <>
          <div className="bg-white rounded-xl shadow-sm border border-gray-100 px-6 py-2">
            {items.map((item) => (
              <CartItemRow
                key={item.productId}
                item={item}
                onUpdate={fetchCart}
              />
            ))}
          </div>

          <div className="mt-4 flex items-center justify-between bg-white rounded-xl shadow-sm border border-gray-100 px-6 py-4">
            <span className="text-gray-600 font-medium">
              Total ({items.reduce((s, i) => s + i.quantity, 0)} items)
            </span>
            <span className="text-xl font-bold text-gray-900">
              ${total.toFixed(2)}
            </span>
          </div>

          <div className="mt-4">
            <Link
              href="/checkout"
              className="block w-full text-center bg-indigo-600 text-white font-semibold py-3 rounded-xl hover:bg-indigo-700 transition text-lg"
            >
              Checkout
            </Link>
          </div>
        </>
      )}
    </div>
  );
}
