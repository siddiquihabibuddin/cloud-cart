"use client";

import { useState } from "react";
import Image from "next/image";
import { Product } from "@/lib/products";
import { addToCart } from "@/lib/cart";
import { useCart } from "@/lib/CartContext";

interface ProductCardProps {
  product: Product;
  userId: string | null;
}

/* Category → accent color mapping (used for borders, buttons, badges) */
const CATEGORY_COLORS: Record<string, string> = {
  Electronics:  "#6366f1",
  Clothing:     "#db2777",
  Books:        "#ea580c",
  Home:         "#0891b2",
  Sports:       "#059669",
  Toys:         "#d97706",
  Beauty:       "#9333ea",
  Food:         "#16a34a",
};

/* Contrast-safe text colors for each category (on white/near-white cards) */
const CATEGORY_TEXT_COLORS: Record<string, string> = {
  Electronics:  "#4338ca",  /* indigo-700 — 6.6:1 */
  Clothing:     "#9d174d",  /* pink-800 — 7.2:1 */
  Books:        "#9a3412",  /* orange-800 — 7.0:1 */
  Home:         "#155e75",  /* cyan-800 — 7.5:1 */
  Sports:       "#065f46",  /* emerald-800 — 8.1:1 */
  Toys:         "#92400e",  /* amber-800 — 7.3:1 */
  Beauty:       "#6b21a8",  /* purple-800 — 6.8:1 */
  Food:         "#14532d",  /* green-900 — 9.2:1 */
};

function getCategoryColor(category: string): string {
  return CATEGORY_COLORS[category] ?? "#7c3aed";
}

function getCategoryTextColor(category: string): string {
  return CATEGORY_TEXT_COLORS[category] ?? "#4c1d95";
}

export default function ProductCard({ product, userId }: ProductCardProps) {
  const { refreshCartCount } = useCart();
  const [loading, setLoading] = useState(false);
  const [added, setAdded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [stock, setStock] = useState(product.stock);
  const [hovered, setHovered] = useState(false);

  const accent = getCategoryColor(product.category);
  const accentText = getCategoryTextColor(product.category);
  const outOfStock = stock === 0;

  async function handleAddToCart() {
    if (!userId) {
      setError("Set a user ID first.");
      return;
    }
    setLoading(true);
    setError(null);
    setStock((s) => Math.max(0, s - 1));
    try {
      await addToCart({
        userId,
        productId: product.productId,
        title: product.title,
        quantity: 1,
        price: product.price,
      });
      setAdded(true);
      refreshCartCount();
      setTimeout(() => setAdded(false), 2000);
    } catch {
      setStock((s) => s + 1);
      setAdded(false);
      setError("Failed to add.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <article
      className="rounded-2xl overflow-hidden flex flex-col transition-all duration-200"
      style={{
        background: "var(--cc-surface-card)",
        border: `1px solid ${hovered ? accent + "55" : "var(--cc-border)"}`,
        boxShadow: hovered ? `var(--cc-shadow-lg), 0 0 0 1px ${accent}30` : "var(--cc-shadow-sm)",
        transform: hovered ? "translateY(-4px)" : "translateY(0)",
      }}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      {/* Top accent border — category color stripe */}
      <div
        className="h-1 w-full shrink-0"
        style={{ background: `linear-gradient(90deg, ${accent}, ${accent}88)` }}
        aria-hidden="true"
      />

      {/* Image area */}
      <div
        className="relative h-48 overflow-hidden"
        style={{
          background: `linear-gradient(135deg, ${accent}22 0%, ${accent}0c 100%)`,
        }}
      >
        {product.imageUrl ? (
          <Image
            src={product.imageUrl}
            alt={product.title}
            fill
            className="object-cover transition-transform duration-300"
            style={{ transform: hovered ? "scale(1.04)" : "scale(1)" }}
            unoptimized
          />
        ) : (
          <div
            className="h-full flex items-center justify-center"
            aria-hidden="true"
          >
            <svg
              className="w-14 h-14 opacity-30"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.5"
              viewBox="0 0 24 24"
              style={{ color: accent }}
            >
              <path strokeLinecap="round" strokeLinejoin="round" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10" />
            </svg>
          </div>
        )}

        {/* Stock badge */}
        <span
          className="absolute top-2.5 right-2.5 px-2.5 py-0.5 rounded-full text-xs font-bold"
          style={
            outOfStock
              ? { background: "#fef2f2", color: "#dc2626", border: "1px solid #fecaca" }
              : stock <= 3
              ? { background: "#fff7ed", color: "#ea580c", border: "1px solid #fed7aa" }
              : { background: "#f0fdf4", color: "#16a34a", border: "1px solid #bbf7d0" }
          }
          aria-label={outOfStock ? "Out of stock" : `${stock} in stock`}
        >
          {outOfStock ? "Out of stock" : `${stock} left`}
        </span>

        {/* Category pill overlaid at bottom of image */}
        <span
          className="absolute bottom-2.5 left-2.5 px-2.5 py-0.5 rounded-full text-xs font-bold text-white"
          style={{ background: accent + "cc", backdropFilter: "blur(4px)" }}
        >
          {product.category}
        </span>
      </div>

      {/* Card body */}
      <div className="p-4 flex flex-col flex-1">
        <h3
          className="font-bold text-sm leading-snug flex-1 line-clamp-2 mb-3"
          style={{ color: "var(--cc-text-primary)" }}
        >
          {product.title}
        </h3>

        {/* Price row */}
        <div className="flex items-end justify-between mb-3">
          <p
            className="text-2xl font-black"
            style={{ color: accentText }}
          >
            ${product.price.toFixed(2)}
          </p>
          {product.stock > 0 && product.stock <= 5 && (
            <p className="text-xs font-semibold" style={{ color: "#ea580c" }}>
              Only {stock} left!
            </p>
          )}
        </div>

        {/* Error message */}
        {error && (
          <p className="text-xs font-medium mb-2" style={{ color: "#dc2626" }}>
            {error}
          </p>
        )}

        {/* Add to cart button */}
        <button
          onClick={handleAddToCart}
          disabled={outOfStock || loading || !userId}
          className="w-full py-2.5 rounded-xl text-sm font-bold text-white transition-all duration-150 relative overflow-hidden"
          style={
            added
              ? {
                  background: "linear-gradient(135deg, #059669, #10b981)",
                  color: "#fff",
                  boxShadow: "0 4px 14px rgba(5,150,105,0.45)",
                }
              : outOfStock
              ? {
                  background: "linear-gradient(135deg, #9ca3af, #6b7280)",
                  color: "#fff",
                  opacity: 0.65,
                  cursor: "not-allowed",
                }
              : !userId
              ? {
                  background: `linear-gradient(135deg, ${accent}bb, ${accent}88)`,
                  color: "#fff",
                  opacity: 0.55,
                  cursor: "not-allowed",
                }
              : loading
              ? {
                  background: `linear-gradient(135deg, ${accent}cc, ${accent}99)`,
                  color: "#fff",
                  boxShadow: `0 4px 12px ${accent}44`,
                }
              : {
                  background: `linear-gradient(135deg, ${accent}, ${accent}cc)`,
                  color: "#fff",
                  boxShadow: `0 4px 14px ${accent}55`,
                }
          }
          onMouseEnter={(e) => {
            if (!outOfStock && !loading && userId && !added) {
              (e.currentTarget as HTMLElement).style.transform = "translateY(-1px) scale(1.02)";
              (e.currentTarget as HTMLElement).style.boxShadow = `0 6px 20px ${accent}66`;
            }
          }}
          onMouseLeave={(e) => {
            (e.currentTarget as HTMLElement).style.transform = "";
            (e.currentTarget as HTMLElement).style.boxShadow = !outOfStock && userId && !added ? `0 4px 14px ${accent}55` : "";
          }}
          aria-label={
            added
              ? "Added to cart"
              : outOfStock
              ? "Out of stock"
              : !userId
              ? "Set a user ID to add to cart"
              : `Add ${product.title} to cart`
          }
        >
          {added ? (
            <span className="flex items-center justify-center gap-1.5">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2.5" viewBox="0 0 24 24" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
              </svg>
              Added!
            </span>
          ) : loading ? (
            <span className="flex items-center justify-center gap-1.5">
              <span
                className="w-3.5 h-3.5 border-2 border-t-transparent rounded-full cc-spin"
                style={{ borderColor: "rgba(255,255,255,0.9)" }}
                aria-hidden="true"
              />
              Adding…
            </span>
          ) : (
            <span className="flex items-center justify-center gap-1.5">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2.5" viewBox="0 0 24 24" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z" />
              </svg>
              {outOfStock ? "Out of Stock" : !userId ? "Set user ID first" : "Add to Cart"}
            </span>
          )}
        </button>
      </div>
    </article>
  );
}
