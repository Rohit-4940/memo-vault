import { Component, OnInit, inject } from '@angular/core';
import { NgForOf, NgIf, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { FiscalYearService } from '../../../shared-services/api-service/fiscal-year.service';
import { ToastrService } from 'ngx-toastr';
import { LocalStorageUtil } from '../../../@core/utils/local-storage-util';
import { AdvancedTableComponent, TableColumn } from '../../../shared-components/advanced-table/advanced-table.component';

@Component({
  selector: 'app-fiscal-year',
  standalone: true,
  imports: [NgForOf, NgIf, FormsModule, NgbModalModule, DatePipe, AdvancedTableComponent],
  templateUrl: './fiscal-year.component.html',
  styleUrl: './fiscal-year.component.scss'
})
export class FiscalYearComponent implements OnInit {
  columns: TableColumn[] = [
    { key: 'id', label: 'ID', sortable: true, width: '80px' },
    { key: 'name', label: 'Name', sortable: true, filterable: true, filterType: 'text' },
    { key: 'startDate', label: 'Start Date', sortable: true, type: 'date' },
    { key: 'endDate', label: 'End Date', sortable: true, type: 'date' },
    { key: 'isActive', label: 'Active', sortable: true, filterable: true, filterType: 'boolean' },
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
  selectedFiscalYear: any = { name: '', startDate: '', endDate: '', isActive: false };
  isEditing = false;
  isAdmin = false;
  private modalService = inject(NgbModal);

  constructor(
    private fiscalYearService: FiscalYearService,
    private toastr: ToastrService
  ) {}

  ngOnInit() {
    const storage = LocalStorageUtil.getStorage();
    this.isAdmin = storage.roleName === 'ADMIN' || storage.roleName === 'SUPER_ADMIN';
    this.loadData();
  }

  loadData() {
    this.loading = true;
    const filterObj: any = {};
    this.filters.forEach((v, k) => { if (v) filterObj[k] = v; });
    this.fiscalYearService.getPaginated(this.searchQuery, this.pageNumber, this.pageSize, this.sortBy, this.sortDir, filterObj).subscribe({
      next: (res) => {
        this.data = res.content || [];
        this.totalElements = res.totalElements || 0;
        this.totalPages = res.totalPages || 1;
        this.loading = false;
      },
      error: () => { this.toastr.error('Failed to load'); this.loading = false; }
    });
  }

  onSearch(value: string) { this.searchQuery = value; this.pageNumber = 1; this.loadData(); }
  onPageChange(page: number) { this.pageNumber = page; this.loadData(); }
  onSizeChange(size: number) { this.pageSize = size; this.pageNumber = 1; this.loadData(); }
  onSortChange(sort: any) { this.sortBy = sort.sortBy; this.sortDir = sort.sortDir; this.loadData(); }
  onFilterChange(filters: Map<string, string>) { this.filters = filters; this.pageNumber = 1; this.loadData(); }

  openCreateModal(content: any) {
    this.isEditing = false;
    this.selectedFiscalYear = { name: '', startDate: '', endDate: '', isActive: false };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  openEditModal(content: any, fiscalYear: any) {
    this.isEditing = true;
    this.selectedFiscalYear = { ...fiscalYear, startDate: this.formatDate(fiscalYear.startDate), endDate: this.formatDate(fiscalYear.endDate) };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  private formatDate(date: any): string {
    if (!date) return '';
    const d = new Date(date);
    return d.toISOString().split('T')[0];
  }

  saveFiscalYear() {
    const payload = { ...this.selectedFiscalYear };
    const obs = this.isEditing
      ? this.fiscalYearService.updateFiscalYear(payload.id, payload)
      : this.fiscalYearService.createFiscalYear(payload);
    obs.subscribe({
      next: () => {
        this.toastr.success(this.isEditing ? 'Fiscal year updated' : 'Fiscal year created');
        this.modalService.dismissAll();
        this.loadData();
      },
      error: (err) => this.toastr.error(err.error?.message || 'Failed')
    });
  }

  setActive(fiscalYear: any) {
    if (confirm(`Set "${fiscalYear.name}" as active fiscal year?`)) {
      this.fiscalYearService.setActiveFiscalYear(fiscalYear.id).subscribe({
        next: () => { this.toastr.success('Active fiscal year updated'); this.loadData(); },
        error: () => this.toastr.error('Failed to set active fiscal year')
      });
    }
  }

  deleteFiscalYear(id: number) {
    if (confirm('Delete this fiscal year?')) {
      this.fiscalYearService.deleteFiscalYear(id).subscribe({
        next: () => { this.toastr.success('Fiscal year deleted'); this.loadData(); },
        error: () => this.toastr.error('Delete failed')
      });
    }
  }
}
