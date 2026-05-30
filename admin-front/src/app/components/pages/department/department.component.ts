import { Component, OnInit, inject } from '@angular/core';
import { NgForOf, NgIf } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { DepartmentService } from '../../../shared-services/api-service/department.service';
import { ToastrService } from 'ngx-toastr';
import { LocalStorageUtil } from '../../../@core/utils/local-storage-util';

@Component({
  selector: 'app-department',
  standalone: true,
  imports: [NgForOf, NgIf, FormsModule, NgbModalModule],
  templateUrl: './department.component.html',
  styleUrl: './department.component.scss'
})
export class DepartmentComponent implements OnInit {
  departments: any[] = [];
  selectedDepartment: any = { name: '', description: '', status: 'ACTIVE' };
  isEditing = false;
  isAdmin = false;
  private modalService = inject(NgbModal);

  constructor(
    private departmentService: DepartmentService,
    private toastr: ToastrService
  ) {}

  ngOnInit() {
    const storage = LocalStorageUtil.getStorage();
    this.isAdmin = storage.roleName === 'ADMIN' || storage.roleName === 'SUPER_ADMIN';
    this.loadDepartments();
  }

  loadDepartments() {
    this.departmentService.getAllDepartments().subscribe({
      next: (data) => this.departments = data,
      error: () => this.toastr.error('Failed to load departments')
    });
  }

  openCreateModal(content: any) {
    this.isEditing = false;
    this.selectedDepartment = { name: '', description: '', status: 'ACTIVE' };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  openEditModal(content: any, department: any) {
    this.isEditing = true;
    this.selectedDepartment = { ...department };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  saveDepartment() {
    const obs = this.isEditing
      ? this.departmentService.updateDepartment(this.selectedDepartment.id, this.selectedDepartment)
      : this.departmentService.createDepartment(this.selectedDepartment);
    obs.subscribe({
      next: () => {
        this.toastr.success(this.isEditing ? 'Department updated' : 'Department created');
        this.modalService.dismissAll();
        this.loadDepartments();
      },
      error: (err) => this.toastr.error(err.error?.message || 'Failed')
    });
  }

  deleteDepartment(id: number) {
    if (confirm('Delete this department?')) {
      this.departmentService.deleteDepartment(id).subscribe({
        next: () => { this.toastr.success('Department deleted'); this.loadDepartments(); },
        error: () => this.toastr.error('Delete failed')
      });
    }
  }
}
