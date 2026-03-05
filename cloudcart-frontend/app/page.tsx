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
    const _uid = localStorage.getItem("cc_user_id") || "userid4"; localStorage.setItem("cc_user_id", _uid); setUserId(_uid);
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
      {/* ── Hero Banner ─────────────────────────────────────────── */}
      {!isSearchMode && (
        <section
          className="relative rounded-3xl overflow-hidden mb-10 cc-fade-up"
          style={{
            background: "var(--cc-grad-brand)",
            minHeight: "220px",
          }}
          aria-label="Hero banner"
        >
          {/* Decorative blobs */}
          <div
            className="absolute top-0 right-0 w-72 h-72 rounded-full opacity-20 -translate-y-1/3 translate-x-1/4"
            style={{ background: "radial-gradient(circle, #f97316, transparent)" }}
            aria-hidden="true"
          />
          <div
            className="absolute bottom-0 left-0 w-52 h-52 rounded-full opacity-15 translate-y-1/3 -translate-x-1/4"
            style={{ background: "radial-gradient(circle, #06b6d4, transparent)" }}
            aria-hidden="true"
          />

          <div className="relative px-8 py-10 sm:px-12 sm:py-14 max-w-2xl">
            <p
              className="text-xs font-bold uppercase tracking-widest mb-3 opacity-80"
              style={{ color: "#a78bfa" }}
            >
              Discover &amp; Shop
            </p>
            <h1 className="text-3xl sm:text-4xl font-black text-white leading-tight mb-3">
              Find the perfect{" "}
              <span style={{ color: "#fb923c" }}>product</span>
              <br />
              at the right price
            </h1>
            <p className="text-sm sm:text-base text-white/70 max-w-md">
              Browse our catalog, add items to your cart, and check out in
              seconds — powered by serverless AWS.
            </p>
          </div>
        </section>
      )}

      {/* ── Toolbar ─────────────────────────────────────────────── */}
      <div className="flex flex-col sm:flex-row sm:items-center gap-4 mb-6">
        <div className="flex-1">
          {isSearchMode ? (
            <h1 className="text-2xl font-black" style={{ color: "var(--cc-text-primary)" }}>
              Search results
            </h1>
          ) : (
            <h2 className="text-xl font-bold" style={{ color: "var(--cc-text-primary)" }}>
              All Products
              {!loading && (
                <span
                  className="ml-2 text-sm font-normal"
                  style={{ color: "var(--cc-text-muted)" }}
                >
                  ({products.length}{nextKey ? "+" : ""} items)
                </span>
              )}
            </h2>
          )}
        </div>

        {/* Search input */}
        <div className="relative w-full sm:w-80">
          {/* Search icon */}
          <span
            className="absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none"
            aria-hidden="true"
          >
            <svg
              className="w-4 h-4"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              viewBox="0 0 24 24"
              style={{ color: "var(--cc-text-muted)" }}
            >
              <circle cx="11" cy="11" r="8" />
              <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-4.35-4.35" />
            </svg>
          </span>

          <input
            type="search"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search products…"
            className="w-full pl-10 pr-10 py-2.5 text-sm rounded-xl border-2 transition-all duration-150"
            style={{
              background: "var(--cc-surface-card)",
              borderColor: searchQuery ? "var(--cc-violet)" : "var(--cc-border)",
              color: "var(--cc-text-primary)",
              boxShadow: searchQuery ? "var(--cc-shadow-glow)" : "var(--cc-shadow-sm)",
            }}
            onFocus={(e) => {
              e.currentTarget.style.borderColor = "var(--cc-violet)";
              e.currentTarget.style.boxShadow = "var(--cc-shadow-glow)";
            }}
            onBlur={(e) => {
              if (!searchQuery) {
                e.currentTarget.style.borderColor = "var(--cc-border)";
                e.currentTarget.style.boxShadow = "var(--cc-shadow-sm)";
              }
            }}
            aria-label="Search products"
          />

          {/* Spinner / clear */}
          <span className="absolute right-3.5 top-1/2 -translate-y-1/2">
            {searching ? (
              <span
                className="w-4 h-4 border-2 border-t-transparent rounded-full cc-spin inline-block"
                style={{ borderColor: "var(--cc-violet)" }}
                aria-label="Searching"
              />
            ) : searchQuery ? (
              <button
                onClick={() => setSearchQuery("")}
                className="text-sm transition-colors duration-150"
                style={{ color: "var(--cc-text-muted)" }}
                aria-label="Clear search"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2.5" viewBox="0 0 24 24" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            ) : null}
          </span>
        </div>
      </div>

      {/* Search results hint */}
      {isSearchMode && (
        <div className="flex items-center justify-between mb-5">
          <p className="text-sm" style={{ color: "var(--cc-text-secondary)" }}>
            <strong style={{ color: "var(--cc-violet)" }}>{displayProducts.length}</strong>
            {" "}result{displayProducts.length !== 1 ? "s" : ""} for{" "}
            <em>&ldquo;{searchQuery}&rdquo;</em>
          </p>
          <button
            onClick={() => setSearchQuery("")}
            className="text-xs font-bold px-3.5 py-1.5 rounded-lg transition-all duration-150 hover:scale-105 hover:-translate-y-0.5"
            style={{
              color: "var(--cc-violet-dark)",
              background: "var(--cc-surface-3)",
              border: "1.5px solid var(--cc-border-vivid)",
              boxShadow: "var(--cc-shadow-sm)",
            }}
          >
            Clear search
          </button>
        </div>
      )}

      {/* ── Loading skeletons ────────────────────────────────────── */}
      {loading && !isSearchMode && (
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-5" aria-busy="true" aria-label="Loading products">
          {Array.from({ length: 8 }).map((_, i) => (
            <div
              key={i}
              className="rounded-2xl overflow-hidden border"
              style={{
                background: "var(--cc-surface-card)",
                borderColor: "var(--cc-border)",
                boxShadow: "var(--cc-shadow-sm)",
              }}
            >
              <div className="h-44 cc-skeleton" />
              <div className="p-4 space-y-2.5">
                <div className="h-3 cc-skeleton rounded-full w-16" />
                <div className="h-4 cc-skeleton rounded-full w-full" />
                <div className="h-4 cc-skeleton rounded-full w-3/4" />
                <div className="h-6 cc-skeleton rounded-full w-20 mt-3" />
                <div className="h-9 cc-skeleton rounded-xl mt-3" />
              </div>
            </div>
          ))}
        </div>
      )}

      {/* ── Error ───────────────────────────────────────────────── */}
      {error && !loading && (
        <div
          className="flex items-start gap-3 px-5 py-4 rounded-2xl border text-sm cc-fade-up"
          style={{
            background: "#fef2f2",
            borderColor: "#fecaca",
            color: "#991b1b",
          }}
          role="alert"
        >
          <svg className="w-5 h-5 mt-0.5 shrink-0" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="12" cy="12" r="10" />
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4m0 4h.01" />
          </svg>
          <p>{error}</p>
        </div>
      )}

      {/* ── Empty state ──────────────────────────────────────────── */}
      {!loading && !error && displayProducts.length === 0 && (
        <div
          className="flex flex-col items-center justify-center py-24 rounded-3xl border cc-fade-up"
          style={{ borderColor: "var(--cc-border-vivid)", background: "var(--cc-surface-3)" }}
        >
          <div
            className="w-16 h-16 rounded-2xl flex items-center justify-center mb-5 text-3xl"
            style={{ background: "var(--cc-grad-brand)" }}
            aria-hidden="true"
          >
            {isSearchMode ? (
              <svg className="w-7 h-7 text-white" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" aria-hidden="true">
                <circle cx="11" cy="11" r="8" />
                <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-4.35-4.35" />
              </svg>
            ) : (
              <svg className="w-7 h-7 text-white" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10" />
              </svg>
            )}
          </div>
          <p className="text-lg font-bold mb-1" style={{ color: "var(--cc-text-primary)" }}>
            {isSearchMode ? "No results found" : "No products found"}
          </p>
          <p className="text-sm" style={{ color: "var(--cc-text-secondary)" }}>
            {isSearchMode
              ? "Try a different search term or browse all products."
              : "Add some products via the API or check that LocalStack is running."}
          </p>
          {isSearchMode && (
            <button
              onClick={() => setSearchQuery("")}
              className="mt-5 px-5 py-2 rounded-xl text-sm font-bold text-white transition-all duration-150 hover:scale-105"
              style={{ background: "var(--cc-grad-brand)" }}
            >
              Browse all products
            </button>
          )}
        </div>
      )}

      {/* ── Product grid ─────────────────────────────────────────── */}
      {!loading && !error && displayProducts.length > 0 && (
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-5 cc-fade-up">
          {displayProducts.map((p) => (
            <ProductCard key={p.productId} product={p} userId={userId} />
          ))}
        </div>
      )}

      {/* ── Load more ────────────────────────────────────────────── */}
      {!isSearchMode && nextKey && !loading && (
        <div className="mt-10 text-center">
          <button
            onClick={loadMore}
            disabled={loadingMore}
            className="inline-flex items-center gap-2 px-8 py-3 rounded-xl text-sm font-bold text-white transition-all duration-150 disabled:opacity-60 hover:scale-105 hover:-translate-y-0.5 active:scale-95"
            style={{
              background: "var(--cc-grad-brand)",
              boxShadow: "var(--cc-shadow-btn)",
            }}
          >
            {loadingMore ? (
              <>
                <span
                  className="w-4 h-4 border-2 border-t-transparent rounded-full cc-spin"
                  style={{ borderColor: "rgba(255,255,255,0.8)" }}
                  aria-hidden="true"
                />
                Loading more…
              </>
            ) : (
              "Load more products"
            )}
          </button>
        </div>
      )}
    </div>
  );
}
