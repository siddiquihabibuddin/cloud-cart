import { Component, Input, Output, EventEmitter, inject, signal } from '@angular/core';
import { Product } from '../../../core/models/product.model';
import { CartService } from '../../../core/services/cart.service';
import { UserService } from '../../../core/services/user.service';

const CATEGORY_COLORS: Record<string, string> = {
  Electronics: '#6366f1',
  Clothing:    '#db2777',
  Books:       '#ea580c',
  Home:        '#0891b2',
  Sports:      '#059669',
  Toys:        '#d97706',
  Beauty:      '#9333ea',
  Food:        '#16a34a',
};

@Component({
  selector: 'app-product-card',
  standalone: true,
  templateUrl: './product-card.component.html'
})
export class ProductCardComponent {
  @Input({ required: true }) product!: Product;
  @Output() addToCart = new EventEmitter<Product>();

  cartService = inject(CartService);
  userService = inject(UserService);

  added   = signal<boolean>(false);
  loading = signal<boolean>(false);
  addError = signal<string | null>(null);
  hovered = false;

  get accentColor(): string {
    return CATEGORY_COLORS[this.product.category ?? ''] ?? '#7c3aed';
  }

  get stockBadgeStyle(): string {
    const s = this.product.stock;
    if (s === 0) return 'background:#fef2f2;color:#dc2626;border:1px solid #fecaca;';
    if (s <= 3)  return 'background:#fff7ed;color:#ea580c;border:1px solid #fed7aa;';
    return 'background:rgba(5,150,105,0.15);color:#34d399;border:1px solid rgba(52,211,153,0.3);';
  }

  get addButtonStyle(): string {
    const c = this.accentColor;
    if (this.added())          return `background:linear-gradient(135deg,#059669,#10b981);box-shadow:0 4px 14px rgba(5,150,105,0.5);`;
    if (this.product.stock === 0) return `background:linear-gradient(135deg,#374151,#4b5563);opacity:0.65;cursor:not-allowed;`;
    if (!this.userService.userId()) return `background:linear-gradient(135deg,${c}88,${c}55);opacity:0.55;cursor:not-allowed;`;
    if (this.loading())        return `background:linear-gradient(135deg,${c}cc,${c}99);box-shadow:0 4px 12px ${c}44;`;
    return `background:linear-gradient(135deg,${c},${c}cc);box-shadow:0 4px 14px ${c}55;`;
  }

  onAddToCart(): void {
    if (this.product.stock === 0 || this.loading() || !this.userService.userId()) return;
    this.loading.set(true);
    this.addError.set(null);
    this.cartService.addItem({
      userId: this.userService.userId(),
      productId: this.product.productID,
      productName: this.product.title,
      price: this.product.price,
      quantity: 1
    });
    this.added.set(true);
    this.loading.set(false);
    this.addToCart.emit(this.product);
    setTimeout(() => this.added.set(false), 2000);
  }
}
