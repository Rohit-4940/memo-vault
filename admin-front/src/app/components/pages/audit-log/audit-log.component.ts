import { Component, OnInit } from '@angular/core';
import { NgForOf, NgIf, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuditLogService } from '../../../shared-services/api-service/audit-log.service';
import { PaginationComponent } from '../../../shared-components/pagination/pagination.component';

@Component({
  selector: 'app-audit-log',
  standalone: true,
  imports: [NgForOf, NgIf, DatePipe, FormsModule, PaginationComponent],
  templateUrl: './audit-log.component.html',
  styleUrl: './audit-log.component.scss'
})
export class AuditLogComponent implements OnInit {
  logs: any[] = [];
  loading = false;

  filterAction = '';
  filterStatus = '';
  filterUsername = '';
  filterDateFrom = '';
  filterDateTo = '';

  currentPage = 0;
  pageSize = 20;
  totalElements = 0;
  totalPages = 0;

  actionOptions = ['LOGIN', 'LOGOUT', 'PASSWORD_CHANGE', 'TOKEN_REFRESHED', 'LOGIN_BLOCKED',
    'PASSWORD_RESET_SENT', 'PASSWORD_RESET_COMPLETED'];
  statusOptions = ['SUCCESS', 'FAILURE', 'SUSPICIOUS'];
  showFilters = false;

  constructor(private auditLogService: AuditLogService) {}

  ngOnInit() {
    this.loadLogs();
  }

  loadLogs() {
    this.loading = true;
    const params: any = {
      page: this.currentPage,
      size: this.pageSize,
      sort: 'createdDate',
      dir: 'desc'
    };
    if (this.filterAction) params.action = this.filterAction;
    if (this.filterStatus) params.status = this.filterStatus;
    if (this.filterUsername) params.username = this.filterUsername;
    if (this.filterDateFrom) params.dateFrom = new Date(this.filterDateFrom).toISOString();
    if (this.filterDateTo) {
      const d = new Date(this.filterDateTo);
      d.setHours(23, 59, 59, 999);
      params.dateTo = d.toISOString();
    }
    this.auditLogService.getAuditLogs(params).subscribe({
      next: (res) => {
        this.logs = res.content || [];
        this.currentPage = res.page || 0;
        this.pageSize = res.size || 20;
        this.totalElements = res.totalElements || 0;
        this.totalPages = res.totalPages || 0;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  onPageChange(event: { page: number; size: number }) {
    this.currentPage = event.page;
    this.pageSize = event.size;
    this.loadLogs();
  }

  onSearch() {
    this.currentPage = 0;
    this.loadLogs();
  }

  clearFilters() {
    this.filterAction = '';
    this.filterStatus = '';
    this.filterUsername = '';
    this.filterDateFrom = '';
    this.filterDateTo = '';
    this.currentPage = 0;
    this.loadLogs();
  }

  getStatusBadge(status: string): string {
    switch (status) {
      case 'SUCCESS': return 'badge-success';
      case 'FAILURE': return 'badge-danger';
      case 'SUSPICIOUS': return 'badge-warning';
      default: return 'badge-secondary';
    }
  }

  getActionIcon(action: string): string {
    switch (action) {
      case 'LOGIN': return 'fa-sign-in-alt';
      case 'LOGOUT': return 'fa-sign-out-alt';
      case 'PASSWORD_CHANGE': case 'PASSWORD_RESET_COMPLETED': return 'fa-key';
      case 'PASSWORD_RESET_SENT': return 'fa-envelope';
      case 'TOKEN_REFRESHED': return 'fa-sync';
      case 'LOGIN_BLOCKED': return 'fa-ban';
      default: return 'fa-circle';
    }
  }

  truncate(str: string | null | undefined, len: number): string {
    if (!str) return '-';
    return str.length > len ? str.substring(0, len) + '...' : str;
  }

  getStatusColor(status: string): string {
    switch (status) {
      case 'SUCCESS': return 'var(--nexmemo-success)';
      case 'FAILURE': return 'var(--nexmemo-danger)';
      case 'SUSPICIOUS': return 'var(--nexmemo-warning)';
      default: return 'var(--nexmemo-text-secondary)';
    }
  }
}
