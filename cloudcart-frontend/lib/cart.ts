import { cartApi } from "./api";

export type CartItem = {
  userId: string;
  productId: string;
  quantity: number;
  price: number;
  addedAt: string;
};

export async function getCart(userId: string): Promise<CartItem[]> {
  const res = await cartApi.get<CartItem[] | { items: CartItem[] }>(
    `/cart/${userId}`
  );
  const data = res.data;
  if (Array.isArray(data)) return data;
  return (data as { items: CartItem[] }).items ?? [];
}

export async function addToCart(item: {
  userId: string;
  productId: string;
  quantity: number;
  price: number;
}): Promise<void> {
  await cartApi.post("/cart", item);
}

export async function updateQty(
  userId: string,
  productId: string,
  quantity: number
): Promise<void> {
  await cartApi.patch(`/cart/${userId}/${productId}`, { quantity });
}

export async function removeItem(
  userId: string,
  productId: string
): Promise<void> {
  await cartApi.delete(`/cart/${userId}/${productId}`);
}
