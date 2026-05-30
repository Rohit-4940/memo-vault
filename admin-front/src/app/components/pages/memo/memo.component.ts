import { Component, OnInit, inject } from '@angular/core';
import { NgForOf, NgIf, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { MemoService } from '../../../shared-services/api-service/memo.service';
import { MemoTypeService } from '../../../shared-services/api-service/memo-type.service';
import { DepartmentService } from '../../../shared-services/api-service/department.service';
import { BranchService } from '../../../shared-services/api-service/branch.service';
import { MemoTemplateService } from '../../../shared-services/api-service/memo-template.service';
import { UserService } from '../../../shared-services/api-service/user.service';
import { ToastrService } from 'ngx-toastr';
import { LocalStorageUtil } from '../../../@core/utils/local-storage-util';

@Component({
  selector: 'app-memo',
  standalone: true,
  imports: [NgForOf, NgIf, FormsModule, NgbModalModule, DatePipe],
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
  selectedMemo: any = {
    title: '', description: '', memoTypeId: null, departmentId: null,
    branchId: null, priority: 'MEDIUM', amount: 0, templateId: null
  };
  detailMemo: any = null;
  approvalHistory: any[] = [];
  memoRouting: any[] = [];
  searchTerm = '';
  statusFilter = '';
  branchFilter = '';
  currentUserId = 0;
  currentRole = '';
  showDetailView = false;
  isEditing = false;
  forwardUserId: number | null = null;
  remarks = '';

  private modalService = inject(NgbModal);

  constructor(
    private memoService: MemoService,
    private memoTypeService: MemoTypeService,
    private departmentService: DepartmentService,
    private branchService: BranchService,
    private templateService: MemoTemplateService,
    private userService: UserService,
    private toastr: ToastrService
  ) {}

  ngOnInit() {
    const storage = LocalStorageUtil.getStorage();
    this.currentUserId = storage.userId || 0;
    this.currentRole = storage.roleName || '';
    this.loadMemos();
    this.loadDropdowns();
  }

  loadMemos() {
    this.memoService.getAllMemos().subscribe({
      next: (data) => this.memos = data,
      error: () => this.toastr.error('Failed to load memos')
    });
  }

  loadDropdowns() {
    this.memoTypeService.getAllMemoTypes().subscribe({ next: (d) => this.memoTypes = d });
    this.departmentService.getAllDepartments().subscribe({ next: (d) => this.departments = d });
    this.branchService.getAllBranches().subscribe({ next: (d) => this.branches = d });
    this.templateService.getAllTemplates().subscribe({ next: (d) => { this.templates = d; } });
    this.userService.getAllUsers().subscribe({ next: (d) => this.users = d });
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
        if (!this.isEditing) {
          this.selectedMemo.description = tmpl.content || '';
          this.selectedMemo.memoTypeId = tmpl.memoTypeId || tmpl.memoType?.id || null;
          this.selectedMemo.departmentId = tmpl.departmentId || tmpl.department?.id || null;
        }
      }
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

  openCreateModal(content: any) {
    this.isEditing = false;
    this.selectedMemo = {
      title: '', description: '', memoTypeId: null, departmentId: null,
      branchId: null, priority: 'MEDIUM', amount: 0, templateId: null
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
      templateId: memo.templateId || null
    };
    this.onDepartmentOrTypeChange();
    this.modalService.open(content, { size: 'lg', centered: true, windowClass: 'memo-modal' });
  }

  saveMemo() {
    const obs = this.isEditing
      ? this.memoService.createMemo(this.selectedMemo)
      : this.memoService.createMemo(this.selectedMemo);
    obs.subscribe({
      next: () => {
        this.toastr.success(this.isEditing ? 'Memo updated' : 'Memo created');
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
    window.scrollTo(0, 0);
  }

  backToList() {
    this.showDetailView = false;
    this.detailMemo = null;
    this.approvalHistory = [];
    this.memoRouting = [];
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
        next: () => { this.toastr.success('Memo returned'); this.backToList(); },
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

  getStatusBadge(status: string): string {
    switch (status) {
      case 'DRAFT': return 'badge-secondary';
      case 'PENDING_APPROVAL': case 'PENDING': return 'badge-warning';
      case 'APPROVED': return 'badge-success';
      case 'REJECTED': return 'badge-danger';
      case 'CANCELLED': return 'badge-secondary';
      case 'RETURNED': return 'badge-info';
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
    return this.detailMemo?.status === 'DRAFT' && (this.detailMemo?.createdBy === this.currentUserId || this.detailMemo?.userId === this.currentUserId);
  }

  get canSubmit(): boolean {
    return this.detailMemo?.status === 'DRAFT' && (this.detailMemo?.createdBy === this.currentUserId || this.detailMemo?.userId === this.currentUserId);
  }

  get canApprove(): boolean {
    return this.detailMemo?.status === 'PENDING_APPROVAL' || this.detailMemo?.status === 'PENDING';
  }

  get canReturn(): boolean {
    return this.detailMemo?.status === 'PENDING_APPROVAL' || this.detailMemo?.status === 'PENDING';
  }

  get canReject(): boolean {
    return this.detailMemo?.status === 'PENDING_APPROVAL' || this.detailMemo?.status === 'PENDING';
  }

  get canCancel(): boolean {
    return this.detailMemo?.status === 'DRAFT' || this.detailMemo?.status === 'PENDING_APPROVAL' || this.detailMemo?.status === 'PENDING';
  }

  get canForward(): boolean {
    return this.detailMemo?.status === 'PENDING_APPROVAL' || this.detailMemo?.status === 'PENDING';
  }

  getStatusIcon(status: string): string {
    switch (status) {
      case 'SUBMITTED': case 'PENDING_APPROVAL': return 'fa-paper-plane';
      case 'APPROVED': return 'fa-check-circle';
      case 'REJECTED': return 'fa-times-circle';
      case 'RETURNED': return 'fa-undo';
      case 'FORWARDED': return 'fa-forward';
      case 'CANCELLED': return 'fa-ban';
      default: return 'fa-circle';
    }
  }
}
