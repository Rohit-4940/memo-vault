import { Component, OnInit, inject } from '@angular/core';
import { NgForOf, NgIf } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { DepartmentService } from '../../../shared-services/api-service/department.service';
import { ToastrService } from 'ngx-toastr';
import { LocalStorageUtil } from '../../../@core/utils/local-storage-util';
import { AdvancedTableComponent, TableColumn } from '../../../shared-components/advanced-table/advanced-table.component';

@Component({
  selector: 'app-department',
  standalone: true,
  imports: [NgForOf, NgIf, FormsModule, NgbModalModule, AdvancedTableComponent],
  templateUrl: './department.component.html',
  styleUrl: './department.component.scss'
})
export class DepartmentComponent implements OnInit {
  columns: TableColumn[] = [
    { key: 'id', label: 'ID', sortable: true, width: '80px' },
    { key: 'name', label: 'Name', sortable: true, filterable: true, filterType: 'text' },
    { key: 'description', label: 'Description', sortable: false, filterable: true, filterType: 'text' },
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
    this.loadData();
  }

  loadData() {
    this.loading = true;
    const filterObj: any = {};
    this.filters.forEach((v, k) => { if (v) filterObj[k] = v; });
    this.departmentService.getPaginated(this.searchQuery, this.pageNumber, this.pageSize, this.sortBy, this.sortDir, filterObj).subscribe({
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
        this.loadData();
      },
      error: (err) => this.toastr.error(err.error?.message || 'Failed')
    });
  }

  deleteDepartment(id: number) {
    if (confirm('Delete this department?')) {
      this.departmentService.deleteDepartment(id).subscribe({
        next: () => { this.toastr.success('Department deleted'); this.loadData(); },
        error: () => this.toastr.error('Delete failed')
      });
    }
  }
}
