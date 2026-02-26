import { productsApi } from "./api";

export type Product = {
  productId: string;
  title: string;
  price: number;
  stock: number;
  category: string;
  imageUrl: string;
};

type ListResponse =
  | { products: Product[]; nextKey?: string }
  | { items: Product[]; nextKey?: string }
  | Product[];

export async function listProducts(
  limit = 12,
  lastKey?: string
): Promise<{ products: Product[]; nextKey?: string }> {
  const params: Record<string, string | number> = { limit };
  if (lastKey) params.lastKey = lastKey;
  const res = await productsApi.get<ListResponse>("/products", { params });
  const data = res.data;
  if (Array.isArray(data)) return { products: data };
  if ("products" in data) return { products: data.products, nextKey: data.nextKey };
  if ("items" in data) return { products: data.items, nextKey: data.nextKey };
  return { products: [] };
}

export async function getProduct(id: string): Promise<Product> {
  const res = await productsApi.get<Product>(`/products/${id}`);
  return res.data;
}
