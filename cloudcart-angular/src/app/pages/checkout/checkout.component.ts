import { Component, OnInit, OnDestroy, inject, signal, computed } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { CartService } from '../../core/services/cart.service';
import { OrderService } from '../../core/services/order.service';
import { UserService } from '../../core/services/user.service';
import { Order } from '../../core/models/order.model';

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './checkout.component.html'
})
export class CheckoutComponent implements OnInit, OnDestroy {
  private cartService = inject(CartService);
  private orderService = inject(OrderService);
  private userService = inject(UserService);
  private router = inject(Router);

  cartItems = this.cartService.cartItems;
  total = computed(() =>
    this.cartItems().reduce((sum, i) => sum + i.price * i.quantity, 0)
  );
  totalQty = computed(() =>
    this.cartItems().reduce((sum, i) => sum + i.quantity, 0)
  );

  statusGradient = computed(() => {
    const s = this.orderStatus();
    if (s === 'PAID' || s === 'SHIPMENT_CREATED' || s === 'SHIPPED')
      return 'linear-gradient(135deg, #059669, #10b981)';
    if (s === 'FAILED') return 'linear-gradient(135deg, #dc2626, #ef4444)';
    return 'linear-gradient(135deg, #d97706, #f59e0b)';
  });

  statusBgColor = computed(() => {
    const s = this.orderStatus();
    if (s === 'PAID' || s === 'SHIPMENT_CREATED' || s === 'SHIPPED')
      return 'rgba(5,150,105,0.1)';
    if (s === 'FAILED') return 'rgba(220,38,38,0.1)';
    return 'rgba(217,119,6,0.1)';
  });

  statusBorderColor = computed(() => {
    const s = this.orderStatus();
    if (s === 'PAID' || s === 'SHIPMENT_CREATED' || s === 'SHIPPED')
      return 'rgba(52,211,153,0.35)';
    if (s === 'FAILED') return 'rgba(220,38,38,0.35)';
    return 'rgba(245,158,11,0.35)';
  });

  statusTextColor = computed(() => {
    const s = this.orderStatus();
    if (s === 'PAID' || s === 'SHIPMENT_CREATED' || s === 'SHIPPED') return '#34d399';
    if (s === 'FAILED') return '#fca5a5';
    return '#fcd34d';
  });

  statusLabel = computed(() => {
    switch (this.orderStatus()) {
      case 'SHIPMENT_CREATED': return 'Shipment Created';
      case 'PAID':             return 'Payment Successful';
      case 'PENDING':          return 'Processing Payment';
      case 'FAILED':           return 'Payment Failed';
      case 'SHIPPED':          return 'Order Shipped';
      default:                 return 'Order Submitted';
    }
  });

  statusDetail = computed(() => {
    switch (this.orderStatus()) {
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
  });

  placing = signal<boolean>(false);
  orderId = signal<string | null>(null);
  orderStatus = signal<string | null>(null);
  orderData = signal<Order | null>(null);
  error = signal<string | null>(null);
  pollCount = signal<number>(0);

  private pollSub?: Subscription;
  private readonly MAX_POLLS = 20;

  ngOnInit(): void {
    this.cartService.loadCart();
  }

  ngOnDestroy(): void {
    this.stopPolling();
  }

  placeOrder(): void {
    if (this.placing() || this.cartItems().length === 0) return;
    this.placing.set(true);
    this.error.set(null);

    const request = {
      userId: this.userService.userId(),
      items: this.cartItems().map(i => ({
        productId: i.productId,
        quantity: i.quantity,
        price: i.price
      }))
    };

    this.orderService.placeOrder(request).subscribe({
      next: (res) => {
        this.orderId.set(res.orderId);
        this.orderStatus.set('PENDING');
        this.placing.set(false);
        this.startPolling(res.orderId);
      },
      error: (err) => {
        this.error.set(err.error?.message ?? 'Failed to place order. Please try again.');
        this.placing.set(false);
      }
    });
  }

  private startPolling(orderId: string): void {
    let delay = 2000;
    const poll = () => {
      if (this.pollCount() >= this.MAX_POLLS) {
        this.stopPolling();
        return;
      }
      this.orderService.getOrder(orderId, this.userService.userId()).subscribe({
        next: (order) => {
          this.orderData.set(order);
          this.orderStatus.set(order.status);
          this.pollCount.update(c => c + 1);

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
          this.pollCount.update(c => c + 1);
          if (this.pollCount() < this.MAX_POLLS) {
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
    const s = this.orderStatus();
    if (s === 'PAID') return 2;
    if (s === 'SHIPMENT_CREATED' || s === 'SHIPPED') return 3;
    return 1;
  }
}
