import { Component, Input, Output, EventEmitter } from '@angular/core';
import { NgClass, NgFor, NgIf } from '@angular/common';

@Component({
  selector: 'app-pagination',
  standalone: true,
  imports: [NgClass, NgFor, NgIf],
  templateUrl: './pagination.component.html',
  styleUrl: './pagination.component.scss'
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
