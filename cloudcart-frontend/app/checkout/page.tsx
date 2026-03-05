"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { getCart, clearCart, CartItem } from "@/lib/cart";
import { useCart } from "@/lib/CartContext";
import { placeOrder, getOrder } from "@/lib/api";

const POLLING_TIMEOUT_MSG =
  "Order is being processed — check back shortly in My Orders.";

interface OrderStatus {
  orderId: string;
  status: "PENDING" | "PAID" | "SHIPMENT_CREATED" | "FAILED" | "TIMEOUT";
}

/* ── Shared icon helper ─────────────────────────────────────── */
function OrderStatusIcon({ status }: { status: OrderStatus["status"] }) {
  if (status === "SHIPMENT_CREATED") {
    return (
      <svg className="w-10 h-10 text-white" fill="none" stroke="currentColor" strokeWidth="1.5" viewBox="0 0 24 24" aria-hidden="true">
        <path strokeLinecap="round" strokeLinejoin="round" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10" />
      </svg>
    );
  }
  if (status === "PAID") {
    return (
      <svg className="w-10 h-10 text-white" fill="none" stroke="currentColor" strokeWidth="2.5" viewBox="0 0 24 24" aria-hidden="true">
        <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
      </svg>
    );
  }
  if (status === "FAILED") {
    return (
      <svg className="w-10 h-10 text-white" fill="none" stroke="currentColor" strokeWidth="2.5" viewBox="0 0 24 24" aria-hidden="true">
        <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
      </svg>
    );
  }
  // PENDING / TIMEOUT — animated spinner
  return (
    <span
      className="w-10 h-10 border-4 border-t-transparent rounded-full cc-spin inline-block"
      style={{ borderColor: "rgba(255,255,255,0.9)" }}
      aria-label="Processing"
    />
  );
}

export default function CheckoutPage() {
  const { refreshCartCount } = useCart();
  const [userId, setUserId] = useState<string | null | undefined>(undefined);
  const [items, setItems] = useState<CartItem[]>([]);
  const [loadingCart, setLoadingCart] = useState(true);
  const [placing, setPlacing] = useState(false);
  const [orderStatus, setOrderStatus] = useState<OrderStatus | null>(null);
  const [error, setError] = useState<string | null>(null);

  const cancelledRef = useRef(false);
  useEffect(() => {
    cancelledRef.current = false;
    return () => { cancelledRef.current = true; };
  }, []);

  useEffect(() => {
    const _uid = localStorage.getItem("cc_user_id") || "userid4"; localStorage.setItem("cc_user_id", _uid); setUserId(_uid);
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
  const totalQty = items.reduce((s, i) => s + i.quantity, 0);

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
          if (order.status === "PAID" || order.status === "SHIPMENT_CREATED") {
            clearCart(uid).then(() => refreshCartCount()).catch(() => refreshCartCount());
          }
          return;
        }
      } catch {
        // keep polling
      }
    }
    setOrderStatus((prev) => prev ? { ...prev, status: "TIMEOUT" } : null);
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

  if (userId === undefined || loadingCart) return null;

  // ── No user ────────────────────────────────────────────────
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
            <path strokeLinecap="round" strokeLinejoin="round" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
          </svg>
        </div>
        <p className="font-bold text-lg mb-1" style={{ color: "var(--cc-text-primary)" }}>
          No session active
        </p>
        <p className="text-sm mb-6" style={{ color: "var(--cc-text-secondary)" }}>
          Please set a user ID on the home page first.
        </p>
        <Link
          href="/"
          className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-bold text-white transition-all hover:scale-105"
          style={{ background: "var(--cc-grad-brand)", boxShadow: "var(--cc-shadow-md)" }}
        >
          Go to products
        </Link>
      </div>
    );
  }

  // ── Order status screen ─────────────────────────────────────
  if (orderStatus) {
    const isPending = orderStatus.status === "PENDING";
    const isPaid = orderStatus.status === "PAID";
    const isShipmentCreated = orderStatus.status === "SHIPMENT_CREATED";
    const isTimeout = orderStatus.status === "TIMEOUT";
    const isFailed = orderStatus.status === "FAILED";
    const isSuccess = isPaid || isShipmentCreated;

    const statusGrad = isSuccess
      ? "linear-gradient(135deg, #059669, #10b981)"
      : isFailed
      ? "linear-gradient(135deg, #dc2626, #ef4444)"
      : "linear-gradient(135deg, #d97706, #f59e0b)";

    const statusBg = isSuccess ? "#f0fdf4" : isFailed ? "#fef2f2" : "#fffbeb";
    const statusBorder = isSuccess ? "#bbf7d0" : isFailed ? "#fecaca" : "#fde68a";
    const statusTextColor = isSuccess ? "#065f46" : isFailed ? "#991b1b" : "#92400e";

    const statusLabel = isShipmentCreated
      ? "Shipment Created"
      : isPaid
      ? "Payment Successful"
      : isPending
      ? "Processing Payment"
      : isTimeout
      ? "Order Submitted"
      : "Payment Failed";

    const statusDetail = isShipmentCreated
      ? "Your order has been confirmed and a shipment is being prepared."
      : isPaid
      ? "Payment was processed successfully. Your order is confirmed."
      : isPending
      ? "Payment is being processed. This page updates automatically."
      : isTimeout
      ? POLLING_TIMEOUT_MSG
      : "The payment could not be processed. Please try again.";

    return (
      <div className="max-w-md mx-auto py-10 cc-fade-up" role="status" aria-live="polite">
        <div
          className="rounded-3xl overflow-hidden"
          style={{ border: `1px solid ${statusBorder}`, boxShadow: "var(--cc-shadow-lg)" }}
        >
          {/* Colored header */}
          <div
            className="flex flex-col items-center justify-center gap-4 py-10 px-8"
            style={{ background: statusGrad }}
          >
            <div
              className="w-20 h-20 rounded-full flex items-center justify-center"
              style={{ background: "rgba(255,255,255,0.2)" }}
            >
              <OrderStatusIcon status={orderStatus.status} />
            </div>
            <h2 className="text-2xl font-black text-white text-center">
              {statusLabel}
            </h2>
          </div>

          {/* Details */}
          <div style={{ background: statusBg }} className="px-8 py-6 text-center space-y-4">
            <div
              className="inline-flex flex-col items-center gap-1 px-5 py-3 rounded-2xl border"
              style={{ borderColor: statusBorder }}
            >
              <p className="text-xs font-bold uppercase tracking-widest" style={{ color: statusTextColor }}>
                Order ID
              </p>
              <p className="font-mono text-sm font-semibold" style={{ color: "var(--cc-text-primary)" }}>
                {orderStatus.orderId}
              </p>
              <span
                className="mt-1 px-2.5 py-0.5 rounded-full text-xs font-bold"
                style={{ background: statusGrad, color: "#fff" }}
              >
                {isTimeout ? "PENDING" : orderStatus.status}
              </span>
            </div>

            <p className="text-sm" style={{ color: statusTextColor }}>
              {statusDetail}
            </p>

            <div className="pt-2 space-y-2">
              <Link
                href="/"
                className="flex items-center justify-center gap-2 w-full py-3 rounded-xl text-sm font-bold text-white transition-all hover:scale-[1.02] hover:-translate-y-0.5 active:scale-[0.98]"
                style={{ background: "var(--cc-grad-brand)", boxShadow: "var(--cc-shadow-btn)" }}
              >
                Continue Shopping
              </Link>
              <Link
                href="/orders"
                className="flex items-center justify-center gap-2 w-full py-3 rounded-xl text-sm font-bold transition-all hover:scale-[1.01] hover:-translate-y-0.5"
                style={{
                  background: "var(--cc-surface-3)",
                  color: "var(--cc-violet-dark)",
                  border: "1.5px solid var(--cc-border-vivid)",
                  boxShadow: "var(--cc-shadow-sm)",
                }}
              >
                View My Orders
              </Link>
            </div>
          </div>
        </div>
      </div>
    );
  }

  // ── Checkout form ───────────────────────────────────────────
  return (
    <div className="max-w-2xl mx-auto cc-fade-up">
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-3xl font-black" style={{ color: "var(--cc-text-primary)" }}>
            Checkout
          </h1>
          {items.length > 0 && (
            <p className="text-sm mt-0.5" style={{ color: "var(--cc-text-muted)" }}>
              Review your order before placing it
            </p>
          )}
        </div>
        <Link
          href="/cart"
          className="inline-flex items-center gap-1.5 text-sm font-semibold transition-colors duration-150"
          style={{ color: "var(--cc-violet)" }}
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2.5" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
          </svg>
          Back to cart
        </Link>
      </div>

      {/* Empty cart */}
      {items.length === 0 ? (
        <div
          className="flex flex-col items-center justify-center py-24 rounded-3xl border"
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
          <p className="text-lg font-black mb-2" style={{ color: "var(--cc-text-primary)" }}>
            Your cart is empty
          </p>
          <Link
            href="/"
            className="inline-flex items-center gap-2 px-6 py-3 rounded-xl text-sm font-bold text-white mt-2 transition-all hover:scale-105"
            style={{ background: "var(--cc-grad-brand)", boxShadow: "var(--cc-shadow-md)" }}
          >
            Browse products
          </Link>
        </div>
      ) : (
        <div className="space-y-4">
          {/* Order summary card */}
          <div
            className="rounded-2xl overflow-hidden"
            style={{
              background: "var(--cc-surface-card)",
              border: "1px solid var(--cc-border)",
              boxShadow: "var(--cc-shadow-sm)",
            }}
          >
            <div
              className="px-6 py-4 border-b"
              style={{ background: "var(--cc-surface-3)", borderColor: "var(--cc-border-vivid)" }}
            >
              <h2
                className="text-xs font-bold uppercase tracking-widest"
                style={{ color: "var(--cc-violet)" }}
              >
                Order Summary
              </h2>
            </div>
            <div className="px-6 py-4 divide-y" style={{ "--tw-divide-opacity": 1 } as React.CSSProperties}>
              {items.map((item) => (
                <div
                  key={item.productId}
                  className="flex justify-between items-center py-3 first:pt-0 last:pb-0"
                >
                  <div className="min-w-0">
                    <p
                      className="text-sm font-semibold truncate"
                      style={{ color: "var(--cc-text-primary)" }}
                    >
                      {item.title || item.productId}
                    </p>
                    <p className="text-xs mt-0.5" style={{ color: "var(--cc-text-muted)" }}>
                      Qty: {item.quantity} &times; ${item.price.toFixed(2)}
                    </p>
                  </div>
                  <span className="font-bold text-sm ml-4" style={{ color: "var(--cc-text-primary)" }}>
                    ${(item.price * item.quantity).toFixed(2)}
                  </span>
                </div>
              ))}
            </div>
          </div>

          {/* Total bar */}
          <div
            className="flex justify-between items-center px-6 py-4 rounded-2xl"
            style={{
              background: "var(--cc-surface-3)",
              border: "1px solid var(--cc-border-vivid)",
            }}
          >
            <span className="font-bold" style={{ color: "var(--cc-text-primary)" }}>
              Total ({totalQty} item{totalQty !== 1 ? "s" : ""})
            </span>
            <span className="text-2xl font-black cc-gradient-text">
              ${total.toFixed(2)}
            </span>
          </div>

          {/* Error */}
          {error && (
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

          {/* Place order button */}
          <button
            onClick={handlePlaceOrder}
            disabled={placing}
            className="w-full flex items-center justify-center gap-3 py-4 rounded-2xl text-base font-black text-white transition-all duration-150 disabled:opacity-60 disabled:cursor-not-allowed hover:scale-[1.02] hover:-translate-y-0.5 active:scale-[0.98]"
            style={{
              background: "var(--cc-grad-brand)",
              boxShadow: "var(--cc-shadow-btn)",
            }}
          >
            {placing ? (
              <>
                <span
                  className="w-5 h-5 border-2 border-t-transparent rounded-full cc-spin"
                  style={{ borderColor: "rgba(255,255,255,0.8)" }}
                  aria-hidden="true"
                />
                Placing Order…
              </>
            ) : (
              <>
                <svg className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth="2.5" viewBox="0 0 24 24" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                Place Order &mdash; ${total.toFixed(2)}
              </>
            )}
          </button>

          <p className="text-center text-xs" style={{ color: "var(--cc-text-muted)" }}>
            By placing your order you agree to our demo terms. All transactions are simulated.
          </p>
        </div>
      )}
    </div>
  );
}
