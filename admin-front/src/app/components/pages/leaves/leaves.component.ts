import { Component, OnInit, inject } from '@angular/core';
import { NgForOf, NgIf, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { LeaveService } from '../../../shared-services/api-service/leave.service';
import { ToastrService } from 'ngx-toastr';
import { LocalStorageUtil } from '../../../@core/utils/local-storage-util';

@Component({
  selector: 'app-leaves',
  standalone: true,
  imports: [NgForOf, NgIf, FormsModule, NgbModalModule, DatePipe],
  templateUrl: './leaves.component.html',
  styleUrl: './leaves.component.scss'
})
export class LeavesComponent implements OnInit {
  leaves: any[] = [];
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
    this.loadLeaves();
  }

  loadLeaves() {
    const obs = this.isAdmin ? this.leaveService.getAllLeaves() : this.leaveService.getMyLeaves();
    obs.subscribe({
      next: (data) => this.leaves = data,
      error: () => this.toastr.error('Failed to load leaves')
    });
  }

  openCreateModal(content: any) {
    this.selectedLeave = { leaveType: '', startDate: '', endDate: '', reason: '' };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  submitLeave() {
    this.leaveService.createLeave(this.selectedLeave).subscribe({
      next: () => {
        this.toastr.success('Leave request submitted');
        this.modalService.dismissAll();
        this.loadLeaves();
      },
      error: (err) => this.toastr.error(err.error?.message || 'Failed')
    });
  }

  approveLeave(id: number) {
    if (confirm('Approve this leave request?')) {
      this.leaveService.approveLeave(id).subscribe({
        next: () => { this.toastr.success('Leave approved'); this.loadLeaves(); },
        error: () => this.toastr.error('Failed')
      });
    }
  }

  rejectLeave(id: number) {
    const remarks = prompt('Enter rejection reason:');
    if (remarks !== null) {
      this.leaveService.rejectLeave(id, remarks || undefined).subscribe({
        next: () => { this.toastr.success('Leave rejected'); this.loadLeaves(); },
        error: () => this.toastr.error('Failed')
      });
    }
  }

  cancelLeave(id: number) {
    if (confirm('Cancel this leave request?')) {
      this.leaveService.cancelLeave(id).subscribe({
        next: () => { this.toastr.success('Leave cancelled'); this.loadLeaves(); },
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
