import { Component, OnInit, inject } from '@angular/core';
import { NgForOf, NgIf, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { BudgetService } from '../../../shared-services/api-service/budget.service';
import { FiscalYearService } from '../../../shared-services/api-service/fiscal-year.service';
import { DepartmentService } from '../../../shared-services/api-service/department.service';
import { BranchService } from '../../../shared-services/api-service/branch.service';
import { ToastrService } from 'ngx-toastr';
import { LocalStorageUtil } from '../../../@core/utils/local-storage-util';
import { AdvancedTableComponent, TableColumn } from '../../../shared-components/advanced-table/advanced-table.component';

@Component({
  selector: 'app-budget',
  standalone: true,
  imports: [NgForOf, NgIf, FormsModule, NgbModalModule, DecimalPipe, AdvancedTableComponent],
  templateUrl: './budget.component.html',
  styleUrl: './budget.component.scss'
})
export class BudgetComponent implements OnInit {
  columns: TableColumn[] = [
    { key: 'id', label: 'ID', sortable: true, width: '80px' },
    { key: 'fiscalYearName', label: 'Fiscal Year', sortable: true, filterable: true, filterType: 'text' },
    { key: 'departmentName', label: 'Department', sortable: true, filterable: true, filterType: 'text' },
    { key: 'branchName', label: 'Branch', sortable: true, filterable: true, filterType: 'text' },
    { key: 'allocatedAmount', label: 'Allocated', sortable: true, type: 'number' },
    { key: 'utilizedAmount', label: 'Utilized', sortable: true, type: 'number' },
    { key: 'remainingAmount', label: 'Remaining', sortable: false, type: 'number' }
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
  fiscalYears: any[] = [];
  departments: any[] = [];
  branches: any[] = [];
  selectedBudget: any = { fiscalYearId: null, departmentId: null, branchId: null, allocatedAmount: 0, description: '' };
  isEditing = false;
  isAdmin = false;
  private modalService = inject(NgbModal);

  constructor(
    private budgetService: BudgetService,
    private fiscalYearService: FiscalYearService,
    private departmentService: DepartmentService,
    private branchService: BranchService,
    private toastr: ToastrService
  ) {}

  ngOnInit() {
    const storage = LocalStorageUtil.getStorage();
    this.isAdmin = storage.roleName === 'ADMIN' || storage.roleName === 'SUPER_ADMIN';
    this.loadData();
    this.fiscalYearService.getAllFiscalYears().subscribe({ next: (d) => this.fiscalYears = d });
    this.departmentService.getAllDepartments().subscribe({ next: (d) => this.departments = d });
    this.branchService.getAllBranches().subscribe({ next: (d) => this.branches = d });
  }

  loadData() {
    this.loading = true;
    const filterObj: any = {};
    this.filters.forEach((v, k) => { if (v) filterObj[k] = v; });
    this.budgetService.getPaginated(this.searchQuery, this.pageNumber, this.pageSize, this.sortBy, this.sortDir, filterObj).subscribe({
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
    this.selectedBudget = { fiscalYearId: null, departmentId: null, branchId: null, allocatedAmount: 0, description: '' };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  openEditModal(content: any, budget: any) {
    this.isEditing = true;
    this.selectedBudget = {
      ...budget,
      fiscalYearId: budget.fiscalYearId,
      departmentId: budget.departmentId,
      branchId: budget.branchId
    };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  saveBudget() {
    const obs = this.isEditing
      ? this.budgetService.updateBudget(this.selectedBudget.id, this.selectedBudget)
      : this.budgetService.createBudget(this.selectedBudget);
    obs.subscribe({
      next: () => {
        this.toastr.success(this.isEditing ? 'Budget updated' : 'Budget created');
        this.modalService.dismissAll();
        this.loadData();
      },
      error: (err) => this.toastr.error(err.error?.message || 'Failed')
    });
  }

  deleteBudget(id: number) {
    if (confirm('Delete this budget allocation?')) {
      this.budgetService.deleteBudget(id).subscribe({
        next: () => { this.toastr.success('Budget deleted'); this.loadData(); },
        error: () => this.toastr.error('Delete failed')
      });
    }
  }

  getUtilized(budget: any): number {
    return budget.utilizedAmount || budget.usedAmount || 0;
  }

  getRemaining(budget: any): number {
    return (budget.allocatedAmount || 0) - this.getUtilized(budget);
  }
}
