import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CartItem, AddToCartRequest } from '../models/cart.model';
import { UserService } from './user.service';

@Injectable({ providedIn: 'root' })
export class CartService {
  private http = inject(HttpClient);
  private userService = inject(UserService);
  private base = '/api-cart';

  cartCount = signal<number>(0);
  cartItems = signal<CartItem[]>([]);

  loadCart(): void {
    const userId = this.userService.userId();
    this.http.get<CartItem[]>(`${this.base}/cart/${userId}`).subscribe({
      next: (items) => {
        const list = Array.isArray(items) ? items : [];
        this.cartItems.set(list);
        this.cartCount.set(list.reduce((sum, i) => sum + i.quantity, 0));
      },
      error: () => {
        this.cartItems.set([]);
        this.cartCount.set(0);
      }
    });
  }

  addItem(item: AddToCartRequest): void {
    this.http.post(`${this.base}/cart`, item).subscribe({
      next: () => this.loadCart(),
      error: (err) => console.error('Add to cart failed', err)
    });
  }

  updateQty(productId: string, quantity: number): void {
    const userId = this.userService.userId();
    this.http.patch(`${this.base}/cart/${userId}/${productId}`, { quantity }).subscribe({
      next: () => this.loadCart(),
      error: (err) => console.error('Update qty failed', err)
    });
  }

  removeItem(productId: string): void {
    const userId = this.userService.userId();
    // Optimistic update
    const updated = this.cartItems().filter(i => i.productId !== productId);
    this.cartItems.set(updated);
    this.cartCount.set(updated.reduce((sum, i) => sum + i.quantity, 0));

    this.http.delete(`${this.base}/cart/${userId}/${productId}`).subscribe({
      error: () => this.loadCart() // revert on error
    });
  }

  clearCart(): void {
    const userId = this.userService.userId();
    this.http.delete(`${this.base}/cart/${userId}`).subscribe({
      next: () => {
        this.cartItems.set([]);
        this.cartCount.set(0);
      },
      error: (err) => console.error('Clear cart failed', err)
    });
  }

  refreshCount(): void {
    const userId = this.userService.userId();
    this.http.get<CartItem[]>(`${this.base}/cart/${userId}`).subscribe({
      next: (items) => {
        const list = Array.isArray(items) ? items : [];
        this.cartCount.set(list.reduce((sum, i) => sum + i.quantity, 0));
      },
      error: () => this.cartCount.set(0)
    });
  }
}
