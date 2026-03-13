import { Component, Input, Output, EventEmitter, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
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
  imports: [CommonModule],
  templateUrl: './product-card.component.html'
})
export class ProductCardComponent implements OnInit {
  @Input({ required: true }) product!: Product;
  @Output() addToCart = new EventEmitter<Product>();

  cartService = inject(CartService);
  userService = inject(UserService);

  added = false;
  loading = false;
  addError: string | null = null;
  hovered = false;
  stock = 0;

  ngOnInit(): void {
    this.stock = this.product.stock;
  }

  get accentColor(): string {
    return CATEGORY_COLORS[this.product.category ?? ''] ?? '#7c3aed';
  }

  get imageUrl(): string {
    if (this.product.imageUrl) return this.product.imageUrl;
    const seed = encodeURIComponent(this.product.title.toLowerCase().replace(/\s+/g, '-'));
    return `https://picsum.photos/seed/${seed}/400/300`;
  }

  get stockBadgeStyle(): string {
    const s = this.stock;
    if (s === 0) return 'background:#fef2f2;color:#dc2626;border:1px solid #fecaca;';
    if (s <= 3)  return 'background:#fff7ed;color:#ea580c;border:1px solid #fed7aa;';
    return 'background:rgba(5,150,105,0.15);color:#34d399;border:1px solid rgba(52,211,153,0.3);';
  }

  get addButtonStyle(): string {
    const c = this.accentColor;
    if (this.added)       return `background:linear-gradient(135deg,#059669,#10b981);box-shadow:0 4px 14px rgba(5,150,105,0.5);`;
    if (this.stock === 0) return `background:linear-gradient(135deg,#374151,#4b5563);opacity:0.65;cursor:not-allowed;`;
    if (!this.userService.getUserId()) return `background:linear-gradient(135deg,${c}88,${c}55);opacity:0.55;cursor:not-allowed;`;
    if (this.loading)     return `background:linear-gradient(135deg,${c}cc,${c}99);box-shadow:0 4px 12px ${c}44;`;
    return `background:linear-gradient(135deg,${c},${c}cc);box-shadow:0 4px 14px ${c}55;`;
  }

  get userId(): string {
    return this.userService.getUserId();
  }

  onAddToCart(): void {
    if (this.stock === 0 || this.loading || !this.userService.getUserId()) return;
    this.stock = Math.max(0, this.stock - 1);
    this.loading = true;
    this.addError = null;

    const existing = this.cartService.getCartItems().find(i => i.productId === this.product.productId);
    if (existing) {
      this.cartService.updateQty(this.product.productId, existing.quantity + 1);
    } else {
      this.cartService.addItem({
        userId: this.userService.getUserId(),
        productId: this.product.productId,
        title: this.product.title,
        price: this.product.price,
        quantity: 1
      });
    }

    this.added = true;
    this.loading = false;
    this.addToCart.emit(this.product);
    setTimeout(() => { this.added = false; }, 2000);
  }
}
