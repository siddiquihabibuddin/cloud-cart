import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { UserService } from '../../../core/services/user.service';
import { CartService } from '../../../core/services/cart.service';

@Component({
  selector: 'app-welcome-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './welcome-modal.component.html'
})
export class WelcomeModalComponent {
  userService = inject(UserService);
  cartService = inject(CartService);

  inputUserId = 'userid4';

  submit(): void {
    this.userService.setUserId(this.inputUserId);
    this.cartService.loadCart();
  }

  dismiss(): void {
    this.userService.dismissModal();
  }

  onInputFocus(event: FocusEvent): void {
    const el = event.target as HTMLInputElement;
    el.style.borderColor = 'rgba(124,58,237,0.7)';
    el.style.boxShadow = '0 0 0 3px rgba(124,58,237,0.25)';
  }

  onInputBlur(event: FocusEvent): void {
    const el = event.target as HTMLInputElement;
    if (!el.value) {
      el.style.borderColor = 'rgba(124,58,237,0.35)';
    }
    el.style.boxShadow = 'none';
  }

  onBtnHover(event: MouseEvent, hovering: boolean): void {
    const btn = event.currentTarget as HTMLButtonElement;
    if (btn.disabled) return;
    btn.style.transform = hovering ? 'translateY(-1px) scale(1.02)' : '';
    btn.style.boxShadow = hovering ? '0 6px 20px rgba(124,58,237,0.65)' : 'var(--cc-shadow-btn)';
  }
}
