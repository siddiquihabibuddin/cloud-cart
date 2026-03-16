import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { CartService } from '../../core/services/cart.service';
import { CartItem } from '../../core/models/cart.model';
import { CartItemRowComponent } from '../../shared/components/cart-item-row/cart-item-row.component';

@Component({
  selector: 'app-cart',
  standalone: true,
  imports: [CommonModule, RouterLink, CartItemRowComponent],
  templateUrl: './cart.component.html'
})
export class CartComponent implements OnInit, OnDestroy {
  cartService = inject(CartService);
  loading = true;
  cartItems: CartItem[] = [];

  private cartItemsSub?: Subscription;

  get total(): number {
    return this.cartItems.reduce((sum, i) => sum + i.price * i.quantity, 0);
  }

  get totalQty(): number {
    return this.cartItems.reduce((sum, i) => sum + i.quantity, 0);
  }

  ngOnInit(): void {
    this.loading = true;
    this.cartItemsSub = this.cartService.cartItems.subscribe(items => {
      this.cartItems = items;
    });
    this.cartService.loadCart();
    // Brief timeout to allow the cart to load before showing content
    setTimeout(() => { this.loading = false; }, 600);
  }

  ngOnDestroy(): void {
    this.cartItemsSub?.unsubscribe();
  }

  onQuantityChange(event: { productId: string; quantity: number }): void {
    this.cartService.updateQty(event.productId, event.quantity);
  }

  onRemove(productId: string): void {
    this.cartService.removeItem(productId);
  }

  trackByProductId(index: number, item: CartItem): string {
    return item.productId;
  }
}
