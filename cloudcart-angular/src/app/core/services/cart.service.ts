import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject } from 'rxjs';
import { CartItem, AddToCartRequest } from '../models/cart.model';
import { UserService } from './user.service';

@Injectable({ providedIn: 'root' })
export class CartService {
  private http = inject(HttpClient);
  private userService = inject(UserService);
  private base = '/api-cart';

  private cartCount$ = new BehaviorSubject<number>(0);
  private cartItems$ = new BehaviorSubject<CartItem[]>([]);

  get cartCount(): BehaviorSubject<number> {
    return this.cartCount$;
  }

  get cartItems(): BehaviorSubject<CartItem[]> {
    return this.cartItems$;
  }

  getCartCount(): number {
    return this.cartCount$.getValue();
  }

  getCartItems(): CartItem[] {
    return this.cartItems$.getValue();
  }

  loadCart(): void {
    const userId = this.userService.getUserId();
    this.http.get<CartItem[]>(`${this.base}/cart/${userId}`).subscribe({
      next: (items) => {
        const list = Array.isArray(items) ? items : [];
        this.cartItems$.next(list);
        this.cartCount$.next(list.reduce((sum, i) => sum + i.quantity, 0));
      },
      error: () => {
        this.cartItems$.next([]);
        this.cartCount$.next(0);
      }
    });
  }

  addItem(item: AddToCartRequest): void {
    // Optimistic update
    const existing = this.cartItems$.getValue().find(i => i.productId === item.productId);
    if (existing) {
      this.cartItems$.next(
        this.cartItems$.getValue().map(i =>
          i.productId === item.productId ? { ...i, quantity: i.quantity + item.quantity } : i
        )
      );
    } else {
      this.cartItems$.next([...this.cartItems$.getValue(), item as CartItem]);
    }
    this.cartCount$.next(this.cartItems$.getValue().reduce((sum, i) => sum + i.quantity, 0));

    this.http.post(`${this.base}/cart`, item).subscribe({
      next: () => this.loadCart(),
      error: () => this.loadCart() // revert on error
    });
  }

  updateQty(productId: string, quantity: number): void {
    const userId = this.userService.getUserId();
    // Optimistic update
    this.cartItems$.next(
      this.cartItems$.getValue().map(i => i.productId === productId ? { ...i, quantity } : i)
    );
    this.cartCount$.next(this.cartItems$.getValue().reduce((sum, i) => sum + i.quantity, 0));

    this.http.patch(`${this.base}/cart/${userId}/${productId}`, { quantity }).subscribe({
      next: () => this.loadCart(),
      error: () => this.loadCart() // revert on error
    });
  }

  removeItem(productId: string): void {
    const userId = this.userService.getUserId();
    // Optimistic update
    const updated = this.cartItems$.getValue().filter(i => i.productId !== productId);
    this.cartItems$.next(updated);
    this.cartCount$.next(updated.reduce((sum, i) => sum + i.quantity, 0));

    this.http.delete(`${this.base}/cart/${userId}/${productId}`).subscribe({
      error: () => this.loadCart() // revert on error
    });
  }

  clearCart(): void {
    const userId = this.userService.getUserId();
    this.http.delete(`${this.base}/cart/${userId}`).subscribe({
      next: () => {
        this.cartItems$.next([]);
        this.cartCount$.next(0);
      },
      error: (err) => console.error('Clear cart failed', err)
    });
  }

  refreshCount(): void {
    const userId = this.userService.getUserId();
    this.http.get<CartItem[]>(`${this.base}/cart/${userId}`).subscribe({
      next: (items) => {
        const list = Array.isArray(items) ? items : [];
        this.cartCount$.next(list.reduce((sum, i) => sum + i.quantity, 0));
      },
      error: () => this.cartCount$.next(0)
    });
  }
}
