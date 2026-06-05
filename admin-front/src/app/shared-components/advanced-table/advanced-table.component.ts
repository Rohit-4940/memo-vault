import { Component, Input, Output, EventEmitter, ContentChild, TemplateRef, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { TranslatePipe } from '@ngx-translate/core';

export interface TableColumn {
  key: string;
  label: string;
  sortable?: boolean;
  filterable?: boolean;
  filterType?: 'text' | 'select' | 'date' | 'boolean' | 'number';
  filterOptions?: { label: string; value: any }[];
  type?: 'text' | 'number' | 'date' | 'boolean' | 'enum' | 'currency';
  width?: string;
  sortKey?: string;
  hidden?: boolean;
  pinRight?: boolean;
}

export interface SortState {
  sortBy: string;
  sortDir: 'asc' | 'desc';
}

@Component({
  selector: 'app-advanced-table',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './advanced-table.component.html',
  styleUrl: './advanced-table.component.scss'
})
export class AdvancedTableComponent implements OnInit, OnDestroy {
  @Input() columns: TableColumn[] = [];
  @Input() data: any[] = [];
  @Input() totalElements = 0;
  @Input() loading = false;
  @Input() pageSize = 20;
  @Input() pageNumber = 1;
  @Input() totalPages = 1;
  @Input() pageSizeOptions: number[] = [10, 20, 50, 100];
  @Input() searchable = true;
  @Input() searchPlaceholder = 'table.search.placeholder';
  @Input() showPageSize = true;
  @Input() showInfo = true;
  @Input() emptyMessage = 'table.empty';
  @Input() loadingMessage = 'table.loading';
  @Input() selectable = false;
  @Input() selectedIds: number[] = [];
  @Input() striped = true;
  @Input() hover = true;
  @Input() small = false;
  @Input() bordered = false;
  @Input() showFilters = true;
  @Input() filterMode: 'inline' | 'panel' = 'inline';
  @Input() serverSide = true;
  @Input() exportable = false;

  @Output() pageChange = new EventEmitter<number>();
  @Output() sizeChange = new EventEmitter<number>();
  @Output() sortChange = new EventEmitter<SortState>();
  @Output() searchChange = new EventEmitter<string>();
  @Output() filterChange = new EventEmitter<Map<string, string>>();
  @Output() rowClick = new EventEmitter<any>();
  @Output() selectionChange = new EventEmitter<number[]>();
  @Output() export = new EventEmitter<void>();

  @ContentChild('actions') actionsTemplate?: TemplateRef<any>;

  @ContentChild('customCell') customCellTemplate?: TemplateRef<any>;

  sortBy = 'id';
  sortDir: 'asc' | 'desc' = 'desc';
  searchQuery = '';
  filters: { [key: string]: string } = {};
  selectedIdsSet = new Set<number>();
  filterPanelVisible = false;
  panelFilters: { [key: string]: string } = {};

  private searchSubject = new Subject<string>();
  private filterSubject = new Subject<{ key: string; value: string }>();

  ngOnInit() {
    this.searchSubject.pipe(debounceTime(300), distinctUntilChanged()).subscribe(q => {
      this.searchChange.emit(q);
    });
    this.filterSubject.pipe(debounceTime(400)).subscribe(f => {
      this.filters[f.key] = f.value;
      const map = new Map(Object.entries(this.filters));
      this.filterChange.emit(map);
    });
  }

  ngOnDestroy() {
    this.searchSubject.complete();
    this.filterSubject.complete();
  }

  onSearchInput(value: string) {
    this.searchSubject.next(value);
  }

  onSort(column: TableColumn) {
    if (!column.sortable) return;
    const sortKey = column.sortKey || column.key;
    if (this.sortBy === sortKey) {
      this.sortDir = this.sortDir === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortBy = sortKey;
      this.sortDir = 'asc';
    }
    this.sortChange.emit({ sortBy: this.sortBy, sortDir: this.sortDir });
  }

  getSortIcon(column: TableColumn): string {
    const key = column.sortKey || column.key;
    if (this.sortBy !== key) return 'fa-sort';
    return this.sortDir === 'asc' ? 'fa-sort-up' : 'fa-sort-down';
  }

  onFilterInput(key: string, value: string) {
    this.filterSubject.next({ key, value });
  }

  toggleFilterPanel() {
    this.filterPanelVisible = !this.filterPanelVisible;
  }

  onSearch() {
    const map = new Map<string, string>();
    Object.entries(this.panelFilters).forEach(([k, v]) => { if (v) map.set(k, v); });
    this.filters = { ...this.panelFilters };
    this.filterChange.emit(map);
  }

  onClearFilters() {
    this.panelFilters = {};
    this.filters = {};
    this.filterChange.emit(new Map());
  }

  goToPage(page: number) {
    if (page < 1 || page > this.totalPages || page === this.pageNumber) return;
    this.pageChange.emit(page);
  }

  onPageSizeChange(size: number) {
    this.sizeChange.emit(size);
  }

  toggleSelect(id: number) {
    if (this.selectedIdsSet.has(id)) {
      this.selectedIdsSet.delete(id);
    } else {
      this.selectedIdsSet.add(id);
    }
    this.selectionChange.emit(Array.from(this.selectedIdsSet));
  }

  toggleSelectAll() {
    if (this.selectedIdsSet.size === this.data.length) {
      this.selectedIdsSet.clear();
    } else {
      this.data.forEach(item => this.selectedIdsSet.add(item.id));
    }
    this.selectionChange.emit(Array.from(this.selectedIdsSet));
  }

  isSelected(id: number): boolean {
    return this.selectedIdsSet.has(id);
  }

  isAllSelected(): boolean {
    return this.data.length > 0 && this.selectedIdsSet.size === this.data.length;
  }

  get startIndex(): number {
    return this.totalElements === 0 ? 0 : (this.pageNumber - 1) * this.pageSize + 1;
  }

  get endIndex(): number {
    return Math.min(this.pageNumber * this.pageSize, this.totalElements);
  }

  get visibleColumns(): TableColumn[] {
    return this.columns.filter(c => !c.hidden);
  }

  get filterableColumns(): TableColumn[] {
    return this.visibleColumns.filter(c => c.filterable);
  }

  getCellValue(row: any, column: TableColumn): any {
    const value = this.getNestedValue(row, column.key);
    if (value == null) return '—';
    switch (column.type) {
      case 'date': return value;
      case 'currency': return value;
      case 'boolean': return value ? 'Yes' : 'No';
      default: return value;
    }
  }

  getNestedValue(obj: any, path: string): any {
    return path.split('.').reduce((o, k) => (o || {})[k], obj);
  }

  get visiblePages(): number[] {
    const pages: number[] = [];
    const total = this.totalPages;
    const current = this.pageNumber;
    let start = Math.max(1, current - 2);
    let end = Math.min(total, current + 2);
    if (end - start < 4) {
      if (start === 1) end = Math.min(total, start + 4);
      else start = Math.max(1, end - 4);
    }
    for (let i = start; i <= end; i++) pages.push(i);
    return pages;
  }

  onRowClick(row: any) {
    this.rowClick.emit(row);
  }

  trackByFn(index: number, item: any): any {
    return item?.id || index;
  }
}
