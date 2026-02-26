"use client";

import Link from "next/link";
import { useCart } from "@/lib/CartContext";

interface HeaderProps {
  userId: string | null;
  onChangeUser: () => void;
}

export default function Header({ userId, onChangeUser }: HeaderProps) {
  const { cartCount } = useCart();

  return (
    <header className="bg-indigo-700 text-white shadow-md">
      <div className="max-w-6xl mx-auto px-4 py-3 flex items-center justify-between">
        <Link href="/" className="text-xl font-bold tracking-tight hover:opacity-90">
          CloudCart
        </Link>

        <div className="flex items-center gap-4">
          {userId && (
            <span className="text-sm bg-indigo-500 px-3 py-1 rounded-full flex items-center gap-2">
              Shopping as: <strong>{userId}</strong>
              <button
                onClick={onChangeUser}
                className="ml-1 underline text-xs hover:text-indigo-200"
              >
                Change
              </button>
            </span>
          )}

          <Link
            href="/cart"
            className="relative flex items-center gap-1 bg-white text-indigo-700 font-semibold px-4 py-1.5 rounded-full text-sm hover:bg-indigo-50 transition"
          >
            Cart
            {cartCount > 0 && (
              <span className="ml-1 bg-red-500 text-white text-xs font-bold rounded-full w-5 h-5 flex items-center justify-center">
                {cartCount > 99 ? "99+" : cartCount}
              </span>
            )}
          </Link>
        </div>
      </div>
    </header>
  );
}
