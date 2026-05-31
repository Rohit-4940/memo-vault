import { Component, OnInit, inject } from '@angular/core';
import { NgIf, NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiConfig } from '../../../@core/utils/api/ApiConfig';
import { LocalStorageUtil } from '../../../@core/utils/local-storage-util';
import { ToastrService } from 'ngx-toastr';

@Component({
  selector: 'app-vendor',
  standalone: true,
  imports: [NgIf, NgClass, FormsModule],
  template: `
<div class="vendor-container animate-fade-in">
  <div class="vendor-header">
    <div class="header-left">
      <div class="header-icon"><i class="fas fa-building"></i></div>
      <div>
        <h1>Vendors & Counterparties</h1>
        <p class="header-subtitle">Manage banking vendors, suppliers and counterparty relationships</p>
      </div>
    </div>
    <div class="header-actions">
      <div class="search-wrapper">
        <i class="fas fa-search search-icon"></i>
        <input class="form-control search-input" [(ngModel)]="searchQuery" (input)="onSearch()" placeholder="Search vendors..." />
        @if (searchQuery) {
          <button class="search-clear" (click)="clearSearch()"><i class="fas fa-times"></i></button>
        }
      </div>
      <button class="btn btn-gradient" (click)="openCreate()"><i class="fas fa-plus"></i> New Vendor</button>
    </div>
  </div>

  @if (loading) {
    <div class="loading-state">
      <div class="spinner"></div>
      <p>Loading vendors...</p>
    </div>
  } @else if (vendors.length === 0) {
    <div class="empty-state">
      <div class="empty-icon"><i class="fas fa-building"></i></div>
      <h3>No Vendors Found</h3>
      <p>{{ searchQuery ? 'No vendors match your search criteria.' : 'No vendors have been added yet. Create your first vendor to get started.' }}</p>
      @if (!searchQuery) {
        <button class="btn btn-gradient" (click)="openCreate()"><i class="fas fa-plus"></i> Add Vendor</button>
      }
    </div>
  } @else {
    <div class="vendor-card">
      <div class="table-container">
        <table class="vendor-table">
          <thead>
            <tr>
              <th>Code</th>
              <th>Vendor Name</th>
              <th>Contact Person</th>
              <th>Phone</th>
              <th>PAN / Tax ID</th>
              <th>Payment Terms</th>
              <th>Status</th>
              <th class="actions-col">Actions</th>
            </tr>
          </thead>
          <tbody>
            @for (v of vendors; track v.id) {
              <tr>
                <td><span class="code-badge">{{ v.vendorCode }}</span></td>
                <td class="vendor-name">{{ v.vendorName }}</td>
                <td class="text-muted">{{ v.contactPerson || '—' }}</td>
                <td class="text-muted">{{ v.phone || '—' }}</td>
                <td><code class="pan-code">{{ v.panNumber || '—' }}</code></td>
                <td><span class="terms-badge">{{ v.paymentTerms || '—' }}</span></td>
                <td><span class="status-badge" [class.active]="v.status === 'ACTIVE'" [class.inactive]="v.status !== 'ACTIVE'">{{ v.status }}</span></td>
                <td class="actions-col">
                  <button class="btn btn-sm btn-icon" title="Edit" (click)="openEdit(v)"><i class="fas fa-edit"></i></button>
                  <button class="btn btn-sm btn-icon btn-icon-danger" title="Delete" (click)="deleteVendor(v.id)"><i class="fas fa-trash-alt"></i></button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </div>
  }
</div>

@if (showModal) {
  <div class="modal-overlay" (click)="closeModal()">
    <div class="modal-container" (click)="$event.stopPropagation()">
      <div class="modal-header">
        <h5><i class="fas" [class.fa-plus-circle]="!isEditing" [class.fa-edit]="isEditing"></i> {{ isEditing ? 'Edit' : 'New' }} Vendor</h5>
        <button class="btn-close" (click)="closeModal()">&times;</button>
      </div>
      <div class="modal-body">
        <div class="form-row">
          <div class="form-group">
            <label>Vendor Code <span class="required">*</span></label>
            <input class="form-control" [(ngModel)]="form.vendorCode" placeholder="e.g., VEN-001" />
          </div>
          <div class="form-group">
            <label>Vendor Name <span class="required">*</span></label>
            <input class="form-control" [(ngModel)]="form.vendorName" placeholder="e.g., ABC Supplies Pvt. Ltd." />
          </div>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>Contact Person</label>
            <input class="form-control" [(ngModel)]="form.contactPerson" placeholder="Full name" />
          </div>
          <div class="form-group">
            <label>Email</label>
            <input class="form-control" [(ngModel)]="form.email" placeholder="email@example.com" />
          </div>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>Phone</label>
            <input class="form-control" [(ngModel)]="form.phone" placeholder="+977-98XXXXXXXX" />
          </div>
          <div class="form-group">
            <label>PAN / Tax ID</label>
            <input class="form-control" [(ngModel)]="form.panNumber" placeholder="e.g., 123456789" />
          </div>
        </div>
        <div class="form-group">
          <label>Address</label>
          <textarea class="form-control" [(ngModel)]="form.address" rows="2" placeholder="Street, city, country"></textarea>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>Bank Name</label>
            <input class="form-control" [(ngModel)]="form.bankName" placeholder="e.g., Nepal Bank Ltd." />
          </div>
          <div class="form-group">
            <label>Bank Account</label>
            <input class="form-control" [(ngModel)]="form.bankAccount" placeholder="Account number" />
          </div>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>Payment Terms</label>
            <select class="form-control" [(ngModel)]="form.paymentTerms">
              <option value="">— Select —</option>
              <option value="IMMEDIATE">Immediate</option>
              <option value="NET15">Net 15</option>
              <option value="NET30">Net 30</option>
              <option value="NET45">Net 45</option>
              <option value="NET60">Net 60</option>
              <option value="NET90">Net 90</option>
            </select>
          </div>
          <div class="form-group">
            <label>Status</label>
            <select class="form-control" [(ngModel)]="form.status">
              <option value="ACTIVE">Active</option>
              <option value="INACTIVE">Inactive</option>
            </select>
          </div>
        </div>
        <div class="form-group">
          <label>Notes</label>
          <textarea class="form-control" [(ngModel)]="form.notes" rows="2" placeholder="Additional notes..."></textarea>
        </div>
      </div>
      <div class="modal-footer">
        <button class="btn btn-secondary" (click)="closeModal()">Cancel</button>
        <button class="btn btn-primary" (click)="saveVendor()" [disabled]="saving">
          @if (saving) { <span class="spinner-sm"></span> }
          {{ isEditing ? 'Update' : 'Create' }} Vendor
        </button>
      </div>
    </div>
  </div>
}
  `,
  styles: [`
    .vendor-container { padding: 1.5rem; max-width: 1400px; margin: 0 auto; }
    .vendor-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem; margin-bottom: 1.5rem; }
    .header-left { display: flex; align-items: center; gap: 1rem; }
    .header-icon { width: 48px; height: 48px; border-radius: 14px; background: linear-gradient(135deg, var(--nexmemo-primary, #2563eb), var(--nexmemo-secondary, #7c3aed)); display: flex; align-items: center; justify-content: center; color: #fff; font-size: 1.3rem; box-shadow: 0 4px 15px rgba(37,99,235,0.3); }
    .vendor-header h1 { margin: 0; font-size: 1.5rem; font-weight: 700; color: var(--nexmemo-text-primary, #1e293b); }
    .header-subtitle { margin: 0; font-size: 0.85rem; color: var(--nexmemo-text-secondary, #64748b); }
    .header-actions { display: flex; align-items: center; gap: 1rem; }
    .search-wrapper { position: relative; display: flex; align-items: center; }
    .search-icon { position: absolute; left: 12px; color: var(--nexmemo-text-muted, #94a3b8); font-size: 0.85rem; pointer-events: none; z-index: 1; }
    .search-input { padding-left: 36px; padding-right: 36px; width: 280px; border-radius: 10px; border: 1px solid var(--nexmemo-border, #e2e8f0); background: var(--nexmemo-bg-secondary, #f8fafc); height: 40px; font-size: 0.875rem; transition: all 0.2s; }
    .search-input:focus { border-color: var(--nexmemo-primary, #2563eb); box-shadow: 0 0 0 3px rgba(37,99,235,0.1); background: #fff; outline: none; }
    .search-clear { position: absolute; right: 10px; background: none; border: none; color: var(--nexmemo-text-muted, #94a3b8); cursor: pointer; padding: 4px; font-size: 0.8rem; }
    .search-clear:hover { color: var(--nexmemo-text-primary, #1e293b); }
    .vendor-card { background: var(--nexmemo-card-bg, #fff); border-radius: 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.05), 0 1px 2px rgba(0,0,0,0.1); border: 1px solid var(--nexmemo-border, #e2e8f0); overflow: hidden; backdrop-filter: blur(20px); }
    .table-container { overflow-x: auto; }
    .vendor-table { width: 100%; border-collapse: collapse; }
    .vendor-table thead { background: linear-gradient(135deg, var(--nexmemo-primary, #2563eb), var(--nexmemo-secondary, #7c3aed)); }
    .vendor-table thead th { padding: 0.85rem 1rem; font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; color: #fff; text-align: left; white-space: nowrap; }
    .vendor-table tbody tr { border-bottom: 1px solid var(--nexmemo-border, #e2e8f0); transition: background 0.15s; }
    .vendor-table tbody tr:hover { background: var(--nexmemo-bg-hover, #f1f5f9); }
    .vendor-table tbody td { padding: 0.85rem 1rem; font-size: 0.875rem; color: var(--nexmemo-text-primary, #1e293b); vertical-align: middle; }
    .code-badge { display: inline-block; padding: 0.2rem 0.6rem; border-radius: 6px; background: var(--nexmemo-bg-secondary, #f1f5f9); font-family: 'SF Mono', 'Fira Code', monospace; font-size: 0.8rem; font-weight: 600; color: var(--nexmemo-primary, #2563eb); border: 1px solid var(--nexmemo-border, #e2e8f0); }
    .vendor-name { font-weight: 600; }
    .pan-code { font-family: 'SF Mono', 'Fira Code', monospace; font-size: 0.8rem; color: var(--nexmemo-text-secondary, #64748b); }
    .terms-badge { display: inline-block; padding: 0.2rem 0.5rem; border-radius: 4px; background: var(--nexmemo-bg-secondary, #f8fafc); font-size: 0.78rem; font-weight: 500; color: var(--nexmemo-text-secondary, #475569); border: 1px solid var(--nexmemo-border, #e2e8f0); }
    .status-badge { display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.25rem 0.75rem; border-radius: 20px; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.03em; }
    .status-badge::before { content: ''; width: 6px; height: 6px; border-radius: 50%; }
    .status-badge.active { background: rgba(16,185,129,0.12); color: #059669; }
    .status-badge.active::before { background: #059669; }
    .status-badge.inactive { background: rgba(239,68,68,0.12); color: #dc2626; }
    .status-badge.inactive::before { background: #dc2626; }
    .actions-col { width: 100px; text-align: center; }
    .btn-icon { width: 34px; height: 34px; border-radius: 8px; border: 1px solid var(--nexmemo-border, #e2e8f0); background: var(--nexmemo-bg-secondary, #f8fafc); color: var(--nexmemo-text-secondary, #64748b); cursor: pointer; transition: all 0.2s; display: inline-flex; align-items: center; justify-content: center; margin: 0 2px; }
    .btn-icon:hover { background: var(--nexmemo-primary, #2563eb); color: #fff; border-color: var(--nexmemo-primary, #2563eb); }
    .btn-icon-danger:hover { background: #ef4444; color: #fff; border-color: #ef4444; }
    .loading-state { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 4rem 2rem; color: var(--nexmemo-text-secondary, #64748b); }
    .spinner { width: 40px; height: 40px; border: 3px solid var(--nexmemo-border, #e2e8f0); border-top-color: var(--nexmemo-primary, #2563eb); border-radius: 50%; animation: spin 0.7s linear infinite; }
    @keyframes spin { to { transform: rotate(360deg); } }
    .empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 4rem 2rem; text-align: center; }
    .empty-icon { width: 80px; height: 80px; border-radius: 50%; background: linear-gradient(135deg, rgba(37,99,235,0.08), rgba(124,58,237,0.08)); display: flex; align-items: center; justify-content: center; font-size: 2rem; color: var(--nexmemo-primary, #2563eb); margin-bottom: 1rem; }
    .empty-state h3 { margin: 0 0 0.5rem; font-size: 1.2rem; color: var(--nexmemo-text-primary, #1e293b); }
    .empty-state p { margin: 0 0 1.5rem; color: var(--nexmemo-text-secondary, #64748b); max-width: 400px; }
    .modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); backdrop-filter: blur(4px); display: flex; align-items: center; justify-content: center; z-index: 1050; padding: 1rem; }
    .modal-container { background: var(--nexmemo-card-bg, #fff); border-radius: 16px; width: 100%; max-width: 680px; max-height: 90vh; overflow-y: auto; box-shadow: 0 25px 50px rgba(0,0,0,0.2); animation: modalIn 0.25s ease; }
    @keyframes modalIn { from { opacity: 0; transform: scale(0.95) translateY(10px); } to { opacity: 1; transform: scale(1) translateY(0); } }
    .modal-header { display: flex; justify-content: space-between; align-items: center; padding: 1.25rem 1.5rem; border-bottom: 1px solid var(--nexmemo-border, #e2e8f0); background: linear-gradient(135deg, var(--nexmemo-primary, #2563eb), var(--nexmemo-secondary, #7c3aed)); border-radius: 16px 16px 0 0; }
    .modal-header h5 { margin: 0; color: #fff; font-size: 1.1rem; font-weight: 600; }
    .modal-header h5 i { margin-right: 0.5rem; }
    .btn-close { background: none; border: none; font-size: 1.5rem; color: rgba(255,255,255,0.7); cursor: pointer; line-height: 1; padding: 0; }
    .btn-close:hover { color: #fff; }
    .modal-body { padding: 1.5rem; }
    .modal-footer { display: flex; justify-content: flex-end; gap: 0.75rem; padding: 1rem 1.5rem; border-top: 1px solid var(--nexmemo-border, #e2e8f0); }
    .form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
    .form-group { margin-bottom: 1rem; }
    .form-group label { display: block; margin-bottom: 0.4rem; font-size: 0.82rem; font-weight: 600; color: var(--nexmemo-text-secondary, #475569); }
    .required { color: #ef4444; }
    .form-control { width: 100%; padding: 0.6rem 0.85rem; border: 1px solid var(--nexmemo-border, #e2e8f0); border-radius: 8px; font-size: 0.875rem; background: var(--nexmemo-bg-secondary, #f8fafc); transition: all 0.2s; box-sizing: border-box; }
    .form-control:focus { border-color: var(--nexmemo-primary, #2563eb); box-shadow: 0 0 0 3px rgba(37,99,235,0.1); background: #fff; outline: none; }
    textarea.form-control { resize: vertical; min-height: 60px; }
    .btn-gradient { background: linear-gradient(135deg, var(--nexmemo-primary, #2563eb), var(--nexmemo-secondary, #7c3aed)); color: #fff; border: none; padding: 0.6rem 1.25rem; border-radius: 10px; font-weight: 600; font-size: 0.85rem; cursor: pointer; transition: all 0.2s; display: inline-flex; align-items: center; gap: 0.5rem; white-space: nowrap; }
    .btn-gradient:hover { transform: translateY(-1px); box-shadow: 0 6px 20px rgba(37,99,235,0.3); }
    .btn-primary { background: var(--nexmemo-primary, #2563eb); color: #fff; border: none; padding: 0.6rem 1.25rem; border-radius: 8px; font-weight: 600; font-size: 0.85rem; cursor: pointer; transition: all 0.2s; display: inline-flex; align-items: center; gap: 0.5rem; }
    .btn-primary:hover { background: #1d4ed8; }
    .btn-secondary { background: var(--nexmemo-bg-secondary, #f1f5f9); color: var(--nexmemo-text-primary, #1e293b); border: 1px solid var(--nexmemo-border, #e2e8f0); padding: 0.6rem 1.25rem; border-radius: 8px; font-weight: 500; font-size: 0.85rem; cursor: pointer; transition: all 0.2s; }
    .btn-secondary:hover { background: var(--nexmemo-border, #e2e8f0); }
    .btn:disabled { opacity: 0.6; cursor: not-allowed; transform: none !important; }
    .spinner-sm { display: inline-block; width: 14px; height: 14px; border: 2px solid rgba(255,255,255,0.3); border-top-color: #fff; border-radius: 50%; animation: spin 0.6s linear infinite; }
    .text-muted { color: var(--nexmemo-text-secondary, #64748b); }
  `]
})
export class VendorComponent implements OnInit {
  vendors: any[] = [];
  searchQuery = '';
  showModal = false;
  isEditing = false;
  loading = false;
  saving = false;
  form: any = {};
  private http = inject(HttpClient);
  private toastr = inject(ToastrService);

  ngOnInit() {
    this.loadVendors();
  }

  loadVendors() {
    this.loading = true;
    this.http.get<any[]>(`${ApiConfig.URL}/api/v1/vendors`).subscribe({
      next: (data) => { this.vendors = data; this.loading = false; },
      error: () => { this.toastr.error('Failed to load vendors'); this.loading = false; }
    });
  }

  onSearch() {
    if (!this.searchQuery.trim()) { this.loadVendors(); return; }
    this.loading = true;
    this.http.get<any[]>(`${ApiConfig.URL}/api/v1/vendors/search?q=${encodeURIComponent(this.searchQuery)}`).subscribe({
      next: (data) => { this.vendors = data; this.loading = false; },
      error: () => { this.toastr.error('Search failed'); this.loading = false; }
    });
  }

  clearSearch() {
    this.searchQuery = '';
    this.loadVendors();
  }

  openCreate() {
    this.isEditing = false;
    this.form = { vendorCode: '', vendorName: '', contactPerson: '', email: '', phone: '', address: '', panNumber: '', bankAccount: '', bankName: '', paymentTerms: '', status: 'ACTIVE', notes: '' };
    this.showModal = true;
  }

  openEdit(vendor: any) {
    this.isEditing = true;
    this.form = { ...vendor };
    this.showModal = true;
  }

  closeModal() {
    this.showModal = false;
  }

  saveVendor() {
    this.saving = true;
    const obs = this.isEditing
      ? this.http.put(`${ApiConfig.URL}/api/v1/vendors/${this.form.id}`, this.form)
      : this.http.post(`${ApiConfig.URL}/api/v1/vendors`, this.form);
    obs.subscribe({
      next: () => {
        this.toastr.success(this.isEditing ? 'Vendor updated' : 'Vendor created');
        this.closeModal();
        this.loadVendors();
        this.saving = false;
      },
      error: (err) => {
        this.toastr.error(err.error?.message || 'Failed to save vendor');
        this.saving = false;
      }
    });
  }

  deleteVendor(id: number) {
    if (!confirm('Are you sure you want to delete this vendor? This action can be undone by an administrator.')) return;
    this.http.delete(`${ApiConfig.URL}/api/v1/vendors/${id}`).subscribe({
      next: () => { this.toastr.success('Vendor deleted'); this.loadVendors(); },
      error: () => { this.toastr.error('Failed to delete vendor. It may be referenced by existing memos.'); }
    });
  }
}
