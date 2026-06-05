import { Component, OnInit, inject } from '@angular/core';
import { NgForOf, NgIf, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { LeaveService } from '../../../shared-services/api-service/leave.service';
import { ToastrService } from 'ngx-toastr';
import { LocalStorageUtil } from '../../../@core/utils/local-storage-util';
import { AdvancedTableComponent, TableColumn } from '../../../shared-components/advanced-table/advanced-table.component';

@Component({
  selector: 'app-leaves',
  standalone: true,
  imports: [NgForOf, NgIf, FormsModule, NgbModalModule, DatePipe, AdvancedTableComponent],
  templateUrl: './leaves.component.html',
  styleUrl: './leaves.component.scss'
})
export class LeavesComponent implements OnInit {
  columns: TableColumn[] = [
    { key: 'id', label: 'ID', sortable: true, width: '80px' },
    { key: 'userName', label: 'User', sortable: true, filterable: true, filterType: 'text' },
    { key: 'leaveType', label: 'Leave Type', sortable: true, filterable: true, filterType: 'text' },
    { key: 'startDate', label: 'Start Date', sortable: true, type: 'date' },
    { key: 'endDate', label: 'End Date', sortable: true, type: 'date' },
    { key: 'reason', label: 'Reason', sortable: false },
    { key: 'status', label: 'Status', sortable: true, filterable: true, filterType: 'select', filterOptions: [{label:'Pending',value:'PENDING'},{label:'Approved',value:'APPROVED'},{label:'Rejected',value:'REJECTED'},{label:'Cancelled',value:'CANCELLED'}] }
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
  selectedLeave: any = { leaveType: '', startDate: '', endDate: '', reason: '' };
  isAdmin = false;
  currentUserId: number = 0;
  private modalService = inject(NgbModal);

  constructor(
    private leaveService: LeaveService,
    private toastr: ToastrService
  ) {}

  ngOnInit() {
    const storage = LocalStorageUtil.getStorage();
    this.isAdmin = storage.roleName === 'ADMIN' || storage.roleName === 'SUPER_ADMIN';
    this.currentUserId = storage.userId || 0;
    this.loadData();
  }

  loadData() {
    this.loading = true;
    const filterObj: any = {};
    this.filters.forEach((v, k) => { if (v) filterObj[k] = v; });
    this.leaveService.getPaginated(this.searchQuery, this.pageNumber, this.pageSize, this.sortBy, this.sortDir, filterObj).subscribe({
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
    this.selectedLeave = { leaveType: '', startDate: '', endDate: '', reason: '' };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  submitLeave() {
    this.leaveService.createLeave(this.selectedLeave).subscribe({
      next: () => {
        this.toastr.success('Leave request submitted');
        this.modalService.dismissAll();
        this.loadData();
      },
      error: (err) => this.toastr.error(err.error?.message || 'Failed')
    });
  }

  approveLeave(id: number) {
    if (confirm('Approve this leave request?')) {
      this.leaveService.approveLeave(id).subscribe({
        next: () => { this.toastr.success('Leave approved'); this.loadData(); },
        error: () => this.toastr.error('Failed')
      });
    }
  }

  rejectLeave(id: number) {
    const remarks = prompt('Enter rejection reason:');
    if (remarks !== null) {
      this.leaveService.rejectLeave(id, remarks || undefined).subscribe({
        next: () => { this.toastr.success('Leave rejected'); this.loadData(); },
        error: () => this.toastr.error('Failed')
      });
    }
  }

  cancelLeave(id: number) {
    if (confirm('Cancel this leave request?')) {
      this.leaveService.cancelLeave(id).subscribe({
        next: () => { this.toastr.success('Leave cancelled'); this.loadData(); },
        error: () => this.toastr.error('Failed')
      });
    }
  }

  getStatusBadge(status: string): string {
    switch (status) {
      case 'APPROVED': return 'badge-success';
      case 'REJECTED': return 'badge-danger';
      case 'PENDING': return 'badge-warning';
      case 'CANCELLED': return 'badge-secondary';
      default: return 'badge-info';
    }
  }

  isOnLeave(leave: any): boolean {
    if (leave.status !== 'APPROVED') return false;
    const today = new Date();
    const start = new Date(leave.startDate);
    const end = new Date(leave.endDate);
    return today >= start && today <= end;
  }
}
