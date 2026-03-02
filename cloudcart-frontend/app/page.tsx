"use client";

import { useEffect, useRef, useState } from "react";
import { listProducts, searchProducts, Product } from "@/lib/products";
import ProductCard from "@/components/ProductCard";

export default function ProductsPage() {
  const [products, setProducts] = useState<Product[]>([]);
  const [nextKey, setNextKey] = useState<string | undefined>(undefined);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [userId, setUserId] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState<Product[] | null>(null);
  const [searching, setSearching] = useState(false);
  const searchCounterRef = useRef(0);

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

  useEffect(() => {
    if (!searchQuery.trim()) {
      setSearchResults(null);
      setSearching(false);
      return;
    }
    const requestId = ++searchCounterRef.current;
    const id = setTimeout(async () => {
      setSearching(true);
      try {
        const { products } = await searchProducts(searchQuery);
        // Discard stale responses — only apply the most recent request's result
        if (requestId === searchCounterRef.current) {
          setSearchResults(products);
        }
      } catch {
        if (requestId === searchCounterRef.current) {
          setSearchResults([]);
        }
      } finally {
        if (requestId === searchCounterRef.current) {
          setSearching(false);
        }
      }
    }, 300);
    return () => clearTimeout(id);
  }, [searchQuery]);

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

  const displayProducts = searchResults !== null ? searchResults : products;
  const isSearchMode = searchResults !== null;

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-800">Products</h1>
        <div className="relative w-72">
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search products…"
            className="w-full border border-gray-300 rounded-full px-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400 pr-10"
          />
          {searching && (
            <span className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 text-xs">
              …
            </span>
          )}
          {searchQuery && !searching && (
            <button
              onClick={() => setSearchQuery("")}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 text-sm"
            >
              ✕
            </button>
          )}
        </div>
      </div>

      {isSearchMode && (
        <p className="text-sm text-gray-500 mb-4">
          {displayProducts.length} result{displayProducts.length !== 1 ? "s" : ""} for &ldquo;{searchQuery}&rdquo;
        </p>
      )}

      {loading && !isSearchMode && (
        <div className="flex items-center justify-center h-48 text-gray-400">
          Loading products…
        </div>
      )}

      {error && !loading && (
        <div className="bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-3 text-sm">
          {error}
        </div>
      )}

      {!loading && !error && displayProducts.length === 0 && (
        <div className="text-center text-gray-400 py-16">
          <p className="text-lg">{isSearchMode ? "No results found." : "No products found."}</p>
          {!isSearchMode && (
            <p className="text-sm mt-1">
              Add some via the API or check that LocalStack is running.
            </p>
          )}
        </div>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-5">
        {displayProducts.map((p) => (
          <ProductCard key={p.productId} product={p} userId={userId} />
        ))}
      </div>

      {!isSearchMode && nextKey && (
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
