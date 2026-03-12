export interface Product {
  productId: string;
  title: string;
  description?: string;
  price: number;
  stock: number;
  category?: string;
  imageUrl?: string;
}

export interface ProductListResponse {
  products: Product[];
  nextKey?: string;
}

export interface SearchResponse {
  results: Product[];
  total: number;
}
