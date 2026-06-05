import { Component, OnInit, inject } from '@angular/core';
import { NgForOf, NgIf, DecimalPipe, SlicePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { HttpClient } from '@angular/common/http';
import { ApiConfig } from '../../../@core/utils/api/ApiConfig';
import { MemoTemplateService } from '../../../shared-services/api-service/memo-template.service';
import { MemoTypeService } from '../../../shared-services/api-service/memo-type.service';
import { DepartmentService } from '../../../shared-services/api-service/department.service';
import { UserService } from '../../../shared-services/api-service/user.service';
import { ToastrService } from 'ngx-toastr';
import { LocalStorageUtil } from '../../../@core/utils/local-storage-util';
import { CKEditorModule, ChangeEvent } from '@ckeditor/ckeditor5-angular';
import ClassicEditor from '@ckeditor/ckeditor5-build-classic';
import { AdvancedTableComponent, TableColumn } from '../../../shared-components/advanced-table/advanced-table.component';

@Component({
  selector: 'app-memo-template',
  standalone: true,
  imports: [NgForOf, NgIf, DecimalPipe, SlicePipe, FormsModule, NgbModalModule, CKEditorModule, AdvancedTableComponent],
  templateUrl: './memo-template.component.html',
  styleUrl: './memo-template.component.scss'
})
export class MemoTemplateComponent implements OnInit {
  public Editor = ClassicEditor as any;
  public editorConfig = {
    toolbar: ['heading', '|', 'bold', 'italic', 'underline', 'strikethrough', '|',
      'bulletedList', 'numberedList', '|',
      'alignment:left', 'alignment:center', 'alignment:right', '|',
      'insertTable', 'tableColumn', 'tableRow', 'mergeTableCells', '|',
      'link', 'blockQuote', '|', 'undo', 'redo'],
    placeholder: 'Write the memo template content here...'
  };

  columns: TableColumn[] = [
    { key: 'name', label: 'Name', sortable: true, filterable: true, filterType: 'text' },
    { key: 'expenseCategoryName', label: 'Category', sortable: true, filterable: true, filterType: 'text' },
    { key: 'departmentName', label: 'Dept', sortable: true, filterable: true, filterType: 'text' },
    { key: 'defaultAmount', label: 'Default Amount', sortable: true, type: 'number' },
    { key: 'requiredApprovalLevels', label: 'Approval Lvls', sortable: true },
    { key: 'defaultPriority', label: 'Priority', sortable: true, filterable: true, filterType: 'select', filterOptions: [{label:'Low',value:'LOW'},{label:'Medium',value:'MEDIUM'},{label:'High',value:'HIGH'},{label:'Urgent',value:'URGENT'}] },
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

  templates: any[] = [];
  memoTypes: any[] = [];
  departments: any[] = [];
  expenseCategories: any[] = [];
  users: any[] = [];
  selectedTemplate: any = { name: '', description: '', content: '', memoTypeId: null, departmentId: null, expenseCategoryId: null, status: 'ACTIVE', defaultAmount: null, maxAmount: null, defaultPriority: 'MEDIUM', defaultPaymentMethod: '', requiredApprovalLevels: 1, requiredFields: '' };
  isEditing = false;
  isAdmin = false;

  approvalLevels: any[] = [];
  defaultCcUsers: any[] = [];
  selectedTab = 'details';
  newApproverType = 'USER';
  newApproverId: number | null = null;
  newCcUserId: number | null = null;

  private modalService = inject(NgbModal);
  private http = inject(HttpClient);

  constructor(
    private templateService: MemoTemplateService,
    private memoTypeService: MemoTypeService,
    private departmentService: DepartmentService,
    private userService: UserService,
    private toastr: ToastrService
  ) {}

  ngOnInit() {
    const storage = LocalStorageUtil.getStorage();
    this.isAdmin = storage.roleName === 'ADMIN' || storage.roleName === 'SUPER_ADMIN';
    this.loadData();
    this.memoTypeService.getAllMemoTypes().subscribe({ next: (d) => this.memoTypes = d });
    this.departmentService.getAllDepartments().subscribe({ next: (d) => this.departments = d });
    this.http.get<any[]>(`${ApiConfig.URL}/api/v1/expense-categories`).subscribe({ next: (d) => this.expenseCategories = d });
    this.userService.getAllUsers().subscribe({ next: (d) => this.users = d });
  }

  loadData() {
    this.loading = true;
    const filterObj: any = {};
    this.filters.forEach((v, k) => { if (v) filterObj[k] = v; });
    this.templateService.getPaginated(this.searchQuery, this.pageNumber, this.pageSize, this.sortBy, this.sortDir, filterObj).subscribe({
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
    this.selectedTemplate = { name: '', description: '', content: '', memoTypeId: null, departmentId: null, expenseCategoryId: null, status: 'ACTIVE', defaultAmount: null, maxAmount: null, defaultPriority: 'MEDIUM', defaultPaymentMethod: '', requiredApprovalLevels: 1, requiredFields: '' };
    this.approvalLevels = [];
    this.defaultCcUsers = [];
    this.selectedTab = 'details';
    this.modalService.open(content, { size: 'lg', centered: true, windowClass: 'template-modal' });
  }

  openEditModal(content: any, template: any) {
    this.isEditing = true;
    this.selectedTab = 'details';
    this.selectedTemplate = {
      ...template,
      memoType: template.memoType || null,
      department: template.department || null,
      expenseCategory: template.expenseCategory || null,
      memoTypeId: template.memoType?.id || null,
      departmentId: template.department?.id || null,
      expenseCategoryId: template.expenseCategory?.id || null
    };
    this.loadApprovalLevels(template.id);
    this.loadDefaultCcUsers(template.id);
    this.modalService.open(content, { size: 'lg', centered: true, windowClass: 'template-modal' });
  }

  loadApprovalLevels(templateId: number) {
    this.templateService.getApprovalLevels(templateId).subscribe({
      next: (d) => this.approvalLevels = d || [],
      error: () => this.approvalLevels = []
    });
  }

  loadDefaultCcUsers(templateId: number) {
    this.templateService.getDefaultCcUsers(templateId).subscribe({
      next: (d) => this.defaultCcUsers = d || [],
      error: () => this.defaultCcUsers = []
    });
  }

  addApprovalLevel() {
    if (!this.newApproverId) return;
    const user = this.users.find(u => u.id === this.newApproverId);
    this.approvalLevels.push({
      approverType: this.newApproverType,
      approverId: this.newApproverId,
      approverName: user ? user.name || user.userName : 'User ' + this.newApproverId
    });
    this.newApproverId = null;
  }

  removeApprovalLevel(index: number) {
    this.approvalLevels.splice(index, 1);
  }

  saveApprovalLevels() {
    if (!this.selectedTemplate.id) return;
    this.templateService.saveApprovalLevels(this.selectedTemplate.id, this.approvalLevels).subscribe({
      next: () => {
        this.toastr.success('Approval levels saved');
        this.loadApprovalLevels(this.selectedTemplate.id);
      },
      error: () => this.toastr.error('Failed to save approval levels')
    });
  }

  addDefaultCcUser() {
    if (!this.newCcUserId || !this.selectedTemplate.id) return;
    const already = this.defaultCcUsers.some(d => d.user?.id === this.newCcUserId || d.userId === this.newCcUserId);
    if (already) { this.toastr.info('User already added as default CC'); return; }
    this.templateService.addDefaultCcUser(this.selectedTemplate.id, this.newCcUserId).subscribe({
      next: (res) => {
        this.toastr.success('Default CC user added');
        this.loadDefaultCcUsers(this.selectedTemplate.id);
        this.newCcUserId = null;
      },
      error: () => this.toastr.error('Failed to add default CC user')
    });
  }

  removeDefaultCcUser(ccId: number) {
    if (!this.selectedTemplate.id) return;
    this.templateService.removeDefaultCcUser(this.selectedTemplate.id, ccId).subscribe({
      next: () => {
        this.toastr.success('Default CC user removed');
        this.loadDefaultCcUsers(this.selectedTemplate.id);
      },
      error: () => this.toastr.error('Failed to remove default CC user')
    });
  }

  getUserDisplay(user: any): string {
    if (!user) return 'Unknown';
    return user.name || user.userName || 'User';
  }

  getApproverName(level: any): string {
    if (level.approverName) return level.approverName;
    const user = this.users.find(u => u.id === level.approverId);
    return user ? (user.name || user.userName) : 'User ' + level.approverId;
  }

  switchTab(tab: string) {
    this.selectedTab = tab;
  }

  saveTemplate() {
    const payload: any = {
      name: this.selectedTemplate.name,
      description: this.selectedTemplate.description,
      content: this.selectedTemplate.content,
      status: this.selectedTemplate.status,
      defaultAmount: this.selectedTemplate.defaultAmount,
      maxAmount: this.selectedTemplate.maxAmount,
      defaultPriority: this.selectedTemplate.defaultPriority,
      defaultPaymentMethod: this.selectedTemplate.defaultPaymentMethod,
      requiredApprovalLevels: this.selectedTemplate.requiredApprovalLevels,
      requiredFields: this.selectedTemplate.requiredFields,
      memoType: this.selectedTemplate.memoTypeId ? { id: this.selectedTemplate.memoTypeId } : null,
      department: this.selectedTemplate.departmentId ? { id: this.selectedTemplate.departmentId } : null,
      expenseCategory: this.selectedTemplate.expenseCategoryId ? { id: this.selectedTemplate.expenseCategoryId } : null
    };
    const obs = this.isEditing
      ? this.templateService.updateTemplate(this.selectedTemplate.id, payload)
      : this.templateService.createTemplate(payload);
    obs.subscribe({
      next: () => {
        this.toastr.success(this.isEditing ? 'Template updated' : 'Template created');
        this.modalService.dismissAll();
        this.loadData();
      },
      error: (err) => this.toastr.error(err.error?.message || 'Failed')
    });
  }

  deleteTemplate(id: number) {
    if (confirm('Delete this template?')) {
      this.templateService.deleteTemplate(id).subscribe({
        next: () => { this.toastr.success('Template deleted'); this.loadData(); },
        error: () => this.toastr.error('Delete failed')
      });
    }
  }

  onContentChange(event: ChangeEvent) {
    this.selectedTemplate.content = event.editor.getData();
  }
}
