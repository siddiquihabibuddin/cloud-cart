"use client";

import { useEffect, useState } from "react";
import { listProducts, Product } from "@/lib/products";
import ProductCard from "@/components/ProductCard";

export default function ProductsPage() {
  const [products, setProducts] = useState<Product[]>([]);
  const [nextKey, setNextKey] = useState<string | undefined>(undefined);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [userId, setUserId] = useState<string | null>(null);

  useEffect(() => {
    setUserId(localStorage.getItem("cc_user_id"));
  }, []);

  useEffect(() => {
    setLoading(true);
    setError(null);
    listProducts(12)
      .then(({ products, nextKey }) => {
        setProducts(products);
        setNextKey(nextKey);
      })
      .catch(() => setError("Failed to load products. Is LocalStack running?"))
      .finally(() => setLoading(false));
  }, []);

  async function loadMore() {
    if (!nextKey) return;
    setLoadingMore(true);
    try {
      const { products: more, nextKey: newKey } = await listProducts(12, nextKey);
      setProducts((prev) => [...prev, ...more]);
      setNextKey(newKey);
    } catch {
      setError("Failed to load more products.");
    } finally {
      setLoadingMore(false);
    }
  }

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-800 mb-6">Products</h1>

      {loading && (
        <div className="flex items-center justify-center h-48 text-gray-400">
          Loading products…
        </div>
      )}

      {error && !loading && (
        <div className="bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-3 text-sm">
          {error}
        </div>
      )}

      {!loading && !error && products.length === 0 && (
        <div className="text-center text-gray-400 py-16">
          <p className="text-lg">No products found.</p>
          <p className="text-sm mt-1">
            Add some via the API or check that LocalStack is running.
          </p>
        </div>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-5">
        {products.map((p) => (
          <ProductCard key={p.productId} product={p} userId={userId} />
        ))}
      </div>

      {nextKey && (
        <div className="mt-8 text-center">
          <button
            onClick={loadMore}
            disabled={loadingMore}
            className="bg-indigo-600 text-white font-semibold px-8 py-2.5 rounded-full hover:bg-indigo-700 disabled:opacity-50 transition"
          >
            {loadingMore ? "Loading…" : "Load more"}
          </button>
        </div>
      )}
    </div>
  );
}
