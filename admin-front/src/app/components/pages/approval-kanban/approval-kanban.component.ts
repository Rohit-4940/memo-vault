import { Component, OnInit, inject } from '@angular/core';
import { NgIf, NgClass, DatePipe, LowerCasePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { ToastrService } from 'ngx-toastr';
import { ApiConfig } from '../../../@core/utils/api/ApiConfig';
import { forkJoin } from 'rxjs';

@Component({
  selector: 'app-approval-kanban',
  standalone: true,
  imports: [NgIf, NgClass, DatePipe, LowerCasePipe, FormsModule],
  template: `
    <div class="kanban-wrapper">
      <div class="kanban-header">
        <div>
          <h1><i class="fas fa-regular fa-square-kanban"></i> Approval Pipeline</h1>
          <p class="header-subtitle">Track and manage memos across all approval stages</p>
        </div>
        <button class="refresh-btn" (click)="refreshData()" [class.spinning]="isRefreshing">
          <i class="fas fa-sync-alt"></i> Refresh
        </button>
      </div>

      @if (isLoading && !isRefreshing) {
        <div class="global-loader">
          <div class="loader-ring"></div>
          <span>Loading pipeline...</span>
        </div>
      } @else {
        <div class="kanban-board">
          @for (col of columns; track col.key) {
            <div class="kanban-col">
              <div class="col-header" [style.background]="col.gradient">
                <div class="col-header-left">
                  <i class="fas" [ngClass]="col.icon"></i>
                  <span>{{ col.title }}</span>
                </div>
                <span class="col-count">{{ col.memos.length }}</span>
              </div>

              <div class="col-body">
                @if (col.loading) {
                  <div class="col-state">
                    <div class="loader-ring loader-ring-sm"></div>
                    <span>Loading...</span>
                  </div>
                } @else if (col.memos.length === 0) {
                  <div class="col-state">
                    <i class="fas fa-inbox"></i>
                    <span>No {{ col.title | lowercase }} memos</span>
                  </div>
                } @else {
                  @for (memo of col.memos; track memo.id; let i = $index) {
                    <div class="kanban-card" [style.animation-delay]="(i * 0.04) + 's'"
                         (click)="navigateToMemo()">
                      <div class="card-border" [ngClass]="'border-' + (memo.priority || 'LOW').toLowerCase()"></div>
                      <div class="card-body-inner">
                        <div class="card-top">
                          <span class="card-memo-no">{{ memo.memoNo || '—' }}</span>
                        </div>
                        <div class="card-title">{{ memo.title }}</div>
                        <div class="card-amount">{{ formatAmount(memo.amount) }}</div>
                        <div class="card-badges">
                          <span class="badge" [ngClass]="priorityBadge(memo.priority)">
                            <i class="fas fa-flag"></i> {{ memo.priority || 'LOW' }}
                          </span>
                          @if (memo.memoType?.name || memo.categoryName) {
                            <span class="badge badge-info">
                              <i class="fas fa-tag"></i> {{ memo.memoType?.name || memo.categoryName }}
                            </span>
                          }
                        </div>
                        <div class="card-footer">
                          <span class="card-created">
                            <i class="far fa-calendar-alt"></i> {{ (memo.createdAt || memo.createdDate) | date:'MMM dd, yyyy' }}
                          </span>
                          <span class="card-creator">
                            <i class="far fa-user"></i> {{ getCreatorName(memo) }}
                          </span>
                        </div>
                      </div>
                    </div>
                  }
                }
              </div>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .kanban-wrapper {
      padding: 1.5rem 1.75rem;
      max-width: 1440px;
      margin: 0 auto;
      animation: fadeIn 0.3s ease;
      min-height: calc(100vh - 6rem);
    }

    .kanban-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1.5rem;
      flex-wrap: wrap;
      gap: 1rem;
    }

    .kanban-header h1 {
      font-size: 1.5rem;
      font-weight: 700;
      color: var(--nexmemo-text);
    }

    .kanban-header h1 i {
      color: var(--nexmemo-primary);
      margin-right: 0.5rem;
    }

    .header-subtitle {
      color: var(--nexmemo-text-secondary);
      font-size: 0.875rem;
      margin-top: 0.25rem;
    }

    .refresh-btn {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.5rem 1.25rem;
      font-size: 0.85rem;
      font-weight: 500;
      border-radius: var(--radius-sm);
      border: 1px solid var(--nexmemo-border);
      background: var(--nexmemo-surface);
      color: var(--nexmemo-text);
      cursor: pointer;
      transition: all var(--transition-fast);
    }

    .refresh-btn:hover {
      background: var(--nexmemo-surface-hover);
      border-color: var(--nexmemo-text-muted);
    }

    .refresh-btn.spinning i {
      animation: spin 0.6s linear infinite;
    }

    .global-loader {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 5rem 0;
      gap: 1rem;
      color: var(--nexmemo-text-secondary);
      font-size: 0.9rem;
    }

    .loader-ring {
      width: 2rem;
      height: 2rem;
      border: 2.5px solid var(--nexmemo-border);
      border-top-color: var(--nexmemo-primary);
      border-radius: 50%;
      animation: spin 0.6s linear infinite;
    }

    .loader-ring-sm {
      width: 1.25rem;
      height: 1.25rem;
      border-width: 2px;
    }

    .kanban-board {
      display: flex;
      gap: 1rem;
      overflow-x: auto;
      padding-bottom: 0.5rem;
      min-height: calc(100vh - 10rem);
      align-items: flex-start;
    }

    .kanban-board::-webkit-scrollbar { height: 5px; }
    .kanban-board::-webkit-scrollbar-track { background: transparent; }
    .kanban-board::-webkit-scrollbar-thumb { background: var(--nexmemo-border); border-radius: var(--radius-full); }

    .kanban-col {
      flex: 0 0 300px;
      min-width: 280px;
      display: flex;
      flex-direction: column;
      border-radius: var(--radius-lg);
      background: var(--nexmemo-surface);
      border: 1px solid var(--nexmemo-border);
      box-shadow: var(--shadow-sm);
      overflow: hidden;
      max-height: calc(100vh - 10rem);
      transition: box-shadow var(--transition-normal);
    }

    .kanban-col:hover {
      box-shadow: var(--shadow-md);
    }

    .col-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0.85rem 1rem;
      color: #fff;
      font-weight: 600;
      font-size: 0.85rem;
      letter-spacing: 0.01em;
      flex-shrink: 0;
    }

    .col-header-left {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .col-header-left i {
      font-size: 0.9rem;
    }

    .col-count {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 1.5rem;
      height: 1.5rem;
      padding: 0 0.4rem;
      border-radius: var(--radius-full);
      background: rgba(255, 255, 255, 0.25);
      font-size: 0.7rem;
      font-weight: 700;
      backdrop-filter: blur(4px);
    }

    .col-body {
      flex: 1;
      overflow-y: auto;
      padding: 0.65rem;
      display: flex;
      flex-direction: column;
      gap: 0.65rem;
    }

    .col-body::-webkit-scrollbar { width: 4px; }
    .col-body::-webkit-scrollbar-thumb { background: var(--nexmemo-border); border-radius: var(--radius-full); }

    .col-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 2.5rem 1rem;
      color: var(--nexmemo-text-muted);
      gap: 0.6rem;
      font-size: 0.8rem;
    }

    .col-state i {
      font-size: 1.75rem;
    }

    .kanban-card {
      display: flex;
      background: rgba(255, 255, 255, 0.85);
      backdrop-filter: blur(12px);
      -webkit-backdrop-filter: blur(12px);
      border: 1px solid rgba(255, 255, 255, 0.3);
      border-radius: var(--radius-md);
      overflow: hidden;
      cursor: pointer;
      transition: all var(--transition-normal);
      box-shadow: var(--shadow-xs);
      animation: cardFadeIn 0.35s ease both;
    }

    .kanban-card:hover {
      transform: translateY(-2px);
      box-shadow: var(--shadow-lg);
      border-color: rgba(255, 255, 255, 0.6);
    }

    .card-border {
      width: 4px;
      flex-shrink: 0;
    }

    .border-low { background: #9ca3af; }
    .border-medium { background: #3b82f6; }
    .border-high { background: #f97316; }
    .border-urgent { background: #ef4444; }

    .card-body-inner {
      flex: 1;
      padding: 0.75rem 0.85rem;
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
      min-width: 0;
    }

    .card-top {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .card-memo-no {
      font-size: 0.65rem;
      font-weight: 600;
      color: var(--nexmemo-text-muted);
      letter-spacing: 0.03em;
      font-family: var(--font-mono);
      text-transform: uppercase;
    }

    .card-title {
      font-size: 0.85rem;
      font-weight: 600;
      color: var(--nexmemo-text);
      line-height: 1.3;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
      word-break: break-word;
    }

    .card-amount {
      font-size: 0.9rem;
      font-weight: 700;
      color: var(--nexmemo-primary);
      font-family: var(--font-mono);
    }

    .card-badges {
      display: flex;
      flex-wrap: wrap;
      gap: 0.3rem;
      margin-top: 0.15rem;
    }

    .card-badges .badge {
      font-size: 0.6rem;
      padding: 0.15rem 0.5rem;
    }

    .card-badges .badge i {
      font-size: 0.55rem;
    }

    .card-footer {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: 0.25rem;
      padding-top: 0.4rem;
      border-top: 1px solid var(--nexmemo-border-light);
      font-size: 0.65rem;
      color: var(--nexmemo-text-secondary);
      gap: 0.5rem;
    }

    .card-footer i {
      margin-right: 0.2rem;
      font-size: 0.6rem;
    }

    .card-creator {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      max-width: 100px;
    }

    .badge-info {
      background: var(--nexmemo-info-light);
      color: var(--nexmemo-info-dark);
    }

    @keyframes cardFadeIn {
      from { opacity: 0; transform: translateY(8px) scale(0.98); }
      to { opacity: 1; transform: translateY(0) scale(1); }
    }

    @keyframes fadeIn {
      from { opacity: 0; transform: translateY(8px); }
      to { opacity: 1; transform: translateY(0); }
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    @media (max-width: 768px) {
      .kanban-wrapper {
        padding: 1rem;
      }
      .kanban-col {
        flex: 0 0 260px;
        min-width: 240px;
      }
      .kanban-header h1 {
        font-size: 1.2rem;
      }
    }
  `
})
export class ApprovalKanbanComponent implements OnInit {
  private http = inject(HttpClient);
  private router = inject(Router);
  private toastr = inject(ToastrService);

  userId = 0;
  roleName = '';
  isLoading = true;
  isRefreshing = false;

  columns = [
    { key: 'draft', title: 'Draft', icon: 'fa-edit', gradient: 'linear-gradient(135deg, #4f46e5, #3b82f6)', memos: [] as any[], loading: true },
    { key: 'pending', title: 'Pending Approval', icon: 'fa-clock', gradient: 'linear-gradient(135deg, #f59e0b, #f97316)', memos: [] as any[], loading: true },
    { key: 'approved', title: 'Approved', icon: 'fa-check-circle', gradient: 'linear-gradient(135deg, #10b981, #059669)', memos: [] as any[], loading: true },
    { key: 'rejected', title: 'Rejected', icon: 'fa-times-circle', gradient: 'linear-gradient(135deg, #ef4444, #dc2626)', memos: [] as any[], loading: true },
  ];

  ngOnInit() {
    const storage = JSON.parse(localStorage.getItem('nexmemo-storage') || '{}');
    this.userId = storage.userId;
    this.roleName = storage.roleName;
    this.loadData();
  }

  loadData() {
    this.columns.forEach(c => c.loading = true);

    forkJoin({
      pending: this.http.get<any[]>(`${ApiConfig.URL}/api/v1/memos/pending-approvals`),
      my: this.http.get<any[]>(`${ApiConfig.URL}/api/v1/memos/my`),
      all: this.http.get<any[]>(`${ApiConfig.URL}/api/v1/memos`),
    }).subscribe({
      next: (res) => {
        this.columns[0].memos = (res.my || []).filter((m: any) => m.status === 'DRAFT');
        this.columns[1].memos = res.pending || [];
        this.columns[2].memos = (res.all || []).filter((m: any) => m.status === 'APPROVED');
        this.columns[3].memos = (res.all || []).filter((m: any) => m.status === 'REJECTED');
        this.columns.forEach(c => c.loading = false);
        this.isLoading = false;
        this.isRefreshing = false;
      },
      error: () => {
        this.toastr.error('Failed to load approval pipeline');
        this.columns.forEach(c => c.loading = false);
        this.isLoading = false;
        this.isRefreshing = false;
      }
    });
  }

  refreshData() {
    this.isRefreshing = true;
    this.loadData();
  }

  navigateToMemo() {
    this.router.navigate(['/layout/memo']);
  }

  formatAmount(amount: number | null | undefined): string {
    if (amount == null) return '—';
    return 'Rs ' + Number(amount).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  getCreatorName(memo: any): string {
    return memo.creator?.fullName || memo.creator?.name || memo.createdByName || 'Unknown';
  }

  priorityBadge(priority: string | undefined): string {
    switch ((priority || 'LOW').toUpperCase()) {
      case 'LOW': return 'badge-secondary';
      case 'MEDIUM': return 'badge-info';
      case 'HIGH': return 'badge-warning';
      case 'URGENT': return 'badge-danger';
      default: return 'badge-secondary';
    }
  }
}
