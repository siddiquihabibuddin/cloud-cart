"use client";

import { createContext, useContext, useState, useCallback, useEffect } from "react";
import { getCart } from "./cart";

type CartContextType = {
  cartCount: number;
  refreshCartCount: () => void;
};

const CartContext = createContext<CartContextType>({
  cartCount: 0,
  refreshCartCount: () => {},
});

export function CartProvider({
  userId,
  children,
}: {
  userId: string | null;
  children: React.ReactNode;
}) {
  const [cartCount, setCartCount] = useState(0);

  const refreshCartCount = useCallback(() => {
    if (!userId) return;
    getCart(userId)
      .then((items) => setCartCount(items.reduce((s, i) => s + i.quantity, 0)))
      .catch(() => setCartCount(0));
  }, [userId]);

  useEffect(() => {
    setCartCount(0);
    refreshCartCount();
  }, [userId, refreshCartCount]);

  return (
    <CartContext.Provider value={{ cartCount, refreshCartCount }}>
      {children}
    </CartContext.Provider>
  );
}

export function useCart() {
  return useContext(CartContext);
}
