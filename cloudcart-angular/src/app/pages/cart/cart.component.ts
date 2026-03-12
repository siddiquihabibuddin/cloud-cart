import { Component, OnInit, inject, computed, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CartService } from '../../core/services/cart.service';
import { CartItemRowComponent } from '../../shared/components/cart-item-row/cart-item-row.component';

@Component({
  selector: 'app-cart',
  standalone: true,
  imports: [RouterLink, CartItemRowComponent],
  templateUrl: './cart.component.html'
})
export class CartComponent implements OnInit {
  cartService = inject(CartService);
  loading = signal(true);

  total = computed(() =>
    this.cartService.cartItems().reduce((sum, i) => sum + i.price * i.quantity, 0)
  );

  totalQty = computed(() =>
    this.cartService.cartItems().reduce((sum, i) => sum + i.quantity, 0)
  );

  ngOnInit(): void {
    this.loading.set(true);
    this.cartService.loadCart();
    // Brief timeout to allow the cart to load before showing content
    setTimeout(() => this.loading.set(false), 600);
  }

  onQuantityChange(event: { productId: string; quantity: number }): void {
    this.cartService.updateQty(event.productId, event.quantity);
  }

  onRemove(productId: string): void {
    this.cartService.removeItem(productId);
  }
}
