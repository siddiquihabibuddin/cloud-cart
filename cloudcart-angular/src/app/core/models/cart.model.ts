export interface CartItem {
  userId: string;
  productId: string;
  productName: string;
  price: number;
  quantity: number;
}

export interface AddToCartRequest {
  userId: string;
  productId: string;
  productName: string;
  price: number;
  quantity: number;
}
