import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Order, PlaceOrderRequest, PlaceOrderResponse } from '../models/order.model';

@Injectable({ providedIn: 'root' })
export class OrderService {
  private http = inject(HttpClient);
  private base = '/api-orders';

  placeOrder(request: PlaceOrderRequest): Observable<PlaceOrderResponse> {
    return this.http.post<PlaceOrderResponse>(`${this.base}/orders`, request, {
      headers: {
        'Idempotency-Key': crypto.randomUUID(),
        'Content-Type': 'application/json'
      }
    });
  }

  getOrder(orderId: string): Observable<Order> {
    return this.http.get<Order>(`${this.base}/orders/${orderId}`);
  }

  listOrders(userId: string): Observable<Order[]> {
    const params = new HttpParams().set('userId', userId);
    return this.http.get<Order[]>(`${this.base}/orders`, { params });
  }
}
