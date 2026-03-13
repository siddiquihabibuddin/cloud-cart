import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { CartService } from '../../core/services/cart.service';
import { OrderService } from '../../core/services/order.service';
import { UserService } from '../../core/services/user.service';
import { CartItem } from '../../core/models/cart.model';
import { Order } from '../../core/models/order.model';

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './checkout.component.html'
})
export class CheckoutComponent implements OnInit, OnDestroy {
  private cartService = inject(CartService);
  private orderService = inject(OrderService);
  private userService = inject(UserService);
  private router = inject(Router);

  cartItems: CartItem[] = [];

  get total(): number {
    return this.cartItems.reduce((sum, i) => sum + i.price * i.quantity, 0);
  }

  get totalQty(): number {
    return this.cartItems.reduce((sum, i) => sum + i.quantity, 0);
  }

  get statusGradient(): string {
    const s = this.orderStatus;
    if (s === 'PAID' || s === 'SHIPMENT_CREATED' || s === 'SHIPPED')
      return 'linear-gradient(135deg, #059669, #10b981)';
    if (s === 'FAILED') return 'linear-gradient(135deg, #dc2626, #ef4444)';
    return 'linear-gradient(135deg, #d97706, #f59e0b)';
  }

  get statusBgColor(): string {
    const s = this.orderStatus;
    if (s === 'PAID' || s === 'SHIPMENT_CREATED' || s === 'SHIPPED')
      return 'rgba(5,150,105,0.1)';
    if (s === 'FAILED') return 'rgba(220,38,38,0.1)';
    return 'rgba(217,119,6,0.1)';
  }

  get statusBorderColor(): string {
    const s = this.orderStatus;
    if (s === 'PAID' || s === 'SHIPMENT_CREATED' || s === 'SHIPPED')
      return 'rgba(52,211,153,0.35)';
    if (s === 'FAILED') return 'rgba(220,38,38,0.35)';
    return 'rgba(245,158,11,0.35)';
  }

  get statusTextColor(): string {
    const s = this.orderStatus;
    if (s === 'PAID' || s === 'SHIPMENT_CREATED' || s === 'SHIPPED') return '#34d399';
    if (s === 'FAILED') return '#fca5a5';
    return '#fcd34d';
  }

  get statusLabel(): string {
    switch (this.orderStatus) {
      case 'SHIPMENT_CREATED': return 'Shipment Created';
      case 'PAID':             return 'Payment Successful';
      case 'PENDING':          return 'Processing Payment';
      case 'FAILED':           return 'Payment Failed';
      case 'SHIPPED':          return 'Order Shipped';
      default:                 return 'Order Submitted';
    }
  }

  get statusDetail(): string {
    switch (this.orderStatus) {
      case 'SHIPMENT_CREATED':
        return 'Your order has been confirmed and a shipment is being prepared.';
      case 'PAID':
        return 'Payment was processed successfully. Your order is confirmed.';
      case 'PENDING':
        return 'Payment is being processed. This page updates automatically.';
      case 'FAILED':
        return 'The payment could not be processed. Please try again.';
      case 'SHIPPED':
        return 'Your order is on its way!';
      default:
        return 'Order is being processed — check back shortly in My Orders.';
    }
  }

  placing = false;
  orderId: string | null = null;
  orderStatus: string | null = null;
  orderData: Order | null = null;
  error: string | null = null;
  pollCount = 0;

  private pollSub?: Subscription;
  private readonly MAX_POLLS = 20;

  private cartItemsSub?: Subscription;

  ngOnInit(): void {
    this.cartService.loadCart();
    this.cartItemsSub = this.cartService.cartItems.subscribe(items => {
      this.cartItems = items;
    });
  }

  ngOnDestroy(): void {
    this.stopPolling();
    this.cartItemsSub?.unsubscribe();
  }

  placeOrder(): void {
    if (this.placing || this.cartItems.length === 0) return;
    this.placing = true;
    this.error = null;

    const request = {
      userId: this.userService.getUserId(),
      items: this.cartItems.map(i => ({
        productId: i.productId,
        quantity: i.quantity,
        price: i.price
      }))
    };

    this.orderService.placeOrder(request).subscribe({
      next: (res) => {
        this.orderId = res.orderId;
        this.orderStatus = 'PENDING';
        this.placing = false;
        this.startPolling(res.orderId);
      },
      error: (err) => {
        this.error = err.error?.message ?? 'Failed to place order. Please try again.';
        this.placing = false;
      }
    });
  }

  private startPolling(orderId: string): void {
    let delay = 2000;
    const poll = () => {
      if (this.pollCount >= this.MAX_POLLS) {
        this.stopPolling();
        return;
      }
      this.orderService.getOrder(orderId, this.userService.getUserId()).subscribe({
        next: (order) => {
          this.orderData = order;
          this.orderStatus = order.status;
          this.pollCount += 1;

          if (['PAID', 'SHIPPED', 'SHIPMENT_CREATED', 'FAILED'].includes(order.status)) {
            this.stopPolling();
            if (order.status !== 'FAILED') {
              this.cartService.clearCart();
            }
          } else {
            delay = Math.min(delay * 1.2, 5000);
            setTimeout(poll, delay);
          }
        },
        error: () => {
          this.pollCount += 1;
          if (this.pollCount < this.MAX_POLLS) {
            setTimeout(poll, delay);
          }
        }
      });
    };
    setTimeout(poll, delay);
  }

  private stopPolling(): void {
    this.pollSub?.unsubscribe();
  }

  getStatusStep(): number {
    const s = this.orderStatus;
    if (s === 'PAID') return 2;
    if (s === 'SHIPMENT_CREATED' || s === 'SHIPPED') return 3;
    return 1;
  }

  trackByProductId(index: number, item: CartItem): string {
    return item.productId;
  }
}
