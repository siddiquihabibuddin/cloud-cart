import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { Subscription } from 'rxjs';
import { HeaderComponent } from './shared/components/header/header.component';
import { FooterComponent } from './shared/components/footer/footer.component';
import { WelcomeModalComponent } from './shared/components/welcome-modal/welcome-modal.component';
import { CartService } from './core/services/cart.service';
import { UserService } from './core/services/user.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, HeaderComponent, FooterComponent, WelcomeModalComponent],
  templateUrl: './app.component.html'
})
export class AppComponent implements OnInit, OnDestroy {
  cartService = inject(CartService);
  userService = inject(UserService);

  showWelcomeModal = false;

  private modalSub?: Subscription;

  ngOnInit(): void {
    this.modalSub = this.userService.showWelcomeModal.subscribe(show => {
      this.showWelcomeModal = show;
    });
    this.cartService.loadCart();
  }

  ngOnDestroy(): void {
    this.modalSub?.unsubscribe();
  }
}
