import { Component, OnInit, inject } from '@angular/core';
import { NgForOf, NgIf, DecimalPipe, SlicePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { HttpClient } from '@angular/common/http';
import { ApiConfig } from '../../../@core/utils/api/ApiConfig';
import { MemoTemplateService } from '../../../shared-services/api-service/memo-template.service';
import { MemoTypeService } from '../../../shared-services/api-service/memo-type.service';
import { DepartmentService } from '../../../shared-services/api-service/department.service';
import { ToastrService } from 'ngx-toastr';
import { LocalStorageUtil } from '../../../@core/utils/local-storage-util';
import { CKEditorModule, ChangeEvent } from '@ckeditor/ckeditor5-angular';
import ClassicEditor from '@ckeditor/ckeditor5-build-classic';

@Component({
  selector: 'app-memo-template',
  standalone: true,
  imports: [NgForOf, NgIf, DecimalPipe, SlicePipe, FormsModule, NgbModalModule, CKEditorModule],
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

  templates: any[] = [];
  memoTypes: any[] = [];
  departments: any[] = [];
  expenseCategories: any[] = [];
  selectedTemplate: any = { name: '', description: '', content: '', memoTypeId: null, departmentId: null, expenseCategoryId: null, status: 'ACTIVE', defaultAmount: null, maxAmount: null, defaultPriority: 'MEDIUM', defaultPaymentMethod: '', requiredApprovalLevels: 1, requiredFields: '' };
  isEditing = false;
  isAdmin = false;
  private modalService = inject(NgbModal);
  private http = inject(HttpClient);

  constructor(
    private templateService: MemoTemplateService,
    private memoTypeService: MemoTypeService,
    private departmentService: DepartmentService,
    private toastr: ToastrService
  ) {}

  ngOnInit() {
    const storage = LocalStorageUtil.getStorage();
    this.isAdmin = storage.roleName === 'ADMIN' || storage.roleName === 'SUPER_ADMIN';
    this.loadTemplates();
    this.memoTypeService.getAllMemoTypes().subscribe({ next: (d) => this.memoTypes = d });
    this.departmentService.getAllDepartments().subscribe({ next: (d) => this.departments = d });
    this.http.get<any[]>(`${ApiConfig.URL}/api/v1/expense-categories`).subscribe({ next: (d) => this.expenseCategories = d });
  }

  loadTemplates() {
    this.templateService.getAllTemplates().subscribe({
      next: (data) => this.templates = data,
      error: () => this.toastr.error('Failed to load templates')
    });
  }

  openCreateModal(content: any) {
    this.isEditing = false;
    this.selectedTemplate = { name: '', description: '', content: '', memoTypeId: null, departmentId: null, expenseCategoryId: null, status: 'ACTIVE', defaultAmount: null, maxAmount: null, defaultPriority: 'MEDIUM', defaultPaymentMethod: '', requiredApprovalLevels: 1, requiredFields: '' };
    this.modalService.open(content, { size: 'lg', centered: true, windowClass: 'template-modal' });
  }

  openEditModal(content: any, template: any) {
    this.isEditing = true;
    this.selectedTemplate = {
      ...template,
      memoType: template.memoType || null,
      department: template.department || null,
      expenseCategory: template.expenseCategory || null,
      memoTypeId: template.memoType?.id || null,
      departmentId: template.department?.id || null,
      expenseCategoryId: template.expenseCategory?.id || null
    };
    this.modalService.open(content, { size: 'lg', centered: true, windowClass: 'template-modal' });
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
        this.loadTemplates();
      },
      error: (err) => this.toastr.error(err.error?.message || 'Failed')
    });
  }

  deleteTemplate(id: number) {
    if (confirm('Delete this template?')) {
      this.templateService.deleteTemplate(id).subscribe({
        next: () => { this.toastr.success('Template deleted'); this.loadTemplates(); },
        error: () => this.toastr.error('Delete failed')
      });
    }
  }

  onContentChange(event: ChangeEvent) {
    this.selectedTemplate.content = event.editor.getData();
  }
}
