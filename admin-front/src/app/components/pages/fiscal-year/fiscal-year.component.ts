import { Component, OnInit, inject } from '@angular/core';
import { NgForOf, NgIf, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { FiscalYearService } from '../../../shared-services/api-service/fiscal-year.service';
import { ToastrService } from 'ngx-toastr';
import { LocalStorageUtil } from '../../../@core/utils/local-storage-util';

@Component({
  selector: 'app-fiscal-year',
  standalone: true,
  imports: [NgForOf, NgIf, FormsModule, NgbModalModule, DatePipe],
  templateUrl: './fiscal-year.component.html',
  styleUrl: './fiscal-year.component.scss'
})
export class FiscalYearComponent implements OnInit {
  fiscalYears: any[] = [];
  selectedFiscalYear: any = { name: '', startDate: '', endDate: '', isActive: false };
  isEditing = false;
  isAdmin = false;
  private modalService = inject(NgbModal);

  constructor(
    private fiscalYearService: FiscalYearService,
    private toastr: ToastrService
  ) {}

  ngOnInit() {
    const storage = LocalStorageUtil.getStorage();
    this.isAdmin = storage.roleName === 'ADMIN' || storage.roleName === 'SUPER_ADMIN';
    this.loadFiscalYears();
  }

  loadFiscalYears() {
    this.fiscalYearService.getAllFiscalYears().subscribe({
      next: (data) => this.fiscalYears = data,
      error: () => this.toastr.error('Failed to load fiscal years')
    });
  }

  openCreateModal(content: any) {
    this.isEditing = false;
    this.selectedFiscalYear = { name: '', startDate: '', endDate: '', isActive: false };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  openEditModal(content: any, fiscalYear: any) {
    this.isEditing = true;
    this.selectedFiscalYear = { ...fiscalYear, startDate: this.formatDate(fiscalYear.startDate), endDate: this.formatDate(fiscalYear.endDate) };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  private formatDate(date: any): string {
    if (!date) return '';
    const d = new Date(date);
    return d.toISOString().split('T')[0];
  }

  saveFiscalYear() {
    const payload = { ...this.selectedFiscalYear };
    const obs = this.isEditing
      ? this.fiscalYearService.updateFiscalYear(payload.id, payload)
      : this.fiscalYearService.createFiscalYear(payload);
    obs.subscribe({
      next: () => {
        this.toastr.success(this.isEditing ? 'Fiscal year updated' : 'Fiscal year created');
        this.modalService.dismissAll();
        this.loadFiscalYears();
      },
      error: (err) => this.toastr.error(err.error?.message || 'Failed')
    });
  }

  setActive(fiscalYear: any) {
    if (confirm(`Set "${fiscalYear.name}" as active fiscal year?`)) {
      this.fiscalYearService.setActiveFiscalYear(fiscalYear.id).subscribe({
        next: () => { this.toastr.success('Active fiscal year updated'); this.loadFiscalYears(); },
        error: () => this.toastr.error('Failed to set active fiscal year')
      });
    }
  }

  deleteFiscalYear(id: number) {
    if (confirm('Delete this fiscal year?')) {
      this.fiscalYearService.deleteFiscalYear(id).subscribe({
        next: () => { this.toastr.success('Fiscal year deleted'); this.loadFiscalYears(); },
        error: () => this.toastr.error('Delete failed')
      });
    }
  }
}
