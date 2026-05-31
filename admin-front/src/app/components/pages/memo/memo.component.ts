import { Component, OnInit, inject } from '@angular/core';
import { NgForOf, NgIf, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { MemoService } from '../../../shared-services/api-service/memo.service';
import { MemoTypeService } from '../../../shared-services/api-service/memo-type.service';
import { DepartmentService } from '../../../shared-services/api-service/department.service';
import { BranchService } from '../../../shared-services/api-service/branch.service';
import { MemoTemplateService } from '../../../shared-services/api-service/memo-template.service';
import { UserService } from '../../../shared-services/api-service/user.service';
import { ToastrService } from 'ngx-toastr';
import { LocalStorageUtil } from '../../../@core/utils/local-storage-util';
import { HttpClient } from '@angular/common/http';
import { ApiConfig } from '../../../@core/utils/api/ApiConfig';
import { MemoPdfExportService } from '../../../shared-services/memo-pdf-export.service';
import { MemoDocumentUploadComponent } from '../memo-document-upload/memo-document-upload.component';
import { PaginationComponent } from '../../../shared-components/pagination/pagination.component';

@Component({
  selector: 'app-memo',
  standalone: true,
  imports: [NgForOf, NgIf, FormsModule, NgbModalModule, DatePipe, DecimalPipe, MemoDocumentUploadComponent, PaginationComponent],
  templateUrl: './memo.component.html',
  styleUrl: './memo.component.scss'
})
export class MemoComponent implements OnInit {
  memos: any[] = [];
  memoTypes: any[] = [];
  departments: any[] = [];
  branches: any[] = [];
  templates: any[] = [];
  filteredTemplates: any[] = [];
  users: any[] = [];
  memoCcUsers: any[] = [];
  expenseCategories: any[] = [];
  fiscalYears: any[] = [];
  selectedMemo: any = {
    title: '', description: '', memoTypeId: null, departmentId: null,
    branchId: null, priority: 'MEDIUM', amount: 0, templateId: null, ccUserIds: [],
    expenseCategoryId: null, budgetCode: '', paymentMethod: 'BANK_TRANSFER',
    vendorName: '', vendorAccount: '', isUrgent: false, expectedDate: null, fiscalYearId: null
  };
  detailMemo: any = null;
  approvalHistory: any[] = [];
  memoRouting: any[] = [];
  ccRecipients: any[] = [];
  searchTerm = '';
  statusFilter = '';
  branchFilter = '';
  currentUserId = 0;
  currentRole = '';
  showDetailView = false;
  isEditing = false;
  forwardUserId: number | null = null;
  remarks = '';

  // Pagination
  currentPage = 0;
  pageSize = 20;
  totalElements = 0;
  totalPages = 0;
  sortField = 'createdDate';
  sortDir = 'desc';
  loadingMemos = false;

  // Dashboard tabs
  activeTab = 'all';
  stats: { [key: string]: number } = { all: 0, pending: 0, approved: 0, draft: 0, rejected: 0 };
  ccMemos: any[] = [];
  pendingApprovals: any[] = [];

  private modalService = inject(NgbModal);

  constructor(
    private pdfExport: MemoPdfExportService,
    private memoService: MemoService,
    private memoTypeService: MemoTypeService,
    private departmentService: DepartmentService,
    private branchService: BranchService,
    private templateService: MemoTemplateService,
    private userService: UserService,
    private toastr: ToastrService,
    private http: HttpClient,
    private route: ActivatedRoute
  ) {}

  ngOnInit() {
    const storage = LocalStorageUtil.getStorage();
    this.currentUserId = storage.userId || 0;
    this.currentRole = storage.roleName || '';
    this.loadDropdowns();
    this.loadMemos();
    this.loadStats();
    this.loadCcMemos();
    this.loadPendingApprovals();
    this.route.queryParams.subscribe(params => {
      if (params['detail']) {
        const id = Number(params['detail']);
        this.memoService.getMemoById(id).subscribe({
          next: (m) => { this.viewDetail(m); },
          error: () => this.toastr.error('Memo not found')
        });
      }
      if (params['tab']) this.activeTab = params['tab'] as any;
    });
  }

  get isAdminRole(): boolean {
    return this.currentRole === 'ADMIN' || this.currentRole === 'SUPER_ADMIN';
  }

  get canCreateMemo(): boolean {
    return !this.isAdminRole;
  }

  loadMemos() {
    this.loadingMemos = true;
    this.memoService.getMemosPaginated(this.currentPage, this.pageSize, this.sortField, this.sortDir).subscribe({
      next: (res) => {
        this.memos = res.content || [];
        this.currentPage = res.page || 0;
        this.pageSize = res.size || 20;
        this.totalElements = res.totalElements || 0;
        this.totalPages = res.totalPages || 0;
        this.loadingMemos = false;
      },
      error: () => {
        this.toastr.error('Failed to load memos');
        this.loadingMemos = false;
      }
    });
  }

  onPageChange(event: { page: number; size: number }) {
    this.currentPage = event.page;
    this.pageSize = event.size;
    this.loadMemos();
  }

  onSearch() {
    this.currentPage = 0;
    this.loadMemos();
  }

  clearFilters() {
    this.searchTerm = '';
    this.statusFilter = '';
    this.branchFilter = '';
    this.currentPage = 0;
    this.loadMemos();
  }

  switchTab(tab: string) {
    this.activeTab = tab;
    this.statusFilter = tab === 'all' ? '' : tab.toUpperCase();
    this.currentPage = 0;
    this.loadMemos();
  }

  loadStats() {
    this.memoService.getMemosPaginated(0, 1).subscribe({
      next: (res) => { this.stats['all'] = res.totalElements || 0; }
    });
    this.memoService.getByStatus('PENDING_APPROVAL').subscribe({
      next: (d) => { this.stats['pending'] = d.length; this.pendingApprovals = d; }
    });
    this.memoService.getByStatus('APPROVED').subscribe({
      next: (d) => this.stats['approved'] = d.length
    });
    this.memoService.getByStatus('DRAFT').subscribe({
      next: (d) => this.stats['draft'] = d.length
    });
    this.memoService.getByStatus('REJECTED').subscribe({
      next: (d) => this.stats['rejected'] = d.length
    });
  }

  loadCcMemos() {
    this.memoService.getMyCcMemos().subscribe({
      next: (d) => { this.ccMemos = d || []; }
    });
  }

  loadPendingApprovals() {
    this.memoService.getPendingApprovals().subscribe({
      next: (d) => { this.pendingApprovals = d || []; }
    });
  }

  loadDropdowns() {
    this.memoTypeService.getAllMemoTypes().subscribe({ next: (d) => this.memoTypes = d });
    this.departmentService.getAllDepartments().subscribe({ next: (d) => this.departments = d });
    this.branchService.getAllBranches().subscribe({ next: (d) => this.branches = d });
    this.templateService.getAllTemplates().subscribe({ next: (d) => { this.templates = d; } });
    this.userService.getAllUsers().subscribe({ next: (d) => this.users = d });
    this.http.get<any[]>(`${ApiConfig.URL}/api/v1/expense-categories`).subscribe({ next: (d) => this.expenseCategories = d || [] });
    this.http.get<any[]>(`${ApiConfig.URL}/api/v1/fiscal-years`).subscribe({ next: (d) => this.fiscalYears = d || [] });
  }

  get filteredMemos(): any[] {
    return this.memos.filter(m => {
      const matchSearch = !this.searchTerm ||
        m.title?.toLowerCase().includes(this.searchTerm.toLowerCase()) ||
        m.memoNo?.toLowerCase().includes(this.searchTerm.toLowerCase());
      const matchStatus = !this.statusFilter || m.status === this.statusFilter;
      const matchBranch = !this.branchFilter || m.branchId === Number(this.branchFilter) || m.branch?.id === Number(this.branchFilter);
      return matchSearch && matchStatus && matchBranch;
    });
  }

  onTemplateChange() {
    if (this.selectedMemo.templateId) {
      const tmpl = this.templates.find(t => t.id === Number(this.selectedMemo.templateId));
      if (tmpl) {
        this.selectedMemo.flowType = tmpl.flowType;
        if (!this.isEditing) {
          this.selectedMemo.description = tmpl.content || '';
          this.selectedMemo.memoTypeId = tmpl.memoTypeId || tmpl.memoType?.id || null;
          this.selectedMemo.departmentId = tmpl.departmentId || tmpl.department?.id || null;
        }
      }
    } else {
      this.selectedMemo.flowType = null;
    }
  }

  onDepartmentOrTypeChange() {
    const deptId = this.selectedMemo.departmentId;
    const typeId = this.selectedMemo.memoTypeId;
    this.filteredTemplates = this.templates.filter(t => {
      const matchDept = !deptId || t.departmentId === Number(deptId) || t.department?.id === Number(deptId);
      const matchType = !typeId || t.memoTypeId === Number(typeId) || t.memoType?.id === Number(typeId);
      return matchDept && matchType;
    });
  }

  toggleCcUser(userId: number) {
    const idx = this.selectedMemo.ccUserIds.indexOf(userId);
    if (idx > -1) this.selectedMemo.ccUserIds.splice(idx, 1);
    else this.selectedMemo.ccUserIds.push(userId);
  }

  isCcSelected(userId: number): boolean {
    return this.selectedMemo.ccUserIds.includes(userId);
  }

  openCreateModal(content: any) {
    this.isEditing = false;
    this.selectedMemo = {
      title: '', description: '', memoTypeId: null, departmentId: null,
      branchId: null, priority: 'MEDIUM', amount: 0, templateId: null, ccUserIds: [],
      expenseCategoryId: null, budgetCode: '', paymentMethod: 'BANK_TRANSFER',
      vendorName: '', vendorAccount: '', isUrgent: false, expectedDate: null, fiscalYearId: null,
      flowType: null
    };
    this.filteredTemplates = [];
    this.modalService.open(content, { size: 'lg', centered: true, windowClass: 'memo-modal' });
  }

  openEditModal(content: any, memo: any) {
    this.isEditing = true;
    this.selectedMemo = {
      ...memo,
      memoTypeId: memo.memoTypeId || memo.memoType?.id || null,
      departmentId: memo.departmentId || memo.department?.id || null,
      branchId: memo.branchId || memo.branch?.id || null,
      templateId: memo.templateId || null,
      ccUserIds: [],
      expenseCategoryId: memo.expenseCategory?.id || null,
      budgetCode: memo.budgetCode || '',
      paymentMethod: memo.paymentMethod || 'BANK_TRANSFER',
      vendorName: memo.vendorName || '',
      vendorAccount: memo.vendorAccount || '',
      isUrgent: memo.isUrgent || false,
      expectedDate: memo.expectedDate || null,
      fiscalYearId: memo.fiscalYear?.id || null
    };
    this.onDepartmentOrTypeChange();
    this.modalService.open(content, { size: 'lg', centered: true, windowClass: 'memo-modal' });
  }

  saveMemo() {
    const payload = {
      ...this.selectedMemo,
      expenseCategory: this.selectedMemo.expenseCategoryId ? { id: this.selectedMemo.expenseCategoryId } : null,
      fiscalYear: this.selectedMemo.fiscalYearId ? { id: this.selectedMemo.fiscalYearId } : null
    };
    this.memoService.createMemo(payload).subscribe({
      next: () => {
        this.toastr.success('Memo created');
        this.modalService.dismissAll();
        this.loadMemos();
      },
      error: (err) => this.toastr.error(err.error?.message || 'Failed')
    });
  }

  viewDetail(memo: any) {
    this.showDetailView = true;
    this.detailMemo = memo;
    const id = memo.id;
    this.memoService.getApprovalHistory(id).subscribe({ next: (d) => this.approvalHistory = d });
    this.memoService.getMemoRouting(id).subscribe({ next: (d) => this.memoRouting = d });
    this.memoService.getMemoCc(id).subscribe({ next: (d) => this.ccRecipients = d });
    window.scrollTo(0, 0);
  }

  backToList() {
    this.showDetailView = false;
    this.detailMemo = null;
    this.approvalHistory = [];
    this.memoRouting = [];
    this.ccRecipients = [];
    this.loadMemos();
  }

  submitMemo() {
    if (confirm('Submit this memo for approval?')) {
      this.memoService.submitMemo(this.detailMemo.id).subscribe({
        next: () => { this.toastr.success('Memo submitted'); this.backToList(); },
        error: (err) => this.toastr.error(err.error?.message || 'Failed')
      });
    }
  }

  approveMemo() {
    const remarks = prompt('Approval remarks (optional):');
    this.memoService.approveMemo(this.detailMemo.id, remarks || undefined).subscribe({
      next: () => { this.toastr.success('Memo approved'); this.backToList(); },
      error: (err) => this.toastr.error(err.error?.message || 'Failed')
    });
  }

  rejectMemo() {
    const remarks = prompt('Rejection reason:');
    if (remarks !== null) {
      this.memoService.rejectMemo(this.detailMemo.id, remarks || undefined).subscribe({
        next: () => { this.toastr.success('Memo rejected'); this.backToList(); },
        error: (err) => this.toastr.error(err.error?.message || 'Failed')
      });
    }
  }

  returnMemo() {
    const remarks = prompt('Return reason/remarks:');
    if (remarks !== null) {
      this.memoService.returnMemo(this.detailMemo.id, remarks || undefined).subscribe({
        next: () => { this.toastr.success('Memo returned for revision'); this.backToList(); },
        error: (err) => this.toastr.error(err.error?.message || 'Failed')
      });
    }
  }

  forwardMemo() {
    if (!this.forwardUserId) {
      this.toastr.warning('Please select a user to forward to');
      return;
    }
    this.memoService.forwardMemo(this.detailMemo.id, this.forwardUserId, this.remarks || undefined).subscribe({
      next: () => { this.toastr.success('Memo forwarded'); this.backToList(); },
      error: (err) => this.toastr.error(err.error?.message || 'Failed')
    });
  }

  cancelMemo() {
    if (confirm('Cancel this memo?')) {
      this.memoService.cancelMemo(this.detailMemo.id).subscribe({
        next: () => { this.toastr.success('Memo cancelled'); this.backToList(); },
        error: (err) => this.toastr.error(err.error?.message || 'Failed')
      });
    }
  }

  exportPdf() {
    this.memoService.getApprovalHistory(this.detailMemo.id).subscribe({
      next: (history) => this.pdfExport.exportMemo(this.detailMemo, history),
      error: () => this.pdfExport.exportMemo(this.detailMemo, [])
    });
  }

  acknowledgeMemo() {
    if (confirm('Acknowledge this memo?')) {
      this.memoService.acknowledgeMemo(this.detailMemo.id).subscribe({
        next: () => {
          this.toastr.success('Memo acknowledged');
          this.memoService.getMemoCc(this.detailMemo.id).subscribe({ next: (d) => this.ccRecipients = d });
        },
        error: (err) => this.toastr.error(err.error?.message || 'Failed')
      });
    }
  }

  get isCurrentUserCc(): boolean {
    return this.ccRecipients?.some((cc: any) => cc.userId === this.currentUserId || cc.user?.id === this.currentUserId);
  }

  get isCurrentUserCcUnacknowledged(): boolean {
    return this.ccRecipients?.some((cc: any) =>
      (cc.userId === this.currentUserId || cc.user?.id === this.currentUserId) && !cc.acknowledged
    );
  }

  getStatusBadge(status: string): string {
    switch (status) {
      case 'DRAFT': return 'badge-secondary';
      case 'PENDING_APPROVAL': case 'PENDING': return 'badge-warning';
      case 'APPROVED': return 'badge-success';
      case 'REJECTED': return 'badge-danger';
      case 'CANCELLED': return 'badge-secondary';
      case 'REVISION_REQUESTED': return 'badge-info';
      default: return 'badge-info';
    }
  }

  getPriorityBadge(priority: string): string {
    switch (priority) {
      case 'LOW': return 'badge-secondary';
      case 'MEDIUM': return 'badge-info';
      case 'HIGH': return 'badge-warning';
      case 'URGENT': return 'badge-danger';
      default: return 'badge-info';
    }
  }

  get canEdit(): boolean {
    return this.detailMemo?.status === 'DRAFT' && this.detailMemo?.createdBy === this.currentUserId;
  }

  get canSubmit(): boolean {
    return this.detailMemo?.status === 'DRAFT' && this.detailMemo?.createdBy === this.currentUserId;
  }

  get canApprove(): boolean {
    return this.detailMemo?.status === 'PENDING_APPROVAL' && this.detailMemo?.currentApprover?.id === this.currentUserId;
  }

  get canReturn(): boolean {
    return this.detailMemo?.status === 'PENDING_APPROVAL' && this.detailMemo?.currentApprover?.id === this.currentUserId;
  }

  get canReject(): boolean {
    return this.detailMemo?.status === 'PENDING_APPROVAL' && this.detailMemo?.currentApprover?.id === this.currentUserId;
  }

  get canCancel(): boolean {
    return this.detailMemo?.status === 'DRAFT' && this.detailMemo?.createdBy === this.currentUserId;
  }

  get canForward(): boolean {
    return (this.detailMemo?.status === 'PENDING_APPROVAL' || this.detailMemo?.status === 'APPROVED')
      && !this.isAdminRole;
  }

  getStatusIcon(status: string): string {
    switch (status) {
      case 'SUBMITTED': case 'PENDING_APPROVAL': return 'fa-paper-plane';
      case 'APPROVED': return 'fa-check-circle';
      case 'REJECTED': return 'fa-times-circle';
      case 'REVISION_REQUESTED': return 'fa-undo';
      case 'FORWARDED': return 'fa-forward';
      case 'CANCELLED': return 'fa-ban';
      default: return 'fa-circle';
    }
  }
}
