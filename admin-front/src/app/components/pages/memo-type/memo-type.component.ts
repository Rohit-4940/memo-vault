import { Component, OnInit, inject } from '@angular/core';
import { NgForOf, NgIf } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { MemoTypeService } from '../../../shared-services/api-service/memo-type.service';
import { ToastrService } from 'ngx-toastr';
import { LocalStorageUtil } from '../../../@core/utils/local-storage-util';
import { AdvancedTableComponent, TableColumn } from '../../../shared-components/advanced-table/advanced-table.component';

@Component({
  selector: 'app-memo-type',
  standalone: true,
  imports: [NgForOf, NgIf, FormsModule, NgbModalModule, AdvancedTableComponent],
  templateUrl: './memo-type.component.html',
  styleUrl: './memo-type.component.scss'
})
export class MemoTypeComponent implements OnInit {
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
  selectedMemoType: any = { name: '', description: '', status: 'ACTIVE' };
  isEditing = false;
  isAdmin = false;
  private modalService = inject(NgbModal);

  constructor(
    private memoTypeService: MemoTypeService,
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
    this.memoTypeService.getPaginated(this.searchQuery, this.pageNumber, this.pageSize, this.sortBy, this.sortDir, filterObj).subscribe({
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
    this.selectedMemoType = { name: '', description: '', status: 'ACTIVE' };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  openEditModal(content: any, memoType: any) {
    this.isEditing = true;
    this.selectedMemoType = { ...memoType };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  saveMemoType() {
    const obs = this.isEditing
      ? this.memoTypeService.updateMemoType(this.selectedMemoType.id, this.selectedMemoType)
      : this.memoTypeService.createMemoType(this.selectedMemoType);
    obs.subscribe({
      next: () => {
        this.toastr.success(this.isEditing ? 'Memo type updated' : 'Memo type created');
        this.modalService.dismissAll();
        this.loadData();
      },
      error: (err) => this.toastr.error(err.error?.message || 'Failed')
    });
  }

  deleteMemoType(id: number) {
    if (confirm('Delete this memo type?')) {
      this.memoTypeService.deleteMemoType(id).subscribe({
        next: () => { this.toastr.success('Memo type deleted'); this.loadData(); },
        error: () => this.toastr.error('Delete failed')
      });
    }
  }
}
