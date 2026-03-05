"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { listOrders, Order } from "@/lib/api";

/* ── Status badge config ────────────────────────────────────── */
const STATUS_CONFIG: Record<
  string,
  { label: string; bg: string; color: string; border: string; dot: string }
> = {
  PENDING: {
    label: "Pending",
    bg: "#fffbeb",
    color: "#92400e",
    border: "#fde68a",
    dot: "#f59e0b",
  },
  PAID: {
    label: "Paid",
    bg: "#eff6ff",
    color: "#1e40af",
    border: "#bfdbfe",
    dot: "#3b82f6",
  },
  SHIPMENT_CREATED: {
    label: "Shipment Created",
    bg: "#f5f3ff",
    color: "#5b21b6",
    border: "#ddd6fe",
    dot: "#7c3aed",
  },
  SHIPPED: {
    label: "Shipped",
    bg: "#f0fdf4",
    color: "#065f46",
    border: "#bbf7d0",
    dot: "#10b981",
  },
  FAILED: {
    label: "Failed",
    bg: "#fef2f2",
    color: "#991b1b",
    border: "#fecaca",
    dot: "#ef4444",
  },
};

function StatusBadge({ status }: { status: string }) {
  const cfg = STATUS_CONFIG[status] ?? {
    label: status,
    bg: "#f9fafb",
    color: "#374151",
    border: "#e5e7eb",
    dot: "#9ca3af",
  };

  return (
    <span
      className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold"
      style={{ background: cfg.bg, color: cfg.color, border: `1px solid ${cfg.border}` }}
    >
      <span
        className="w-1.5 h-1.5 rounded-full inline-block"
        style={{ background: cfg.dot }}
        aria-hidden="true"
      />
      {cfg.label}
    </span>
  );
}

function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleDateString("en-US", {
      month: "short",
      day: "numeric",
      year: "numeric",
    });
  } catch {
    return iso;
  }
}

function formatTotal(amount: number): string {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
  }).format(amount);
}

/* ── Skeleton card ──────────────────────────────────────────── */
function SkeletonCard() {
  return (
    <div
      className="rounded-2xl px-6 py-5"
      style={{
        background: "var(--cc-surface-card)",
        border: "1px solid var(--cc-border)",
        boxShadow: "var(--cc-shadow-sm)",
      }}
    >
      <div className="flex items-start justify-between mb-4">
        <div className="space-y-2">
          <div className="h-3 cc-skeleton rounded-full w-16" />
          <div className="h-5 cc-skeleton rounded-full w-36" />
        </div>
        <div className="h-6 cc-skeleton rounded-full w-24" />
      </div>
      <div className="flex items-center justify-between">
        <div className="h-4 cc-skeleton rounded-full w-28" />
        <div className="h-6 cc-skeleton rounded-full w-20" />
      </div>
    </div>
  );
}

export default function OrdersPage() {
  const [userId, setUserId] = useState<string | null | undefined>(undefined);
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setUserId(localStorage.getItem("cc_user_id"));
  }, []);

  const fetchOrders = useCallback(async (uid: string) => {
    setLoading(true);
    setError(null);
    try {
      const data = await listOrders(uid);
      const sorted = [...data].sort(
        (a, b) =>
          new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      );
      setOrders(sorted);
    } catch {
      setError("Failed to load orders. Please try again.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (userId) {
      fetchOrders(userId);
    } else if (userId === null) {
      setLoading(false);
    }
  }, [userId, fetchOrders]);

  // Still hydrating localStorage
  if (userId === undefined) return null;

  // No user
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

  return (
    <div className="max-w-2xl mx-auto cc-fade-up">
      {/* Page header */}
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-3xl font-black" style={{ color: "var(--cc-text-primary)" }}>
            My Orders
          </h1>
          {!loading && orders.length > 0 && (
            <p className="text-sm mt-0.5" style={{ color: "var(--cc-text-muted)" }}>
              {orders.length} order{orders.length !== 1 ? "s" : ""} found
            </p>
          )}
        </div>
        <button
          onClick={() => fetchOrders(userId)}
          disabled={loading}
          className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl text-sm font-bold transition-all duration-150 disabled:opacity-50 hover:-translate-y-0.5 hover:scale-[1.02]"
          style={{
            color: "var(--cc-violet-dark)",
            background: "var(--cc-surface-3)",
            border: "1.5px solid var(--cc-border-vivid)",
            boxShadow: "var(--cc-shadow-sm)",
          }}
          aria-label="Refresh orders"
        >
          <svg
            className={`w-3.5 h-3.5 ${loading ? "cc-spin" : ""}`}
            fill="none"
            stroke="currentColor"
            strokeWidth="2.5"
            viewBox="0 0 24 24"
            aria-hidden="true"
          >
            <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
          {loading ? "Refreshing…" : "Refresh"}
        </button>
      </div>

      {/* Error */}
      {error && (
        <div
          className="flex items-start gap-3 px-5 py-4 rounded-2xl border text-sm mb-6"
          style={{ background: "#fef2f2", borderColor: "#fecaca", color: "#991b1b" }}
          role="alert"
        >
          <svg className="w-5 h-5 mt-0.5 shrink-0" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="12" cy="12" r="10" />
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4m0 4h.01" />
          </svg>
          <div>
            <p>{error}</p>
            <button
              onClick={() => fetchOrders(userId)}
              className="mt-1 font-bold underline underline-offset-2 hover:opacity-80"
            >
              Try again
            </button>
          </div>
        </div>
      )}

      {/* Skeleton loading */}
      {loading && (
        <div className="flex flex-col gap-3" aria-busy="true" aria-label="Loading orders">
          <SkeletonCard />
          <SkeletonCard />
          <SkeletonCard />
        </div>
      )}

      {/* Empty state */}
      {!loading && !error && orders.length === 0 && (
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
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
            </svg>
          </div>
          <p className="text-xl font-black mb-2" style={{ color: "var(--cc-text-primary)" }}>
            No orders yet
          </p>
          <p className="text-sm mb-6" style={{ color: "var(--cc-text-secondary)" }}>
            Your placed orders will appear here once you check out.
          </p>
          <Link
            href="/"
            className="inline-flex items-center gap-2 px-6 py-3 rounded-xl text-sm font-bold text-white transition-all hover:scale-105"
            style={{ background: "var(--cc-grad-brand)", boxShadow: "var(--cc-shadow-md)" }}
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2.5" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10" />
            </svg>
            Browse products
          </Link>
        </div>
      )}

      {/* Orders list */}
      {!loading && orders.length > 0 && (
        <>
          <div className="flex flex-col gap-3">
            {orders.map((order, i) => {
              const cfg = STATUS_CONFIG[order.status];
              return (
                <article
                  key={order.orderId}
                  className="rounded-2xl overflow-hidden cc-fade-up"
                  style={{
                    background: "var(--cc-surface-card)",
                    border: "1px solid var(--cc-border)",
                    boxShadow: "var(--cc-shadow-sm)",
                    animationDelay: `${i * 60}ms`,
                  }}
                >
                  {/* Left color accent based on status */}
                  <div
                    className="flex items-stretch"
                  >
                    <div
                      className="w-1 shrink-0"
                      style={{ background: cfg?.dot ?? "#9ca3af" }}
                      aria-hidden="true"
                    />

                    <div className="flex-1 px-5 py-5">
                      {/* Top row: order ID + badge */}
                      <div className="flex items-start justify-between gap-4 mb-3">
                        <div>
                          <p
                            className="text-xs font-bold uppercase tracking-widest mb-0.5"
                            style={{ color: "var(--cc-text-muted)" }}
                          >
                            Order ID
                          </p>
                          <p
                            className="font-mono text-sm font-bold"
                            style={{ color: "var(--cc-text-primary)" }}
                          >
                            {order.orderId.slice(0, 8)}&hellip;
                          </p>
                        </div>
                        <StatusBadge status={order.status} />
                      </div>

                      {/* Bottom row: date + total */}
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-1.5 text-sm" style={{ color: "var(--cc-text-secondary)" }}>
                          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" aria-hidden="true">
                            <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
                            <path strokeLinecap="round" strokeLinejoin="round" d="M16 2v4M8 2v4M3 10h18" />
                          </svg>
                          {formatDate(order.createdAt)}
                        </div>
                        <span className="text-xl font-black cc-gradient-text">
                          {formatTotal(order.totalAmount)}
                        </span>
                      </div>

                      {/* Tracking info (if shipped) */}
                      {order.status === "SHIPPED" && order.trackingId && (
                        <div
                          className="mt-3 pt-3 border-t flex items-center gap-2"
                          style={{ borderColor: "var(--cc-border)" }}
                        >
                          <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" style={{ color: "#10b981" }} aria-hidden="true">
                            <path strokeLinecap="round" strokeLinejoin="round" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10" />
                          </svg>
                          <p className="text-xs font-semibold" style={{ color: "#065f46" }}>
                            Tracking:{" "}
                            <span className="font-mono">{order.trackingId}</span>
                          </p>
                        </div>
                      )}
                    </div>
                  </div>
                </article>
              );
            })}
          </div>

          <div className="mt-10 text-center">
            <Link
              href="/"
              className="inline-flex items-center gap-2 px-6 py-3 rounded-xl text-sm font-bold text-white transition-all hover:scale-105 hover:-translate-y-0.5 active:scale-95"
              style={{ background: "var(--cc-grad-brand)", boxShadow: "var(--cc-shadow-btn)" }}
            >
              Place a new order
            </Link>
          </div>
        </>
      )}
    </div>
  );
}
