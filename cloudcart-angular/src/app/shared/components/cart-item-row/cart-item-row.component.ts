import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CartItem } from '../../../core/models/cart.model';

@Component({
  selector: 'app-cart-item-row',
  standalone: true,
  templateUrl: './cart-item-row.component.html'
})
export class CartItemRowComponent {
  @Input({ required: true }) item!: CartItem;
  @Output() quantityChange = new EventEmitter<{ productId: string; quantity: number }>();
  @Output() remove = new EventEmitter<string>();

  onDecrement(): void {
    if (this.item.quantity > 1) {
      this.quantityChange.emit({ productId: this.item.productId, quantity: this.item.quantity - 1 });
    }
  }

  onIncrement(): void {
    this.quantityChange.emit({ productId: this.item.productId, quantity: this.item.quantity + 1 });
  }

  onRemove(): void {
    this.remove.emit(this.item.productId);
  }

  get lineTotal(): number {
    return this.item.price * this.item.quantity;
  }

  setRemoveHover(event: MouseEvent, hovering: boolean): void {
    const el = event.currentTarget as HTMLElement;
    if (hovering) {
      el.style.background = 'linear-gradient(135deg, #dc2626, #ef4444)';
      el.style.color = '#ffffff';
      el.style.borderColor = 'transparent';
      el.style.boxShadow = '0 4px 12px rgba(220,38,38,0.45)';
      el.style.transform = 'scale(1.05)';
    } else {
      el.style.background = 'rgba(220,38,38,0.12)';
      el.style.color = '#dc2626';
      el.style.borderColor = 'rgba(220,38,38,0.3)';
      el.style.boxShadow = 'none';
      el.style.transform = 'scale(1)';
    }
  }
}
