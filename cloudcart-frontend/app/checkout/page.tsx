"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { getCart, clearCart, CartItem } from "@/lib/cart";
import { useCart } from "@/lib/CartContext";
import { placeOrder, getOrder } from "@/lib/api";

const POLLING_TIMEOUT_MSG =
  "Order is being processed — check back shortly.";

interface OrderStatus {
  orderId: string;
  status: "PENDING" | "PAID" | "FAILED" | "TIMEOUT";
}

export default function CheckoutPage() {
  const { refreshCartCount } = useCart();
  const [userId, setUserId] = useState<string | null | undefined>(undefined);
  const [items, setItems] = useState<CartItem[]>([]);
  const [loadingCart, setLoadingCart] = useState(true);
  const [placing, setPlacing] = useState(false);
  const [orderStatus, setOrderStatus] = useState<OrderStatus | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Cancellation flag: set to true on unmount so the polling loop exits
  // promptly and doesn't call setOrderStatus on an unmounted component.
  const cancelledRef = useRef(false);
  useEffect(() => {
    // Reset on every mount — required because React StrictMode unmounts and
    // remounts in development, which would leave the ref permanently true.
    cancelledRef.current = false;
    return () => {
      cancelledRef.current = true;
    };
  }, []);

  useEffect(() => {
    setUserId(localStorage.getItem("cc_user_id"));
  }, []);

  const fetchCart = useCallback(async () => {
    if (!userId) return;
    setLoadingCart(true);
    try {
      const data = await getCart(userId);
      setItems(data);
    } catch {
      setItems([]);
    } finally {
      setLoadingCart(false);
    }
  }, [userId]);

  useEffect(() => {
    if (userId) fetchCart();
    else if (userId === null) setLoadingCart(false);
  }, [userId, fetchCart]);

  const total = items.reduce((sum, i) => sum + i.price * i.quantity, 0);

  async function pollOrderStatus(orderId: string, uid: string) {
    const maxAttempts = 20;
    for (let attempt = 0; attempt < maxAttempts; attempt++) {
      const delay = Math.min(2000 * Math.pow(1.3, attempt), 5000);
      await new Promise((r) => setTimeout(r, delay));
      if (cancelledRef.current) return;
      try {
        const order = await getOrder(orderId, uid);
        if (cancelledRef.current) return;
        setOrderStatus({ orderId, status: order.status as OrderStatus["status"] });
        if (order.status !== "PENDING") {
          // Only clear the cart once payment is confirmed PAID.
          // On FAILED the cart is preserved so the user can retry.
          if (order.status === "PAID") {
            clearCart(uid).then(() => refreshCartCount()).catch(() => refreshCartCount());
          }
          return;
        }
      } catch {
        // keep polling
      }
    }
    setOrderStatus((prev) =>
      prev ? { ...prev, status: "TIMEOUT" } : null
    );
  }

  async function handlePlaceOrder() {
    if (!userId || items.length === 0) return;
    setPlacing(true);
    setError(null);

    try {
      const orderItems = items.map((i) => ({
        productId: i.productId,
        quantity: i.quantity,
        price: i.price,
      }));

      const result = await placeOrder(userId, orderItems);
      setOrderStatus({ orderId: result.orderId, status: "PENDING" });
      await pollOrderStatus(result.orderId, userId);
    } catch {
      setError("Failed to place order. Please try again.");
    } finally {
      setPlacing(false);
    }
  }

  if (userId === undefined || loadingCart) {
    return null;
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

  if (orderStatus) {
    const isPending = orderStatus.status === "PENDING";
    const isPaid = orderStatus.status === "PAID";
    const isTimeout = orderStatus.status === "TIMEOUT";

    return (
      <div className="max-w-lg mx-auto text-center py-16">
        <div
          className={`rounded-2xl p-10 border ${
            isPaid
              ? "bg-green-50 border-green-200"
              : isPending || isTimeout
              ? "bg-yellow-50 border-yellow-200"
              : "bg-red-50 border-red-200"
          }`}
        >
          <div className="text-5xl mb-4">
            {isPaid ? "✓" : isPending || isTimeout ? "⏳" : "✕"}
          </div>
          <h2 className="text-2xl font-bold text-gray-800 mb-2">
            {isPaid
              ? "Payment Successful!"
              : isPending
              ? "Processing Payment…"
              : isTimeout
              ? "Order Submitted"
              : "Payment Failed"}
          </h2>
          <p className="text-gray-500 text-sm mb-1">
            Order ID:{" "}
            <span className="font-mono text-gray-700">{orderStatus.orderId}</span>
          </p>
          <p className="text-gray-500 text-sm mb-6">
            Status:{" "}
            <span
              className={`font-semibold ${
                isPaid
                  ? "text-green-600"
                  : isPending || isTimeout
                  ? "text-yellow-600"
                  : "text-red-600"
              }`}
            >
              {isTimeout ? "PENDING" : orderStatus.status}
            </span>
          </p>

          {isPending && (
            <p className="text-gray-400 text-xs mb-6">
              Payment is being processed. This page updates automatically.
            </p>
          )}
          {isTimeout && (
            <p className="text-gray-400 text-xs mb-6">
              {POLLING_TIMEOUT_MSG}
            </p>
          )}

          <Link
            href="/"
            className="inline-block bg-indigo-600 text-white font-semibold px-6 py-2.5 rounded-lg hover:bg-indigo-700 transition"
          >
            Continue Shopping
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-lg mx-auto">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-800">Checkout</h1>
        <Link href="/cart" className="text-sm text-indigo-600 hover:underline">
          ← Back to cart
        </Link>
      </div>

      {items.length === 0 ? (
        <div className="text-center text-gray-400 py-16">
          <p className="text-lg mb-2">Your cart is empty.</p>
          <Link href="/" className="text-indigo-600 underline">
            Browse products
          </Link>
        </div>
      ) : (
        <>
          <div className="bg-white rounded-xl shadow-sm border border-gray-100 px-6 py-4 mb-4">
            <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide mb-3">
              Order Summary
            </h2>
            <div className="divide-y divide-gray-50">
              {items.map((item) => (
                <div
                  key={item.productId}
                  className="flex justify-between items-center py-2"
                >
                  <div>
                    <p className="text-sm font-medium text-gray-800">
                      {item.title || item.productId}
                    </p>
                    <p className="text-xs text-gray-400">
                      Qty: {item.quantity} × ${item.price.toFixed(2)}
                    </p>
                  </div>
                  <span className="text-sm font-semibold text-gray-700">
                    ${(item.price * item.quantity).toFixed(2)}
                  </span>
                </div>
              ))}
            </div>
          </div>

          <div className="bg-white rounded-xl shadow-sm border border-gray-100 px-6 py-4 mb-6 flex justify-between items-center">
            <span className="font-medium text-gray-700">
              Total ({items.reduce((s, i) => s + i.quantity, 0)} items)
            </span>
            <span className="text-xl font-bold text-gray-900">
              ${total.toFixed(2)}
            </span>
          </div>

          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-3 text-sm mb-4">
              {error}
            </div>
          )}

          <button
            onClick={handlePlaceOrder}
            disabled={placing}
            className="w-full bg-indigo-600 text-white font-semibold py-3 rounded-xl hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed transition text-lg"
          >
            {placing ? "Placing Order…" : "Place Order"}
          </button>
        </>
      )}
    </div>
  );
}
