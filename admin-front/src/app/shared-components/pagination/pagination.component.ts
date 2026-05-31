import { Component, Input, Output, EventEmitter } from '@angular/core';
import { NgClass, NgFor, NgIf } from '@angular/common';

@Component({
  selector: 'app-pagination',
  standalone: true,
  imports: [NgClass, NgFor, NgIf],
  template: `
    <div class="pagination-bar" *ngIf="totalPages > 1">
      <div class="pagination-info">
        Showing <strong>{{ from }}-{{ to }}</strong> of <strong>{{ totalElements }}</strong>
      </div>
      <div class="pagination-controls">
        <button class="page-btn" [disabled]="isFirstPage" (click)="goToPage(0)" title="First">
          <i class="fas fa-angle-double-left"></i>
        </button>
        <button class="page-btn" [disabled]="isFirstPage" (click)="goToPage(currentPage - 1)" title="Previous">
          <i class="fas fa-chevron-left"></i>
        </button>

        <button *ngFor="let p of visiblePages"
                class="page-num"
                [class.active]="p === currentPage"
                (click)="goToPage(p)">
          {{ p + 1 }}
        </button>

        <button class="page-btn" [disabled]="isLastPage" (click)="goToPage(currentPage + 1)" title="Next">
          <i class="fas fa-chevron-right"></i>
        </button>
        <button class="page-btn" [disabled]="isLastPage" (click)="goToPage(totalPages - 1)" title="Last">
          <i class="fas fa-angle-double-right"></i>
        </button>
      </div>
      <div class="pagination-size">
        <select (change)="onPageSizeChange($event)" [value]="pageSize">
          <option *ngFor="let s of pageSizeOptions" [value]="s">{{ s }}</option>
        </select>
      </div>
    </div>
  `,
  styles: [`
    .pagination-bar { display: flex; align-items: center; justify-content: space-between; padding: 0.75rem 1rem; border-top: 1px solid var(--nexmemo-border); gap: 1rem; flex-wrap: wrap; }
    .pagination-info { font-size: 0.8rem; color: var(--nexmemo-text-secondary); white-space: nowrap; }
    .pagination-controls { display: flex; align-items: center; gap: 2px; }
    .page-btn, .page-num { min-width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; border: 1px solid transparent; border-radius: var(--radius-sm); background: transparent; color: var(--nexmemo-text-secondary); font-size: 0.78rem; cursor: pointer; transition: all 0.15s ease; i { font-size: 0.7rem; } }
    .page-btn:hover:not(:disabled), .page-num:hover:not(.active) { background: var(--nexmemo-bg); color: var(--nexmemo-text); border-color: var(--nexmemo-border); }
    .page-btn:disabled { opacity: 0.35; cursor: default; }
    .page-num.active { background: var(--nexmemo-primary); color: white; font-weight: 600; box-shadow: 0 2px 8px rgba(79,70,229,0.25); }
    .pagination-size select { padding: 0.3rem 0.6rem; font-size: 0.78rem; border: 1px solid var(--nexmemo-border); border-radius: var(--radius-sm); background: var(--nexmemo-surface); color: var(--nexmemo-text-secondary); cursor: pointer; outline: none; &:focus { border-color: var(--nexmemo-primary); } }
  `]
})
export class PaginationComponent {
  @Input() currentPage = 0;
  @Input() pageSize = 20;
  @Input() totalElements = 0;
  @Input() totalPages = 0;
  @Input() pageSizeOptions: number[] = [10, 20, 50, 100];

  @Output() pageChange = new EventEmitter<{ page: number; size: number }>();

  get from(): number { return this.totalElements === 0 ? 0 : this.currentPage * this.pageSize + 1; }
  get to(): number { return Math.min((this.currentPage + 1) * this.pageSize, this.totalElements); }
  get isFirstPage(): boolean { return this.currentPage === 0 || this.totalPages <= 1; }
  get isLastPage(): boolean { return this.currentPage >= this.totalPages - 1 || this.totalPages <= 1; }

  get visiblePages(): number[] {
    const pages: number[] = [];
    const total = this.totalPages;
    const current = this.currentPage;
    let start = Math.max(0, current - 2);
    let end = Math.min(total - 1, current + 2);
    if (end - start < 4) {
      if (start === 0) end = Math.min(total - 1, start + 4);
      else start = Math.max(0, end - 4);
    }
    for (let i = start; i <= end; i++) pages.push(i);
    return pages;
  }

  goToPage(page: number) {
    if (page < 0 || page >= this.totalPages || page === this.currentPage) return;
    this.pageChange.emit({ page, size: this.pageSize });
  }

  onPageSizeChange(event: any) {
    const size = parseInt(event.target.value, 10);
    this.pageChange.emit({ page: 0, size });
  }
}
