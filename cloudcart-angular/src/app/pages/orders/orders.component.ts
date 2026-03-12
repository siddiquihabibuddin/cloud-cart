import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { OrderService } from '../../core/services/order.service';
import { UserService } from '../../core/services/user.service';
import { Order } from '../../core/models/order.model';

@Component({
  selector: 'app-orders',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './orders.component.html'
})
export class OrdersComponent implements OnInit {
  private orderService = inject(OrderService);
  private userService = inject(UserService);

  orders = signal<Order[]>([]);
  loading = signal<boolean>(false);
  refreshing = signal<boolean>(false);
  error = signal<string | null>(null);

  ngOnInit(): void {
    this.loadOrders();
  }

  loadOrders(isRefresh = false): void {
    if (isRefresh) {
      this.refreshing.set(true);
    } else {
      this.loading.set(true);
    }

    this.error.set(null);
    this.orderService.listOrders(this.userService.userId()).subscribe({
      next: (orders) => {
        const sorted = (orders ?? []).sort(
          (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        );
        this.orders.set(sorted);
        this.loading.set(false);
        this.refreshing.set(false);
      },
      error: () => {
        this.error.set('Failed to load orders. Please try again.');
        this.loading.set(false);
        this.refreshing.set(false);
      }
    });
  }

  private readonly STATUS_CONFIG: Record<string, { label: string; bg: string; color: string; border: string; dot: string }> = {
    PENDING:          { label: 'Pending',          bg: 'rgba(217,119,6,0.12)',    color: '#fcd34d', border: 'rgba(245,158,11,0.35)',  dot: '#f59e0b' },
    PAID:             { label: 'Paid',              bg: 'rgba(59,130,246,0.12)',   color: '#93c5fd', border: 'rgba(59,130,246,0.35)',  dot: '#3b82f6' },
    SHIPMENT_CREATED: { label: 'Shipment Created',  bg: 'rgba(124,58,237,0.12)',   color: '#c4b5fd', border: 'rgba(124,58,237,0.35)', dot: '#7c3aed' },
    SHIPPED:          { label: 'Shipped',           bg: 'rgba(52,211,153,0.12)',   color: '#34d399', border: 'rgba(52,211,153,0.35)',  dot: '#10b981' },
    FAILED:           { label: 'Failed',            bg: 'rgba(220,38,38,0.12)',    color: '#fca5a5', border: 'rgba(220,38,38,0.35)',   dot: '#ef4444' },
  };

  getStatusBadgeStyle(status: string): string {
    const cfg = this.STATUS_CONFIG[status];
    if (!cfg) return 'background:rgba(107,114,128,0.2);color:#9ca3af;border:1px solid rgba(107,114,128,0.3);';
    return `background:${cfg.bg};color:${cfg.color};border:1px solid ${cfg.border};`;
  }

  getStatusDot(status: string): string {
    return this.STATUS_CONFIG[status]?.dot ?? '#9ca3af';
  }

  getStatusLabel(status: string): string {
    return this.STATUS_CONFIG[status]?.label ?? status;
  }

  formatDate(dateStr: string): string {
    try {
      return new Date(dateStr).toLocaleDateString('en-US', {
        month: 'short', day: 'numeric', year: 'numeric'
      });
    } catch {
      return dateStr;
    }
  }
}
