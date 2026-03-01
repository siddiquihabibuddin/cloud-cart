"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { listOrders, Order } from "@/lib/api";

interface StatusBadgeProps {
  status: string;
}

function StatusBadge({ status }: StatusBadgeProps) {
  const config: Record<
    string,
    { label: string; className: string }
  > = {
    PENDING: {
      label: "Pending",
      className: "bg-amber-100 text-amber-700 border border-amber-200",
    },
    PAID: {
      label: "Paid",
      className: "bg-blue-100 text-blue-700 border border-blue-200",
    },
    SHIPMENT_CREATED: {
      label: "Shipment Created",
      className: "bg-violet-100 text-violet-700 border border-violet-200",
    },
    SHIPPED: {
      label: "Shipped",
      className: "bg-green-100 text-green-700 border border-green-200",
    },
    FAILED: {
      label: "Failed",
      className: "bg-red-100 text-red-700 border border-red-200",
    },
  };

  const entry = config[status] ?? {
    label: status,
    className: "bg-gray-100 text-gray-600 border border-gray-200",
  };

  return (
    <span
      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${entry.className}`}
    >
      {entry.label}
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

function SkeletonCard() {
  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-100 px-6 py-5 animate-pulse">
      <div className="flex items-start justify-between mb-3">
        <div className="h-4 bg-gray-200 rounded w-32" />
        <div className="h-5 bg-gray-200 rounded-full w-20" />
      </div>
      <div className="flex items-center justify-between">
        <div className="h-4 bg-gray-200 rounded w-24" />
        <div className="h-4 bg-gray-200 rounded w-16" />
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
      // Reverse-chronological: newest first
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
  if (userId === undefined) {
    return null;
  }

  if (userId === null) {
    return (
      <div className="text-center text-gray-400 py-16">
        <p>Please set a user ID on the home page first.</p>
        <Link
          href="/"
          className="text-indigo-600 underline mt-2 inline-block"
        >
          Go to products
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-800">My Orders</h1>
        <button
          onClick={() => fetchOrders(userId)}
          disabled={loading}
          className="text-sm text-indigo-600 hover:text-indigo-800 font-medium disabled:opacity-50 transition"
        >
          {loading ? "Refreshing…" : "Refresh"}
        </button>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-3 text-sm mb-6">
          {error}
          <button
            onClick={() => fetchOrders(userId)}
            className="ml-3 underline font-medium hover:text-red-800"
          >
            Try again
          </button>
        </div>
      )}

      {loading && (
        <div className="flex flex-col gap-3">
          <SkeletonCard />
          <SkeletonCard />
          <SkeletonCard />
        </div>
      )}

      {!loading && !error && orders.length === 0 && (
        <div className="text-center text-gray-400 py-20">
          <div className="text-5xl mb-4">📋</div>
          <p className="text-lg font-medium text-gray-500 mb-1">
            No orders yet
          </p>
          <p className="text-sm mb-6">
            Your placed orders will appear here once you check out.
          </p>
          <Link
            href="/checkout"
            className="inline-block bg-indigo-600 text-white font-semibold px-6 py-2.5 rounded-lg hover:bg-indigo-700 transition"
          >
            Place your first order
          </Link>
        </div>
      )}

      {!loading && orders.length > 0 && (
        <>
          <div className="flex flex-col gap-3">
            {orders.map((order) => (
              <div
                key={order.orderId}
                className="bg-white rounded-xl shadow-sm border border-gray-100 px-6 py-5"
              >
                <div className="flex items-start justify-between gap-4 mb-3">
                  <div>
                    <p className="text-xs text-gray-400 uppercase tracking-wide font-medium mb-0.5">
                      Order ID
                    </p>
                    <p className="font-mono text-sm font-semibold text-gray-700">
                      {order.orderId.slice(0, 8)}&hellip;
                    </p>
                  </div>
                  <StatusBadge status={order.status} />
                </div>

                <div className="flex items-center justify-between text-sm">
                  <span className="text-gray-500">
                    {formatDate(order.createdAt)}
                  </span>
                  <span className="font-bold text-gray-900">
                    {formatTotal(order.totalAmount)}
                  </span>
                </div>

                {order.status === "SHIPPED" && order.trackingId && (
                  <div className="mt-3 pt-3 border-t border-gray-50">
                    <p className="text-xs text-green-700 font-medium">
                      Tracking:{" "}
                      <span className="font-mono">{order.trackingId}</span>
                    </p>
                  </div>
                )}
              </div>
            ))}
          </div>

          <div className="mt-8 text-center">
            <Link
              href="/checkout"
              className="text-sm text-indigo-600 hover:text-indigo-800 font-medium underline underline-offset-2 transition"
            >
              Place a new order
            </Link>
          </div>
        </>
      )}
    </div>
  );
}
