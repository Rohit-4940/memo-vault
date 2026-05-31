import { Component, OnInit, OnDestroy } from '@angular/core';
import { NgForOf, NgIf, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';
import { LocalStorageUtil } from '../../@core/utils/local-storage-util';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [NgForOf, NgIf, DatePipe, FormsModule],
  template: `
    <div class="notification-wrapper" (click)="toggleDropdown($event)">
      <button class="notif-btn" [class.has-unread]="unreadCount > 0">
        <i class="fas fa-bell"></i>
        @if (unreadCount > 0) {
          <span class="notif-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
        }
      </button>

      @if (showDropdown) {
        <div class="notif-dropdown" (click)="$event.stopPropagation()">
          <div class="notif-header">
            <h6>Notifications</h6>
            @if (unreadCount > 0) {
              <button class="btn btn-sm btn-link p-0" (click)="markAllRead()">Mark all read</button>
            }
          </div>
          <div class="notif-list">
            @if (loading) {
              <div class="text-center py-3"><div class="spinner-border spinner-border-sm"></div></div>
            } @else {
              @for (n of notifications; track n.id) {
                <div class="notif-item" [class.unread]="!n.isRead" (click)="handleClick(n)">
                  <div class="notif-icon">
                    <i class="fas" [class]="getIcon(n.type)"></i>
                  </div>
                  <div class="notif-body">
                    <div class="notif-title">{{ n.title }}</div>
                    <div class="notif-msg">{{ n.message }}</div>
                    <div class="notif-time">{{ n.createdDate | date:'short' }}</div>
                  </div>
                  @if (!n.isRead) {
                    <button class="notif-dismiss" (click)="markRead(n.id); $event.stopPropagation()">
                      <i class="fas fa-check"></i>
                    </button>
                  }
                </div>
              } @empty {
                <div class="text-center text-muted py-4"><i class="fas fa-inbox fa-2x mb-2 d-block"></i>No notifications</div>
              }
            }
          </div>
          <div class="notif-footer">
            <button class="btn btn-sm btn-link" (click)="viewAll()">View all</button>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .notification-wrapper { position: relative; display: inline-block; }
    .notif-btn { position: relative; background: none; border: none; color: var(--nexmemo-text-secondary); font-size: 1.1rem; padding: 6px 8px; cursor: pointer; border-radius: var(--radius-sm); transition: all 0.2s; }
    .notif-btn:hover { background: var(--nexmemo-bg); color: var(--nexmemo-text); }
    .notif-btn.has-unread { color: var(--nexmemo-primary); }
    .notif-badge { position: absolute; top: 0; right: 0; background: var(--nexmemo-danger); color: white; font-size: 0.6rem; font-weight: 700; min-width: 16px; height: 16px; border-radius: 8px; display: flex; align-items: center; justify-content: center; padding: 0 4px; box-shadow: 0 2px 6px rgba(239,68,68,0.3); }
    .notif-dropdown { position: absolute; top: calc(100% + 8px); right: 0; width: 360px; max-height: 480px; background: var(--nexmemo-surface); border: 1px solid var(--nexmemo-border); border-radius: var(--radius-md); box-shadow: 0 8px 32px rgba(0,0,0,0.12); z-index: 1000; display: flex; flex-direction: column; overflow: hidden; }
    .notif-header { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border-bottom: 1px solid var(--nexmemo-border); h6 { margin: 0; font-weight: 600; } }
    .notif-list { overflow-y: auto; flex: 1; max-height: 360px; }
    .notif-item { display: flex; gap: 12px; padding: 12px 16px; cursor: pointer; transition: background 0.15s; border-bottom: 1px solid var(--nexmemo-border-light); align-items: flex-start; }
    .notif-item:hover { background: var(--nexmemo-bg); }
    .notif-item.unread { background: rgba(79,70,229,0.04); border-left: 3px solid var(--nexmemo-primary); }
    .notif-icon { width: 32px; height: 32px; border-radius: 50%; background: var(--nexmemo-bg); display: flex; align-items: center; justify-content: center; color: var(--nexmemo-primary); flex-shrink: 0; i { font-size: 0.8rem; } }
    .notif-body { flex: 1; min-width: 0; }
    .notif-title { font-size: 0.82rem; font-weight: 600; color: var(--nexmemo-text); margin-bottom: 2px; }
    .notif-msg { font-size: 0.75rem; color: var(--nexmemo-text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .notif-time { font-size: 0.68rem; color: var(--nexmemo-text-muted); margin-top: 4px; }
    .notif-dismiss { background: none; border: none; color: var(--nexmemo-text-muted); cursor: pointer; padding: 4px; border-radius: 50%; flex-shrink: 0; opacity: 0; }
    .notif-item:hover .notif-dismiss { opacity: 1; }
    .notif-dismiss:hover { color: var(--nexmemo-success); background: rgba(16,185,129,0.1); }
    .notif-footer { border-top: 1px solid var(--nexmemo-border); padding: 8px 16px; text-align: center; }
  `]
})
export class NotificationBellComponent implements OnInit, OnDestroy {
  notifications: any[] = [];
  unreadCount = 0;
  showDropdown = false;
  loading = false;
  private destroy$ = new Subject<void>();
  private pollInterval: any;

  constructor(private http: HttpClient, private router: Router) {}

  ngOnInit() {
    this.loadUnreadCount();
    this.pollInterval = setInterval(() => this.loadUnreadCount(), 30000);
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
    if (this.pollInterval) clearInterval(this.pollInterval);
  }

  get userId(): number { return LocalStorageUtil.getStorage().userId || 0; }

  loadNotifications() {
    this.loading = true;
    this.http.get<any>(`${ApiConfig.URL}/api/v1/notifications?page=0&size=10`).subscribe({
      next: (res) => { this.notifications = res.content || []; this.loading = false; },
      error: () => this.loading = false
    });
  }

  loadUnreadCount() {
    this.http.get<any>(`${ApiConfig.URL}/api/v1/notifications/unread-count`).subscribe({
      next: (res) => { this.unreadCount = res?.data?.count || 0; },
      error: () => {}
    });
  }

  toggleDropdown(event: Event) {
    this.showDropdown = !this.showDropdown;
    if (this.showDropdown) this.loadNotifications();
  }

  markRead(id: number) {
    this.http.put(`${ApiConfig.URL}/api/v1/notifications/${id}/read`, {}).subscribe({
      next: () => {
        const n = this.notifications.find(x => x.id === id);
        if (n) n.isRead = true;
        this.loadUnreadCount();
      }
    });
  }

  markAllRead() {
    this.http.put(`${ApiConfig.URL}/api/v1/notifications/read-all`, {}).subscribe({
      next: () => {
        this.notifications.forEach(n => n.isRead = true);
        this.unreadCount = 0;
      }
    });
  }

  handleClick(n: any) {
    if (!n.isRead) this.markRead(n.id);
    if (n.referenceType === 'MEMO' && n.referenceId) {
      this.router.navigate(['/layout/memo'], { queryParams: { detail: n.referenceId } });
    }
    this.showDropdown = false;
  }

  viewAll() {
    this.showDropdown = false;
    this.router.navigate(['/layout/notifications']);
  }

  getIcon(type: string): string {
    switch (type) {
      case 'MEMO_APPROVAL': return 'fa-file-alt';
      case 'MEMO_APPROVED': return 'fa-check-circle';
      case 'MEMO_REJECTED': return 'fa-times-circle';
      default: return 'fa-bell';
    }
  }
}
