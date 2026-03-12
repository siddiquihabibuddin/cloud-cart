export interface Product {
  productID: string;
  title: string;
  description?: string;
  price: number;
  stock: number;
  category?: string;
}

export interface ProductListResponse {
  items: Product[];
  lastKey?: string;
}

export interface SearchResponse {
  results: Product[];
  total: number;
}
