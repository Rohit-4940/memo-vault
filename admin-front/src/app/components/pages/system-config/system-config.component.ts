import { Component, OnInit, inject } from '@angular/core';
import { NgForOf, NgIf } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { SystemConfigService } from '../../../shared-services/api-service/system-config.service';
import { ToastrService } from 'ngx-toastr';
import { LocalStorageUtil } from '../../../@core/utils/local-storage-util';
import { AdvancedTableComponent, TableColumn } from '../../../shared-components/advanced-table/advanced-table.component';

@Component({
  selector: 'app-system-config',
  standalone: true,
  imports: [NgForOf, NgIf, FormsModule, NgbModalModule, AdvancedTableComponent],
  templateUrl: './system-config.component.html',
  styleUrl: './system-config.component.scss'
})
export class SystemConfigComponent implements OnInit {
  columns: TableColumn[] = [
    { key: 'id', label: 'ID', sortable: true, width: '80px' },
    { key: 'configKey', label: 'Key', sortable: true, filterable: true, filterType: 'text' },
    { key: 'configValue', label: 'Value', sortable: false, width: '250px' },
    { key: 'description', label: 'Description', sortable: false },
    { key: 'dataType', label: 'Data Type', sortable: true, filterable: true, filterType: 'select', filterOptions: [{label:'String',value:'STRING'},{label:'Number',value:'NUMBER'},{label:'Boolean',value:'BOOLEAN'},{label:'JSON',value:'JSON'}] },
    { key: 'status', label: 'Status', sortable: true, filterable: true, filterType: 'select', filterOptions: [{label:'Active',value:'ACTIVE'},{label:'Inactive',value:'INACTIVE'},{label:'Enabled',value:'ENABLED'},{label:'Disabled',value:'DISABLED'}] }
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
  emailConfig: any = { host: '', port: '', username: '', password: '', fromEmail: '', encryption: '' };
  selectedConfig: any = { configKey: '', configValue: '', description: '', dataType: 'STRING', status: 'ACTIVE' };
  isEditing = false;
  isAdmin = false;
  showEmailSection = false;
  private modalService = inject(NgbModal);

  constructor(
    private configService: SystemConfigService,
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
    this.configService.getPaginated(this.searchQuery, this.pageNumber, this.pageSize, this.sortBy, this.sortDir, filterObj).subscribe({
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

  loadEmailConfig() {
    this.configService.getEmailConfig().subscribe({
      next: (data) => {
        this.emailConfig = data;
        this.showEmailSection = true;
      },
      error: () => this.toastr.error('Failed to load email configuration')
    });
  }

  openEditModal(content: any, config: any) {
    this.isEditing = true;
    this.selectedConfig = { ...config };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  openCreateModal(content: any) {
    this.isEditing = false;
    this.selectedConfig = { configKey: '', configValue: '', description: '', dataType: 'STRING', status: 'ACTIVE' };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  saveConfig() {
    this.configService.updateConfig(this.selectedConfig.id, this.selectedConfig).subscribe({
      next: () => {
        this.toastr.success('Configuration updated');
        this.modalService.dismissAll();
        this.loadData();
      },
      error: (err) => this.toastr.error(err.error?.message || 'Failed')
    });
  }

  saveEmailConfig() {
    this.configService.updateEmailConfig(this.emailConfig).subscribe({
      next: () => { this.toastr.success('Email configuration updated'); },
      error: (err) => this.toastr.error(err.error?.message || 'Failed')
    });
  }

  seedDefaults() {
    if (confirm('Seed default system configurations? This will create missing default configs.')) {
      this.configService.seedDefaults().subscribe({
        next: () => { this.toastr.success('Default configurations seeded'); this.loadData(); },
        error: () => this.toastr.error('Seed failed')
      });
    }
  }

  getStatusBadgeClass(status: string): string {
    switch (status) {
      case 'ACTIVE': return 'badge-success';
      case 'INACTIVE': return 'badge-secondary';
      case 'ENABLED': return 'badge-success';
      case 'DISABLED': return 'badge-danger';
      default: return 'badge-info';
    }
  }
}
