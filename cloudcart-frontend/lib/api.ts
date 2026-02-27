import axios from "axios";

export const productsApi = axios.create({
  baseURL: process.env.NEXT_PUBLIC_PRODUCTS_API,
});

export const cartApi = axios.create({
  baseURL: process.env.NEXT_PUBLIC_CART_API,
});

export const ordersApi = axios.create({
  baseURL: process.env.NEXT_PUBLIC_ORDER_API,
});

export async function placeOrder(
  userId: string,
  items: Array<{ productId: string; quantity: number; price: number }>
): Promise<{ orderId: string }> {
  const res = await ordersApi.post("/orders", { userId, items });
  return res.data;
}

export async function getOrder(
  orderId: string,
  userId: string
): Promise<{ orderId: string; userId: string; status: string; totalAmount: number; createdAt: string }> {
  const res = await ordersApi.get(`/orders/${orderId}`, { params: { userId } });
  return res.data;
}
