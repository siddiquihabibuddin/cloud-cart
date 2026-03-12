import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class UserService {
  userId = signal<string>(localStorage.getItem('cc_user_id') ?? 'userid4');
  showWelcomeModal = signal<boolean>(!localStorage.getItem('cc_user_id'));

  setUserId(id: string): void {
    const trimmed = id.trim();
    if (!trimmed) return;
    localStorage.setItem('cc_user_id', trimmed);
    this.userId.set(trimmed);
    this.showWelcomeModal.set(false);
  }

  dismissModal(): void {
    this.showWelcomeModal.set(false);
  }
}
