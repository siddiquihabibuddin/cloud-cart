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

export default function ProductCard({ product, userId }: ProductCardProps) {
  const { refreshCartCount } = useCart();
  const [loading, setLoading] = useState(false);
  const [added, setAdded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [stock, setStock] = useState(product.stock);

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

  const outOfStock = stock === 0;

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden flex flex-col hover:shadow-md transition">
      <div className="relative h-48 bg-gray-50">
        {product.imageUrl ? (
          <Image
            src={product.imageUrl}
            alt={product.title}
            fill
            className="object-cover"
            unoptimized
          />
        ) : (
          <div className="h-full flex items-center justify-center text-gray-300 text-4xl">
            🛍️
          </div>
        )}
        {outOfStock && (
          <span className="absolute top-2 right-2 bg-red-100 text-red-700 text-xs font-semibold px-2 py-0.5 rounded-full">
            Out of stock
          </span>
        )}
        {!outOfStock && (
          <span className="absolute top-2 right-2 bg-green-100 text-green-700 text-xs font-semibold px-2 py-0.5 rounded-full">
            {stock} left
          </span>
        )}
      </div>

      <div className="p-4 flex flex-col flex-1">
        <p className="text-xs text-indigo-500 font-medium uppercase tracking-wide mb-1">
          {product.category}
        </p>
        <h3 className="font-semibold text-gray-800 text-sm flex-1 line-clamp-2">
          {product.title}
        </h3>
        <p className="text-lg font-bold text-gray-900 mt-2">
          ${product.price.toFixed(2)}
        </p>

        {error && <p className="text-red-500 text-xs mt-1">{error}</p>}

        <button
          onClick={handleAddToCart}
          disabled={outOfStock || loading || !userId}
          className={`mt-3 w-full py-2 rounded-lg text-sm font-semibold transition ${
            added
              ? "bg-green-500 text-white"
              : outOfStock || !userId
              ? "bg-gray-100 text-gray-400 cursor-not-allowed"
              : "bg-indigo-600 text-white hover:bg-indigo-700"
          }`}
        >
          {added ? "Added!" : loading ? "Adding…" : "Add to Cart"}
        </button>
      </div>
    </div>
  );
}
