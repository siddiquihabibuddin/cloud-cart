import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { CartService } from '../../../core/services/cart.service';
import { UserService } from '../../../core/services/user.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, FormsModule],
  templateUrl: './header.component.html'
})
export class HeaderComponent implements OnInit, OnDestroy {
  cartService = inject(CartService);
  userService = inject(UserService);

  editingUserId = false;
  tempUserId = '';
  cartCount = 0;
  userId = '';

  private cartCountSub?: Subscription;
  private userIdSub?: Subscription;

  ngOnInit(): void {
    this.cartCountSub = this.cartService.cartCount.subscribe(count => {
      this.cartCount = count;
    });
    this.userIdSub = this.userService.userId.subscribe(id => {
      this.userId = id;
    });
  }

  ngOnDestroy(): void {
    this.cartCountSub?.unsubscribe();
    this.userIdSub?.unsubscribe();
  }

  startEdit(): void {
    this.tempUserId = this.userService.getUserId();
    this.editingUserId = true;
  }

  saveUserId(): void {
    this.userService.setUserId(this.tempUserId);
    this.editingUserId = false;
    this.cartService.loadCart();
  }

  cancelEdit(): void {
    this.editingUserId = false;
  }

  setNavHover(event: MouseEvent, hovering: boolean): void {
    const el = event.currentTarget as HTMLElement;
    el.style.background = hovering ? 'rgba(255,255,255,0.10)' : 'transparent';
    el.style.color = hovering ? '#ffffff' : '#c4b5fd';
  }
}
