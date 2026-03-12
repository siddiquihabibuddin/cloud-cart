import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CartService } from '../../../core/services/cart.service';
import { UserService } from '../../../core/services/user.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, FormsModule],
  templateUrl: './header.component.html'
})
export class HeaderComponent {
  cartService = inject(CartService);
  userService = inject(UserService);

  editingUserId = signal<boolean>(false);
  tempUserId = signal<string>('');

  startEdit(): void {
    this.tempUserId.set(this.userService.userId());
    this.editingUserId.set(true);
  }

  saveUserId(): void {
    this.userService.setUserId(this.tempUserId());
    this.editingUserId.set(false);
    this.cartService.loadCart();
  }

  cancelEdit(): void {
    this.editingUserId.set(false);
  }

  setNavHover(event: MouseEvent, hovering: boolean): void {
    const el = event.currentTarget as HTMLElement;
    el.style.background = hovering ? 'rgba(255,255,255,0.10)' : 'transparent';
    el.style.color = hovering ? '#ffffff' : '#c4b5fd';
  }
}
