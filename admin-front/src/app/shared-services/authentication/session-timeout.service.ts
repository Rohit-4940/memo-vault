import { Injectable, NgZone, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { LocalStorageUtil } from '../../@core/utils/local-storage-util';

@Injectable({ providedIn: 'root' })
export class SessionTimeoutService implements OnDestroy {
  private static readonly DEFAULT_TIMEOUT_MS = 10 * 60 * 1000;
  private static readonly WARNING_BEFORE_MS = 60 * 1000;

  private timeoutMs = SessionTimeoutService.DEFAULT_TIMEOUT_MS;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private warningTimer: ReturnType<typeof setTimeout> | null = null;
  private listeners: (() => void)[] = [];
  private destroyed = false;

  onWarning: (() => void) | null = null;
  onExpired: (() => void) | null = null;

  constructor(private router: Router, private ngZone: NgZone) {
    this.startWatching();
  }

  setTimeout(minutes: number): void {
    this.timeoutMs = minutes * 60 * 1000;
    this.resetTimer();
  }

  private startWatching(): void {
    this.ngZone.runOutsideAngular(() => {
      const events = ['mousedown', 'mousemove', 'keydown', 'keypress', 'keyup', 'click', 'scroll', 'wheel', 'touchstart', 'touchmove'];
      for (const event of events) {
        const handler = () => this.resetTimer();
        document.addEventListener(event, handler);
        this.listeners.push(() => document.removeEventListener(event, handler));
      }
    });
    this.resetTimer();
  }

  private resetTimer(): void {
    if (this.destroyed) return;
    this.clearTimers();
    this.ngZone.runOutsideAngular(() => {
      this.warningTimer = setTimeout(() => {
        this.ngZone.run(() => {
          if (this.onWarning) this.onWarning();
        });
      }, this.timeoutMs - SessionTimeoutService.WARNING_BEFORE_MS);
      this.timer = setTimeout(() => {
        this.ngZone.run(() => this.logout());
      }, this.timeoutMs);
    });
  }

  private clearTimers(): void {
    if (this.warningTimer) { clearTimeout(this.warningTimer); this.warningTimer = null; }
    if (this.timer) { clearTimeout(this.timer); this.timer = null; }
  }

  logout(): void {
    this.clearTimers();
    LocalStorageUtil.clearStorage();
    if (this.onExpired) this.onExpired();
    this.router.navigate(['/login'], { queryParams: { timeout: 'true' } });
  }

  stop(): void {
    this.destroyed = true;
    this.clearTimers();
    for (const remove of this.listeners) remove();
    this.listeners = [];
  }

  ngOnDestroy(): void {
    this.stop();
  }
}