export interface OrderItem {
  productId: string;
  quantity: number;
  price: number;
}

export interface Order {
  orderId: string;
  userId: string;
  items: OrderItem[];
  status: 'PENDING' | 'PAID' | 'FAILED' | 'SHIPMENT_CREATED' | 'SHIPPED';
  totalAmount: number;
  createdAt: string;
  trackingId?: string;
}

export interface PlaceOrderRequest {
  userId: string;
  items: OrderItem[];
}

export interface PlaceOrderResponse {
  orderId: string;
  status: string;
  message?: string;
}
