import { Component, OnInit, inject } from '@angular/core';
import { NgFor, NgIf, NgClass, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { HttpClient } from '@angular/common/http';
import { ApiConfig } from '../../../@core/utils/api/ApiConfig';
import { LocalStorageUtil } from '../../../@core/utils/local-storage-util';
import { ToastrService } from 'ngx-toastr';
import { AdvancedTableComponent, TableColumn } from '../../../shared-components/advanced-table/advanced-table.component';

@Component({
  selector: 'app-expense-category',
  standalone: true,
  imports: [NgFor, NgIf, NgClass, FormsModule, NgbModalModule, DecimalPipe, AdvancedTableComponent],
  template: `
<div class="page-container animate-fade-in">
  <div class="page-header">
    <div>
      <h1><i class="fas fa-tags"></i> Expense Categories</h1>
      <div class="page-subtitle">Configure banking expense categories for memo templates</div>
    </div>
    <div class="header-actions">
      <button class="btn btn-gradient" (click)="openModal(content)"><i class="fas fa-plus"></i> New Category</button>
    </div>
  </div>

  <div class="card">
    <div class="card-body p-0">
      <app-advanced-table
        [columns]="columns"
        [data]="data"
        [totalElements]="totalElements"
        [loading]="loading"
        [pageNumber]="pageNumber"
        [pageSize]="pageSize"
        [totalPages]="totalPages"
        [searchable]="true"
        [showFilters]="true"
        searchPlaceholder="Search categories..."
        (searchChange)="onSearch($event)"
        (pageChange)="onPageChange($event)"
        (sizeChange)="onSizeChange($event)"
        (sortChange)="onSortChange($event)"
        (filterChange)="onFilterChange($event)"
      >
        <ng-template #actions let-row>
          <button class="btn btn-sm btn-ghost" (click)="editModal(editContent, row); $event.stopPropagation()"><i class="fas fa-edit"></i></button>
          <button class="btn btn-sm btn-ghost text-danger" (click)="deleteCategory(row.id); $event.stopPropagation()"><i class="fas fa-trash"></i></button>
        </ng-template>
      </app-advanced-table>
    </div>
  </div>
</div>

<ng-template #content let-modal>
  <div class="modal-header"><h5 class="modal-title">Create Expense Category</h5><button type="button" class="btn-close" (click)="modal.dismiss()"></button></div>
  <div class="modal-body">
    <div class="row">
      <div class="col-md-6">
        <div class="form-group">
          <label>Category Name *</label>
          <input class="form-control" [(ngModel)]="form.categoryName" placeholder="e.g., Travel Fund" />
        </div>
      </div>
      <div class="col-md-6">
        <div class="form-group">
          <label>Category Code *</label>
          <input class="form-control" [(ngModel)]="form.categoryCode" placeholder="e.g., TRAVEL" />
        </div>
      </div>
    </div>
    <div class="form-group">
      <label>Description</label>
      <textarea class="form-control" [(ngModel)]="form.description" rows="2"></textarea>
    </div>
    <div class="row">
      <div class="col-md-6">
        <div class="form-group">
          <label>Parent Category</label>
          <select class="form-control" [(ngModel)]="form.parentCategoryId">
            <option [value]="null">— None (Top Level) —</option>
            @for (cat of data; track cat.id) {
              <option [value]="cat.id">{{ cat.categoryName }}</option>
            }
          </select>
        </div>
      </div>
      <div class="col-md-6">
        <div class="form-group">
          <label>Budget Code Pattern</label>
          <input class="form-control" [(ngModel)]="form.budgetCodePattern" placeholder="e.g., TRV-{year}-{dept}" />
        </div>
      </div>
    </div>
    <div class="row">
      <div class="col-md-6">
        <div class="form-group">
          <label>Approval Levels Required</label>
          <input type="number" class="form-control" [(ngModel)]="form.approvalLevels" min="1" />
        </div>
      </div>
      <div class="col-md-6">
        <div class="form-group">
          <label>Max Amount (NPR)</label>
          <input type="number" class="form-control" [(ngModel)]="form.maxAmount" />
        </div>
      </div>
    </div>
  </div>
  <div class="modal-footer">
    <button class="btn btn-secondary" (click)="modal.dismiss()">Cancel</button>
    <button class="btn btn-gradient" (click)="createCategory(); modal.close()">Create</button>
  </div>
</ng-template>

<ng-template #editContent let-modal>
  <div class="modal-header"><h5 class="modal-title">Edit Expense Category</h5><button type="button" class="btn-close" (click)="modal.dismiss()"></button></div>
  <div class="modal-body">
    <div class="row">
      <div class="col-md-6">
        <div class="form-group">
          <label>Category Name *</label>
          <input class="form-control" [(ngModel)]="editForm.categoryName" />
        </div>
      </div>
      <div class="col-md-6">
        <div class="form-group">
          <label>Category Code *</label>
          <input class="form-control" [(ngModel)]="editForm.categoryCode" />
        </div>
      </div>
    </div>
    <div class="form-group">
      <label>Description</label>
      <textarea class="form-control" [(ngModel)]="editForm.description" rows="2"></textarea>
    </div>
    <div class="row">
      <div class="col-md-6">
        <div class="form-group">
          <label>Approval Levels</label>
          <input type="number" class="form-control" [(ngModel)]="editForm.approvalLevels" min="1" />
        </div>
      </div>
      <div class="col-md-6">
        <div class="form-group">
          <label>Max Amount</label>
          <input type="number" class="form-control" [(ngModel)]="editForm.maxAmount" />
        </div>
      </div>
    </div>
    <div class="form-check">
      <input type="checkbox" class="form-check-input" id="requiresApproval" [(ngModel)]="editForm.requiresApproval" />
      <label class="form-check-label" for="requiresApproval">Requires Approval</label>
    </div>
  </div>
  <div class="modal-footer">
    <button class="btn btn-secondary" (click)="modal.dismiss()">Cancel</button>
    <button class="btn btn-gradient" (click)="updateCategory(); modal.close()">Save</button>
  </div>
</ng-template>
  `,
  styles: [`
    .form-group { margin-bottom: 0.75rem; }
    .form-group label { display: block; margin-bottom: 0.3rem; font-size: 0.82rem; font-weight: 500; color: var(--nexmemo-text-secondary); }
    .modal-body { padding: 1.25rem; }
    .modal-footer { padding: 0.75rem 1.25rem; }
    .form-control { font-size: 0.88rem; padding: 0.4rem 0.65rem; }
  `]
})
export class ExpenseCategoryComponent implements OnInit {
  columns: TableColumn[] = [
    { key: 'categoryCode', label: 'Code', sortable: true, filterable: true, filterType: 'text', width: '100px' },
    { key: 'categoryName', label: 'Name', sortable: true, filterable: true, filterType: 'text' },
    { key: 'parentCategory.categoryName', label: 'Parent', sortable: false },
    { key: 'approvalLevels', label: 'Approval Levels', sortable: true, type: 'number', width: '140px' },
    { key: 'maxAmount', label: 'Max Amount', sortable: true, type: 'currency' },
    { key: 'status', label: 'Status', sortable: true, filterable: true, filterType: 'select', filterOptions: [{label:'Active',value:'ACTIVE'},{label:'Inactive',value:'INACTIVE'}] }
  ];
  data: any[] = [];
  loading = false;
  pageNumber = 1;
  pageSize = 20;
  totalElements = 0;
  totalPages = 1;
  sortBy = 'id';
  sortDir: 'asc' | 'desc' = 'desc';
  searchQuery = '';
  filters = new Map<string, string>();
  form: any = { categoryName: '', categoryCode: '', description: '', parentCategoryId: null, approvalLevels: 1, maxAmount: null, budgetCodePattern: '', requiresApproval: true };
  editForm: any = {};
  isAdmin = false;
  private http = inject(HttpClient);
  private modalService = inject(NgbModal);
  private toastr = inject(ToastrService);

  ngOnInit() {
    const storage = LocalStorageUtil.getStorage();
    this.isAdmin = storage.roleName === 'ADMIN' || storage.roleName === 'SUPER_ADMIN';
    this.loadCategories();
  }

  loadCategories() {
    this.loading = true;
    const filterObj: any = {};
    this.filters.forEach((v, k) => { if (v) filterObj[k] = v; });
    this.http.get<any>(`${ApiConfig.URL}/api/v1/expense-categories/paginated?page=${this.pageNumber - 1}&size=${this.pageSize}&sortBy=${this.sortBy}&sortDir=${this.sortDir}${this.searchQuery ? '&search=' + encodeURIComponent(this.searchQuery) : ''}${Object.keys(filterObj).length ? '&' + Object.keys(filterObj).map(k => k + '=' + encodeURIComponent(filterObj[k])).join('&') : ''}`).subscribe({
      next: (res) => {
        this.data = res.content || [];
        this.totalElements = res.totalElements || 0;
        this.totalPages = res.totalPages || 1;
        this.loading = false;
      },
      error: () => { this.toastr.error('Failed to load categories'); this.loading = false; }
    });
  }

  onSearch(value: string) {
    this.searchQuery = value;
    this.pageNumber = 1;
    this.loadCategories();
  }

  onPageChange(page: number) {
    this.pageNumber = page;
    this.loadCategories();
  }

  onSizeChange(size: number) {
    this.pageSize = size;
    this.pageNumber = 1;
    this.loadCategories();
  }

  onSortChange(sort: any) {
    this.sortBy = sort.sortBy;
    this.sortDir = sort.sortDir;
    this.loadCategories();
  }

  onFilterChange(filters: Map<string, string>) {
    this.filters = filters;
    this.pageNumber = 1;
    this.loadCategories();
  }

  openModal(content: any) {
    this.form = { categoryName: '', categoryCode: '', description: '', parentCategoryId: null, approvalLevels: 1, maxAmount: null, budgetCodePattern: '', requiresApproval: true };
    this.modalService.open(content, { size: 'lg', centered: true, scrollable: true });
  }

  editModal(content: any, cat: any) {
    this.editForm = { ...cat, parentCategoryId: cat.parentCategory?.id || null };
    this.modalService.open(content, { size: 'lg', centered: true, scrollable: true });
  }

  createCategory() {
    const payload = {
      categoryName: this.form.categoryName,
      categoryCode: this.form.categoryCode,
      description: this.form.description,
      parentCategory: this.form.parentCategoryId ? { id: this.form.parentCategoryId } : null,
      approvalLevels: this.form.approvalLevels,
      maxAmount: this.form.maxAmount,
      budgetCodePattern: this.form.budgetCodePattern,
      requiresApproval: this.form.requiresApproval
    };
    this.http.post(`${ApiConfig.URL}/api/v1/expense-categories`, payload).subscribe({
      next: () => { this.toastr.success('Category created'); this.loadCategories(); },
      error: (err) => this.toastr.error(err.error?.message || 'Failed')
    });
  }

  updateCategory() {
    const payload = { ...this.editForm, parentCategory: this.editForm.parentCategoryId ? { id: this.editForm.parentCategoryId } : null };
    this.http.put(`${ApiConfig.URL}/api/v1/expense-categories/${this.editForm.id}`, payload).subscribe({
      next: () => { this.toastr.success('Category updated'); this.loadCategories(); },
      error: (err) => this.toastr.error(err.error?.message || 'Failed')
    });
  }

  deleteCategory(id: number) {
    if (!confirm('Delete this expense category?')) return;
    this.http.delete(`${ApiConfig.URL}/api/v1/expense-categories/${id}`).subscribe({
      next: () => { this.toastr.success('Category deleted'); this.loadCategories(); },
      error: () => this.toastr.error('Failed to delete')
    });
  }
}
