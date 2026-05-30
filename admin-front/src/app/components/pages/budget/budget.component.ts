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

@Component({
  selector: 'app-budget',
  standalone: true,
  imports: [NgForOf, NgIf, FormsModule, NgbModalModule, DecimalPipe],
  templateUrl: './budget.component.html',
  styleUrl: './budget.component.scss'
})
export class BudgetComponent implements OnInit {
  budgets: any[] = [];
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
    this.loadBudgets();
    this.fiscalYearService.getAllFiscalYears().subscribe({ next: (d) => this.fiscalYears = d });
    this.departmentService.getAllDepartments().subscribe({ next: (d) => this.departments = d });
    this.branchService.getAllBranches().subscribe({ next: (d) => this.branches = d });
  }

  loadBudgets() {
    this.budgetService.getAllBudgets().subscribe({
      next: (data) => this.budgets = data,
      error: () => this.toastr.error('Failed to load budgets')
    });
  }

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
        this.loadBudgets();
      },
      error: (err) => this.toastr.error(err.error?.message || 'Failed')
    });
  }

  deleteBudget(id: number) {
    if (confirm('Delete this budget allocation?')) {
      this.budgetService.deleteBudget(id).subscribe({
        next: () => { this.toastr.success('Budget deleted'); this.loadBudgets(); },
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
