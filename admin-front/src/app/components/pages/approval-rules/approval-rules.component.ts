import { Component, OnInit, inject } from '@angular/core';
import { NgFor, NgIf, NgClass, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { HttpClient } from '@angular/common/http';
import { ApiConfig } from '../../../@core/utils/api/ApiConfig';
import { LocalStorageUtil } from '../../../@core/utils/local-storage-util';
import { ToastrService } from 'ngx-toastr';

@Component({
  selector: 'app-approval-rules',
  standalone: true,
  imports: [NgFor, NgIf, NgClass, FormsModule, NgbModalModule, DecimalPipe],
  template: `
<div class="page-container animate-fade-in">
  <div class="page-header">
    <div>
      <h1><i class="fas fa-check-double"></i> Approval Rules</h1>
      <div class="page-subtitle">Configure multi-level approval workflows for banking transactions</div>
    </div>
    <div class="header-actions">
      <button class="btn btn-gradient" (click)="openCreateModal(content)"><i class="fas fa-plus"></i> New Approval Rule</button>
    </div>
  </div>

  <div class="card">
    <div class="card-body p-0">
      <table class="table">
        <thead>
          <tr>
            <th>Rule Name</th>
            <th>Amount Range</th>
            <th>Expense Category</th>
            <th>Approval Levels</th>
            <th class="text-center">Auto-Approve</th>
            <th class="text-center">Committee</th>
            <th>Priority</th>
            <th>Status</th>
            <th class="text-center">Actions</th>
          </tr>
        </thead>
        <tbody>
          @for (rule of rules; track rule.id) {
            <tr>
              <td class="font-medium">{{ rule.ruleName }}</td>
              <td>
                <span class="amount-range">
                  <span class="amount">NPR {{ rule.minAmount | number }}</span>
                  <i class="fas fa-arrow-right amount-arrow"></i>
                  <span class="amount">NPR {{ rule.maxAmount | number }}</span>
                </span>
              </td>
              <td>
                @if (rule.expenseCategory) {
                  <span class="badge badge-info">{{ rule.expenseCategory.categoryName }}</span>
                } @else {
                  <span class="text-muted">All Categories</span>
                }
              </td>
              <td><span class="badge badge-primary">{{ rule.approvalLevelsRequired }}</span></td>
              <td class="text-center">
                @if (rule.autoApprove) {
                  <i class="fas fa-check-circle text-success check-icon"></i>
                }
              </td>
              <td class="text-center">
                @if (rule.committeeRequired) {
                  <span class="badge badge-warning">Committee</span>
                }
              </td>
              <td><span class="priority-badge">{{ rule.priorityOrder }}</span></td>
              <td>
                <span class="badge" [class.badge-success]="rule.status === 'ACTIVE'" [class.badge-secondary]="rule.status !== 'ACTIVE'">{{ rule.status }}</span>
              </td>
              <td class="text-center">
                <button class="btn btn-sm btn-ghost" (click)="openEditModal(editContent, rule)"><i class="fas fa-edit"></i></button>
                <button class="btn btn-sm btn-ghost text-danger" (click)="deleteRule(rule.id)"><i class="fas fa-trash"></i></button>
              </td>
            </tr>
          } @empty {
            <tr>
              <td colspan="9">
                <div class="empty-state">
                  <i class="fas fa-check-double"></i>
                  <p>No approval rules configured. Create one to define multi-level approval workflows.</p>
                </div>
              </td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  </div>
</div>

<ng-template #content let-modal>
  <div class="modal-header">
    <h5 class="modal-title"><i class="fas fa-plus-circle text-primary me-2"></i>Create Approval Rule</h5>
    <button type="button" class="btn-close" (click)="modal.dismiss()"></button>
  </div>
  <div class="modal-body">
    <div class="form-group">
      <label>Rule Name *</label>
      <input class="form-control" [(ngModel)]="form.ruleName" placeholder="e.g., High Value Transfer" />
    </div>
    <div class="row">
      <div class="col-md-6">
        <div class="form-group">
          <label>Min Amount (NPR)</label>
          <input type="number" class="form-control" [(ngModel)]="form.minAmount" min="0" />
        </div>
      </div>
      <div class="col-md-6">
        <div class="form-group">
          <label>Max Amount (NPR)</label>
          <input type="number" class="form-control" [(ngModel)]="form.maxAmount" min="0" />
        </div>
      </div>
    </div>
    <div class="form-group">
      <label>Expense Category</label>
      <select class="form-control" [(ngModel)]="form.expenseCategoryId">
        <option [ngValue]="null">All Categories</option>
        @for (cat of categories; track cat.id) {
          <option [ngValue]="cat.id">{{ cat.categoryName }}</option>
        }
      </select>
    </div>
    <div class="row">
      <div class="col-md-6">
        <div class="form-group">
          <label>Department</label>
          <select class="form-control" [(ngModel)]="form.departmentId">
            <option [ngValue]="null">All Departments</option>
            @for (dept of departments; track dept.id) {
              <option [ngValue]="dept.id">{{ dept.name }}</option>
            }
          </select>
        </div>
      </div>
      <div class="col-md-6">
        <div class="form-group">
          <label>Branch</label>
          <select class="form-control" [(ngModel)]="form.branchId">
            <option [ngValue]="null">All Branches</option>
            @for (b of branches; track b.id) {
              <option [ngValue]="b.id">{{ b.name }}</option>
            }
          </select>
        </div>
      </div>
    </div>
    <div class="form-group">
      <label>Approval Levels Required *</label>
      <input type="number" class="form-control" [(ngModel)]="form.approvalLevelsRequired" min="1" />
    </div>
    <div class="form-group">
      <label>Priority Order</label>
      <input type="number" class="form-control" [(ngModel)]="form.priorityOrder" min="0" />
    </div>
    <div class="row">
      <div class="col-md-6">
        <div class="form-check">
          <input type="checkbox" class="form-check-input" id="autoApprove" [(ngModel)]="form.autoApprove" />
          <label class="form-check-label" for="autoApprove">Auto-Approve</label>
        </div>
      </div>
      <div class="col-md-6">
        <div class="form-check">
          <input type="checkbox" class="form-check-input" id="committeeRequired" [(ngModel)]="form.committeeRequired" />
          <label class="form-check-label" for="committeeRequired">Committee Required</label>
        </div>
      </div>
    </div>
    <div class="form-check mt-2">
      <input type="checkbox" class="form-check-input" id="active" [(ngModel)]="form.active" />
      <label class="form-check-label" for="active">Active</label>
    </div>
  </div>
  <div class="modal-footer">
    <button class="btn btn-secondary" (click)="modal.dismiss()">Cancel</button>
    <button class="btn btn-primary" (click)="createRule(); modal.close()"><i class="fas fa-save me-1"></i>Create</button>
  </div>
</ng-template>

<ng-template #editContent let-modal>
  <div class="modal-header">
    <h5 class="modal-title"><i class="fas fa-edit text-primary me-2"></i>Edit Approval Rule</h5>
    <button type="button" class="btn-close" (click)="modal.dismiss()"></button>
  </div>
  <div class="modal-body">
    <div class="form-group">
      <label>Rule Name *</label>
      <input class="form-control" [(ngModel)]="editForm.ruleName" />
    </div>
    <div class="row">
      <div class="col-md-6">
        <div class="form-group">
          <label>Min Amount (NPR)</label>
          <input type="number" class="form-control" [(ngModel)]="editForm.minAmount" min="0" />
        </div>
      </div>
      <div class="col-md-6">
        <div class="form-group">
          <label>Max Amount (NPR)</label>
          <input type="number" class="form-control" [(ngModel)]="editForm.maxAmount" min="0" />
        </div>
      </div>
    </div>
    <div class="form-group">
      <label>Expense Category</label>
      <select class="form-control" [(ngModel)]="editForm.expenseCategoryId">
        <option [ngValue]="null">All Categories</option>
        @for (cat of categories; track cat.id) {
          <option [ngValue]="cat.id">{{ cat.categoryName }}</option>
        }
      </select>
    </div>
    <div class="row">
      <div class="col-md-6">
        <div class="form-group">
          <label>Department</label>
          <select class="form-control" [(ngModel)]="editForm.departmentId">
            <option [ngValue]="null">All Departments</option>
            @for (dept of departments; track dept.id) {
              <option [ngValue]="dept.id">{{ dept.name }}</option>
            }
          </select>
        </div>
      </div>
      <div class="col-md-6">
        <div class="form-group">
          <label>Branch</label>
          <select class="form-control" [(ngModel)]="editForm.branchId">
            <option [ngValue]="null">All Branches</option>
            @for (b of branches; track b.id) {
              <option [ngValue]="b.id">{{ b.name }}</option>
            }
          </select>
        </div>
      </div>
    </div>
    <div class="form-group">
      <label>Approval Levels Required *</label>
      <input type="number" class="form-control" [(ngModel)]="editForm.approvalLevelsRequired" min="1" />
    </div>
    <div class="form-group">
      <label>Priority Order</label>
      <input type="number" class="form-control" [(ngModel)]="editForm.priorityOrder" min="0" />
    </div>
    <div class="row">
      <div class="col-md-6">
        <div class="form-check">
          <input type="checkbox" class="form-check-input" id="editAutoApprove" [(ngModel)]="editForm.autoApprove" />
          <label class="form-check-label" for="editAutoApprove">Auto-Approve</label>
        </div>
      </div>
      <div class="col-md-6">
        <div class="form-check">
          <input type="checkbox" class="form-check-input" id="editCommitteeRequired" [(ngModel)]="editForm.committeeRequired" />
          <label class="form-check-label" for="editCommitteeRequired">Committee Required</label>
        </div>
      </div>
    </div>
    <div class="form-check mt-2">
      <input type="checkbox" class="form-check-input" id="editActive" [(ngModel)]="editForm.active" />
      <label class="form-check-label" for="editActive">Active</label>
    </div>
  </div>
  <div class="modal-footer">
    <button class="btn btn-secondary" (click)="modal.dismiss()">Cancel</button>
    <button class="btn btn-primary" (click)="updateRule(); modal.close()"><i class="fas fa-save me-1"></i>Save</button>
  </div>
</ng-template>
  `,
  styles: [`
    .form-group { margin-bottom: 1rem; }
    .form-group label { display: block; margin-bottom: 0.4rem; font-size: 0.85rem; font-weight: 500; color: var(--nexmemo-text-secondary); }
    .form-check { display: flex; align-items: center; gap: 0.5rem; }
    .form-check label { font-size: 0.9rem; color: var(--nexmemo-text); margin-bottom: 0; }
    .amount-range { display: inline-flex; align-items: center; gap: 0.5rem; }
    .amount { font-family: var(--font-mono); font-size: 0.85rem; font-weight: 600; color: var(--nexmemo-text); }
    .amount-arrow { font-size: 0.7rem; color: var(--nexmemo-text-muted); }
    .check-icon { font-size: 1.2rem; }
    .priority-badge { font-family: var(--font-mono); font-size: 0.85rem; font-weight: 600; color: var(--nexmemo-primary); }
    .empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 3rem 1rem; color: var(--nexmemo-text-muted); }
    .empty-state i { font-size: 3rem; margin-bottom: 1rem; opacity: 0.4; }
    .empty-state p { font-size: 0.95rem; text-align: center; max-width: 400px; }
    .modal-header .modal-title { display: flex; align-items: center; }
  `]
})
export class ApprovalRulesComponent implements OnInit {
  rules: any[] = [];
  categories: any[] = [];
  departments: any[] = [];
  branches: any[] = [];
  form: any = {
    ruleName: '',
    minAmount: null,
    maxAmount: null,
    approvalLevelsRequired: 1,
    expenseCategoryId: null,
    departmentId: null,
    branchId: null,
    autoApprove: false,
    committeeRequired: false,
    priorityOrder: 0,
    active: true
  };
  editForm: any = {};
  private http = inject(HttpClient);
  private modalService = inject(NgbModal);
  private toastr = inject(ToastrService);

  ngOnInit() {
    const storage = LocalStorageUtil.getStorage();
    this.loadRules();
    this.loadCategories();
    this.loadDepartments();
    this.loadBranches();
  }

  loadRules() {
    this.http.get<any>(`${ApiConfig.URL}/api/v1/approval-rules`).subscribe({
      next: (data) => this.rules = Array.isArray(data) ? data : [],
      error: () => this.toastr.error('Failed to load approval rules')
    });
  }

  loadCategories() {
    this.http.get<any>(`${ApiConfig.URL}/api/v1/expense-categories`).subscribe({
      next: (data) => this.categories = Array.isArray(data) ? data : [],
      error: () => this.toastr.error('Failed to load expense categories')
    });
  }

  loadDepartments() {
    this.http.get<any>(`${ApiConfig.URL}/api/v1/departments`).subscribe({
      next: (data) => this.departments = Array.isArray(data) ? data : [],
      error: () => this.toastr.error('Failed to load departments')
    });
  }

  loadBranches() {
    this.http.get<any>(`${ApiConfig.URL}/api/v1/branches`).subscribe({
      next: (data) => this.branches = Array.isArray(data) ? data : [],
      error: () => this.toastr.error('Failed to load branches')
    });
  }

  openCreateModal(content: any) {
    this.form = {
      ruleName: '',
      minAmount: null,
      maxAmount: null,
      approvalLevelsRequired: 1,
      expenseCategoryId: null,
      departmentId: null,
      branchId: null,
      autoApprove: false,
      committeeRequired: false,
      priorityOrder: 0,
      active: true
    };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  openEditModal(content: any, rule: any) {
    this.editForm = {
      ...rule,
      expenseCategoryId: rule.expenseCategory?.id || null,
      departmentId: rule.department?.id || null,
      branchId: rule.branch?.id || null,
      active: rule.status === 'ACTIVE'
    };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  createRule() {
    const payload = this.buildPayload(this.form);
    this.http.post(`${ApiConfig.URL}/api/v1/approval-rules`, payload).subscribe({
      next: () => { this.toastr.success('Approval rule created'); this.loadRules(); },
      error: (err) => this.toastr.error(err.error?.message || 'Failed to create rule')
    });
  }

  updateRule() {
    const payload = this.buildPayload(this.editForm);
    this.http.put(`${ApiConfig.URL}/api/v1/approval-rules/${this.editForm.id}`, payload).subscribe({
      next: () => { this.toastr.success('Approval rule updated'); this.loadRules(); },
      error: (err) => this.toastr.error(err.error?.message || 'Failed to update rule')
    });
  }

  deleteRule(id: number) {
    if (!confirm('Delete this approval rule? This action cannot be undone.')) return;
    this.http.delete(`${ApiConfig.URL}/api/v1/approval-rules/${id}`).subscribe({
      next: () => { this.toastr.success('Approval rule deleted'); this.loadRules(); },
      error: () => this.toastr.error('Failed to delete rule')
    });
  }

  private buildPayload(source: any): any {
    return {
      ruleName: source.ruleName,
      minAmount: source.minAmount,
      maxAmount: source.maxAmount,
      approvalLevelsRequired: source.approvalLevelsRequired,
      expenseCategory: source.expenseCategoryId ? { id: source.expenseCategoryId } : null,
      department: source.departmentId ? { id: source.departmentId } : null,
      branch: source.branchId ? { id: source.branchId } : null,
      autoApprove: source.autoApprove,
      committeeRequired: source.committeeRequired,
      priorityOrder: source.priorityOrder,
      status: source.active ? 'ACTIVE' : 'INACTIVE'
    };
  }
}
