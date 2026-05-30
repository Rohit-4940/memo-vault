import { Component, OnInit, inject } from '@angular/core';
import { NgForOf, NgIf } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { MemoTemplateService } from '../../../shared-services/api-service/memo-template.service';
import { MemoTypeService } from '../../../shared-services/api-service/memo-type.service';
import { DepartmentService } from '../../../shared-services/api-service/department.service';
import { ToastrService } from 'ngx-toastr';
import { LocalStorageUtil } from '../../../@core/utils/local-storage-util';

@Component({
  selector: 'app-memo-template',
  standalone: true,
  imports: [NgForOf, NgIf, FormsModule, NgbModalModule],
  templateUrl: './memo-template.component.html',
  styleUrl: './memo-template.component.scss'
})
export class MemoTemplateComponent implements OnInit {
  templates: any[] = [];
  memoTypes: any[] = [];
  departments: any[] = [];
  selectedTemplate: any = { name: '', description: '', content: '', memoTypeId: null, departmentId: null, status: 'ACTIVE' };
  isEditing = false;
  isAdmin = false;
  private modalService = inject(NgbModal);

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
  }

  loadTemplates() {
    this.templateService.getAllTemplates().subscribe({
      next: (data) => this.templates = data,
      error: () => this.toastr.error('Failed to load templates')
    });
  }

  openCreateModal(content: any) {
    this.isEditing = false;
    this.selectedTemplate = { name: '', description: '', content: '', memoTypeId: null, departmentId: null, status: 'ACTIVE' };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  openEditModal(content: any, template: any) {
    this.isEditing = true;
    this.selectedTemplate = { ...template, memoTypeId: template.memoTypeId, departmentId: template.departmentId };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  saveTemplate() {
    const obs = this.isEditing
      ? this.templateService.updateTemplate(this.selectedTemplate.id, this.selectedTemplate)
      : this.templateService.createTemplate(this.selectedTemplate);
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
}
