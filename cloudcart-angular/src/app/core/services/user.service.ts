import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class UserService {
  private userId$ = new BehaviorSubject<string>(localStorage.getItem('cc_user_id') ?? 'userid4');
  private showWelcomeModal$ = new BehaviorSubject<boolean>(!localStorage.getItem('cc_user_id'));

  get userId(): BehaviorSubject<string> {
    return this.userId$;
  }

  get showWelcomeModal(): BehaviorSubject<boolean> {
    return this.showWelcomeModal$;
  }

  getUserId(): string {
    return this.userId$.getValue();
  }

  getShowWelcomeModal(): boolean {
    return this.showWelcomeModal$.getValue();
  }

  setUserId(id: string): void {
    const trimmed = id.trim();
    if (!trimmed) return;
    localStorage.setItem('cc_user_id', trimmed);
    this.userId$.next(trimmed);
    this.showWelcomeModal$.next(false);
  }

  dismissModal(): void {
    this.showWelcomeModal$.next(false);
  }
}
