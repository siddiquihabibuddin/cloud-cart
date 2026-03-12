import { Component, inject, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { HeaderComponent } from './shared/components/header/header.component';
import { FooterComponent } from './shared/components/footer/footer.component';
import { WelcomeModalComponent } from './shared/components/welcome-modal/welcome-modal.component';
import { CartService } from './core/services/cart.service';
import { UserService } from './core/services/user.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, HeaderComponent, FooterComponent, WelcomeModalComponent],
  templateUrl: './app.component.html'
})
export class AppComponent implements OnInit {
  cartService = inject(CartService);
  userService = inject(UserService);

  ngOnInit(): void {
    this.cartService.loadCart();
  }
}
