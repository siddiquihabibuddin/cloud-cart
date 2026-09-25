import axios, { AxiosInstance } from "axios";
import { clearSession, getToken } from "./session";

function attachAuth(instance: AxiosInstance): AxiosInstance {
  instance.interceptors.request.use((config) => {
    const token = getToken();
    if (token) {
      config.headers.set("Authorization", `Bearer ${token}`);
    }
    return config;
  });

  instance.interceptors.response.use(
    (res) => res,
    (error) => {
      if (error.response?.status === 401) {
        clearSession();
        if (typeof window !== "undefined") {
          window.location.href = "/login";
        }
      }
      return Promise.reject(error);
    }
  );

  return instance;
}

export const productsApi = attachAuth(axios.create({
  baseURL: process.env.NEXT_PUBLIC_PRODUCTS_API,
}));

export const cartApi = attachAuth(axios.create({
  baseURL: process.env.NEXT_PUBLIC_CART_API,
}));

export const ordersApi = attachAuth(axios.create({
  baseURL: process.env.NEXT_PUBLIC_ORDER_API,
  headers: { "x-api-key": "cloudcart-dev-key-2024" },
}));

export const searchApi = axios.create({
  baseURL: process.env.NEXT_PUBLIC_SEARCH_API,
});

export const agentApi = attachAuth(axios.create({
  baseURL: process.env.NEXT_PUBLIC_AGENT_API,
}));

export interface ChatApiMessage {
  role: string;
  content: string | null;
  [key: string]: unknown;
}

export async function sendChatMessage(
  message: string,
  history: ChatApiMessage[]
): Promise<{ reply: string; history: ChatApiMessage[] }> {
  const { data } = await agentApi.post("/chat", { message, history });
  return data;
}

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

export interface Order {
  orderId: string;
  userId: string;
  status: string;
  totalAmount: number;
  createdAt: string;
  itemsJson?: string;
  trackingId?: string;
  shippedAt?: string;
}

export async function listOrders(userId: string): Promise<Order[]> {
  const { data } = await ordersApi.get(`/orders?userId=${encodeURIComponent(userId)}`);
  return data as Order[];
}
