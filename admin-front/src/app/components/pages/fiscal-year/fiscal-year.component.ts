import { Component, OnInit, inject } from '@angular/core';
import { NgForOf, NgIf, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { FiscalYearService } from '../../../shared-services/api-service/fiscal-year.service';
import { FinancialPeriodService } from '../../../shared-services/api-service/financial-period.service';
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

  // Phase 6c — financial period management (per fiscal year)
  periods: any[] = [];
  periodsLoading = false;
  periodsFiscalYear: any = null;
  periodForm: any = this.emptyPeriod();
  editingPeriodId: number | null = null;

  constructor(
    private fiscalYearService: FiscalYearService,
    private financialPeriodService: FinancialPeriodService,
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

  // ── Phase 6c — financial periods ──────────────────────────────────────────────

  emptyPeriod(): any {
    return { name: '', startDate: '', endDate: '', status: 'CLOSED' };
  }

  openPeriodsModal(content: any, fiscalYear: any) {
    this.periodsFiscalYear = fiscalYear;
    this.periods = [];
    this.editingPeriodId = null;
    this.periodForm = this.emptyPeriod();
    this.modalService.open(content, { size: 'xl', centered: true, scrollable: true });
    this.loadPeriods();
  }

  loadPeriods() {
    if (!this.periodsFiscalYear) return;
    this.periodsLoading = true;
    this.financialPeriodService.getByFiscalYear(this.periodsFiscalYear.id).subscribe({
      next: (p) => { this.periods = p || []; this.periodsLoading = false; },
      error: () => { this.periods = []; this.periodsLoading = false; this.toastr.error('Failed to load financial periods'); }
    });
  }

  editPeriod(period: any) {
    this.editingPeriodId = period.id;
    this.periodForm = {
      name: period.name,
      startDate: this.formatDate(period.startDate),
      endDate: this.formatDate(period.endDate),
      status: period.status
    };
  }

  cancelPeriodEdit() {
    this.editingPeriodId = null;
    this.periodForm = this.emptyPeriod();
  }

  savePeriod() {
    if (!this.periodForm.name || !this.periodForm.startDate || !this.periodForm.endDate) {
      this.toastr.warning('Name, start date and end date are required');
      return;
    }
    const payload = {
      name: this.periodForm.name,
      startDate: this.periodForm.startDate,
      endDate: this.periodForm.endDate,
      status: this.periodForm.status || 'CLOSED',
      fiscalYear: { id: this.periodsFiscalYear.id }
    };
    const obs = this.editingPeriodId
      ? this.financialPeriodService.update(this.editingPeriodId, payload)
      : this.financialPeriodService.create(payload);
    obs.subscribe({
      next: () => {
        this.toastr.success(this.editingPeriodId ? 'Period updated' : 'Period created');
        this.cancelPeriodEdit();
        this.loadPeriods();
      },
      error: (err) => this.toastr.error(err.error?.message || 'Failed to save period')
    });
  }

  openPeriod(period: any) {
    if (!confirm(`Open period "${period.name}"? Any currently open period will be closed.`)) return;
    this.financialPeriodService.open(period.id).subscribe({
      next: () => { this.toastr.success('Period opened'); this.loadPeriods(); },
      error: (err) => this.toastr.error(err.error?.message || 'Failed to open period')
    });
  }

  closePeriod(period: any) {
    if (!confirm(`Close period "${period.name}"?`)) return;
    this.financialPeriodService.close(period.id).subscribe({
      next: () => { this.toastr.success('Period closed'); this.loadPeriods(); },
      error: (err) => this.toastr.error(err.error?.message || 'Failed to close period')
    });
  }

  lockPeriod(period: any) {
    if (!confirm(`Lock period "${period.name}"? A locked period cannot be reopened.`)) return;
    this.financialPeriodService.lock(period.id).subscribe({
      next: () => { this.toastr.success('Period locked'); this.loadPeriods(); },
      error: (err) => this.toastr.error(err.error?.message || 'Failed to lock period')
    });
  }

  deletePeriod(period: any) {
    if (!confirm(`Delete period "${period.name}"?`)) return;
    this.financialPeriodService.delete(period.id).subscribe({
      next: () => { this.toastr.success('Period deleted'); this.loadPeriods(); },
      error: (err) => this.toastr.error(err.error?.message || 'Failed to delete period')
    });
  }

  periodBadge(status: string): string {
    switch (status) {
      case 'OPEN': return 'badge-success';
      case 'CLOSED': return 'badge-secondary';
      case 'LOCKED': return 'badge-dark';
      default: return 'badge-info';
    }
  }
}
