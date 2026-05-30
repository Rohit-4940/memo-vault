import { Component, OnInit, inject } from '@angular/core';
import { NgForOf, NgIf } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { SystemConfigService } from '../../../shared-services/api-service/system-config.service';
import { ToastrService } from 'ngx-toastr';
import { LocalStorageUtil } from '../../../@core/utils/local-storage-util';

@Component({
  selector: 'app-system-config',
  standalone: true,
  imports: [NgForOf, NgIf, FormsModule, NgbModalModule],
  templateUrl: './system-config.component.html',
  styleUrl: './system-config.component.scss'
})
export class SystemConfigComponent implements OnInit {
  configs: any[] = [];
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
    this.loadConfigs();
  }

  loadConfigs() {
    this.configService.getAllConfigs().subscribe({
      next: (data) => this.configs = data,
      error: () => this.toastr.error('Failed to load configurations')
    });
  }

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
        this.loadConfigs();
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
        next: () => { this.toastr.success('Default configurations seeded'); this.loadConfigs(); },
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
