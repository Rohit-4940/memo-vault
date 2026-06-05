import { Component, OnInit, inject } from '@angular/core';
import { NgForOf, NgIf } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { BranchService } from '../../../shared-services/api-service/branch.service';
import { ToastrService } from 'ngx-toastr';
import { AdvancedTableComponent, TableColumn } from '../../../shared-components/advanced-table/advanced-table.component';

@Component({
  selector: 'app-branch',
  standalone: true,
  imports: [NgForOf, NgIf, FormsModule, NgbModalModule, AdvancedTableComponent],
  templateUrl: './branch.component.html',
  styleUrl: './branch.component.scss'
})
export class BranchComponent implements OnInit {
  columns: TableColumn[] = [
    { key: 'id', label: 'ID', sortable: true, filterable: true, filterType: 'number', width: '80px' },
    { key: 'name', label: 'Name', sortable: true, filterable: true, filterType: 'text' },
    { key: 'branchCode', label: 'Code', sortable: true, filterable: true, filterType: 'text', width: '120px' },
    { key: 'streetName', label: 'Address', sortable: false, filterable: true, filterType: 'text' },
    { key: 'branchEmail', label: 'Email', sortable: true, filterable: true, filterType: 'text' }
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

  branches: any[] = [];
  selectedBranch: any = { name: '', branchCode: '', streetName: '', wardNumber: '', branchEmail: '' };
  isEditing = false;
  private modalService = inject(NgbModal);
  constructor(private branchService: BranchService, private toastr: ToastrService) {}

  ngOnInit() { this.loadBranches(); }

  loadBranches() {
    this.loading = true;
    const filterObj: any = {};
    this.filters.forEach((v, k) => { if (v) filterObj[k] = v; });
    this.branchService.getPaginated(this.searchQuery, this.pageNumber, this.pageSize, this.sortBy, this.sortDir, filterObj).subscribe({
      next: (res) => {
        this.data = res.content || [];
        this.totalElements = res.totalElements || 0;
        this.totalPages = res.totalPages || 1;
        this.loading = false;
      },
      error: () => { this.toastr.error('Failed to load branches'); this.loading = false; }
    });
  }

  onSearch(value: string) {
    this.searchQuery = value;
    this.pageNumber = 1;
    this.loadBranches();
  }

  onPageChange(page: number) {
    this.pageNumber = page;
    this.loadBranches();
  }

  onSizeChange(size: number) {
    this.pageSize = size;
    this.pageNumber = 1;
    this.loadBranches();
  }

  onSortChange(sort: any) {
    this.sortBy = sort.sortBy;
    this.sortDir = sort.sortDir;
    this.loadBranches();
  }

  onFilterChange(filters: Map<string, string>) {
    this.filters = filters;
    this.pageNumber = 1;
    this.loadBranches();
  }

  openCreateModal(content: any) {
    this.isEditing = false;
    this.selectedBranch = { name: '', branchCode: '', streetName: '', wardNumber: '', branchEmail: '' };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  openEditModal(content: any, branch: any) {
    this.isEditing = true;
    this.selectedBranch = { ...branch };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  saveBranch() {
    const obs = this.isEditing
      ? this.branchService.updateBranch(this.selectedBranch.id, this.selectedBranch)
      : this.branchService.createBranch(this.selectedBranch);
    obs.subscribe({
      next: () => { this.toastr.success(this.isEditing ? 'Branch updated' : 'Branch created'); this.modalService.dismissAll(); this.loadBranches(); },
      error: (err) => this.toastr.error(err.error?.message || 'Failed')
    });
  }

  deleteBranch(id: number) {
    if (confirm('Delete this branch?')) {
      this.branchService.deleteBranch(id).subscribe({
        next: () => { this.toastr.success('Branch deleted'); this.loadBranches(); },
        error: () => this.toastr.error('Delete failed')
      });
    }
  }
}
