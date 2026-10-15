import { Component, OnInit, ViewChild } from '@angular/core';
import { NgForOf, NgIf, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { ToastrService } from 'ngx-toastr';
import { RequestService } from '../../../shared-services/api-service/request.service';
import { DynamicFormComponent } from '../../../shared-components/dynamic-form/dynamic-form.component';

/**
 * Phase 5b/6 — self-service request catalogue.
 *
 * Two views: browse the request types you may raise (each rendered from its memo type's
 * dynamic form schema) and track your own requests. A request is a memo underneath, so
 * this page only orchestrates the `/requests` API — approval, numbering, notification and
 * audit are all inherited from the memo engine.
 */
@Component({
  selector: 'app-memo-catalogue',
  standalone: true,
  imports: [NgForOf, NgIf, DatePipe, DecimalPipe, FormsModule, NgbModalModule, DynamicFormComponent],
  templateUrl: './memo-catalogue.component.html',
  styleUrl: './memo-catalogue.component.scss'
})
export class MemoCatalogueComponent implements OnInit {
  activeTab: 'catalogue' | 'mine' = 'catalogue';

  catalogue: any[] = [];
  myRequests: any[] = [];
  loadingCatalogue = false;
  loadingMine = false;
  catalogueSearch = '';

  // Raise / edit state
  selectedType: any = null;
  newRequest: any = this.emptyRequest();
  renderedFields: any[] = [];
  editingId: number | null = null;
  submitting = false;
  @ViewChild('dynamicFormRef') dynamicFormRef?: DynamicFormComponent;

  // Tracking state
  tracking: any = null;
  trackingRequest: any = null;
  loadingTracking = false;

  constructor(
    private requestService: RequestService,
    private modalService: NgbModal,
    private toastr: ToastrService
  ) {}

  ngOnInit(): void {
    this.loadCatalogue();
    this.loadMine();
  }

  emptyRequest(): any {
    return {
      title: '', description: '', priority: 'MEDIUM', amount: null,
      isUrgent: false, expectedDate: null, initialFormValues: {}
    };
  }

  get filteredCatalogue(): any[] {
    const q = (this.catalogueSearch || '').trim().toLowerCase();
    if (!q) return this.catalogue;
    return this.catalogue.filter(t =>
      (t.name || '').toLowerCase().includes(q) || (t.description || '').toLowerCase().includes(q));
  }

  setTab(tab: 'catalogue' | 'mine'): void {
    this.activeTab = tab;
    if (tab === 'mine') this.loadMine();
    if (tab === 'catalogue') this.loadCatalogue();
  }

  loadCatalogue(): void {
    this.loadingCatalogue = true;
    this.requestService.getCatalogue().subscribe({
      next: (d) => { this.catalogue = d || []; this.loadingCatalogue = false; },
      error: () => { this.catalogue = []; this.loadingCatalogue = false; this.toastr.error('Failed to load request catalogue'); }
    });
  }

  loadMine(): void {
    this.loadingMine = true;
    this.requestService.getMyRequests(0, 100).subscribe({
      next: (page) => { this.myRequests = page?.content || []; this.loadingMine = false; },
      error: () => { this.myRequests = []; this.loadingMine = false; }
    });
  }

  openRaise(modal: any, type: any): void {
    this.selectedType = type;
    this.editingId = null;
    this.newRequest = this.emptyRequest();
    this.renderedFields = this.mapFields(type?.fields);
    this.modalService.open(modal, { size: 'lg', centered: true, scrollable: true });
  }

  openEdit(modal: any, request: any): void {
    const typeId = request.memoTypeId || request.memoType?.id;
    const type = this.catalogue.find(t => t.id === typeId)
      || { id: typeId, name: request.memoType?.name || 'Request', description: '', fields: [] };
    this.selectedType = type;
    this.editingId = request.id;
    this.newRequest = {
      title: request.title || '',
      description: request.description || '',
      priority: request.priority || 'MEDIUM',
      amount: request.amount ?? null,
      isUrgent: !!request.isUrgent,
      expectedDate: request.expectedDate || null,
      initialFormValues: this.parseJson(request.formValues)
    };
    this.renderedFields = this.mapFields(type?.fields);
    this.modalService.open(modal, { size: 'lg', centered: true, scrollable: true });
  }

  saveRequest(modal: any, submitNow: boolean): void {
    if (!this.selectedType?.id) { this.toastr.error('No request type selected'); return; }
    if (!this.newRequest.title || !String(this.newRequest.title).trim()) {
      this.toastr.warning('Title is required');
      return;
    }
    if (this.dynamicFormRef && !this.dynamicFormRef.isValid()) {
      this.toastr.warning('Please fill in all required fields');
      return;
    }
    let formValues: any = {};
    if (this.dynamicFormRef) {
      try { formValues = JSON.parse(this.dynamicFormRef.getFormDataJson()); } catch { formValues = {}; }
    }
    const payload = {
      memoTypeId: this.selectedType.id,
      title: String(this.newRequest.title).trim(),
      description: this.newRequest.description,
      priority: this.newRequest.priority,
      amount: this.newRequest.amount,
      isUrgent: !!this.newRequest.isUrgent,
      expectedDate: this.newRequest.expectedDate || null,
      formValues
    };
    this.submitting = true;
    const obs = this.editingId
      ? this.requestService.updateRequest(this.editingId, payload)
      : this.requestService.createRequest(payload, submitNow);
    obs.subscribe({
      next: () => {
        this.toastr.success(this.editingId ? 'Request updated' : (submitNow ? 'Request submitted' : 'Draft saved'));
        this.submitting = false;
        modal.close();
        this.activeTab = 'mine';
        this.loadMine();
      },
      error: (err) => { this.submitting = false; this.toastr.error(err.error?.message || 'Failed'); }
    });
  }

  submitRequest(request: any): void {
    if (!confirm(`Submit "${request.title}" for approval?`)) return;
    this.requestService.submitRequest(request.id).subscribe({
      next: () => { this.toastr.success('Request submitted'); this.loadMine(); },
      error: (err) => this.toastr.error(err.error?.message || 'Failed to submit')
    });
  }

  cancelRequest(request: any): void {
    if (!confirm(`Cancel "${request.title}"?`)) return;
    this.requestService.cancelRequest(request.id).subscribe({
      next: () => { this.toastr.success('Request cancelled'); this.loadMine(); },
      error: (err) => this.toastr.error(err.error?.message || 'Failed to cancel')
    });
  }

  track(request: any, modal: any): void {
    this.trackingRequest = request;
    this.tracking = null;
    this.loadingTracking = true;
    this.modalService.open(modal, { size: 'lg', centered: true, scrollable: true });
    this.requestService.getTracking(request.id).subscribe({
      next: (t) => { this.tracking = t; this.loadingTracking = false; },
      error: () => { this.loadingTracking = false; this.toastr.error('Failed to load tracking'); }
    });
  }

  canEdit(request: any): boolean {
    return request?.status === 'DRAFT' || request?.status === 'REVISION_REQUESTED';
  }
  canSubmit(request: any): boolean {
    return request?.status === 'DRAFT';
  }
  canCancel(request: any): boolean {
    return request?.status === 'DRAFT';
  }

  statusBadge(status: string): string {
    switch (status) {
      case 'DRAFT': return 'badge-secondary';
      case 'PENDING_APPROVAL': case 'PENDING': return 'badge-warning';
      case 'APPROVED': return 'badge-success';
      case 'REJECTED': return 'badge-danger';
      case 'CANCELLED': return 'badge-secondary';
      case 'REVISION_REQUESTED': return 'badge-info';
      default: return 'badge-info';
    }
  }

  priorityBadge(priority: string): string {
    switch (priority) {
      case 'LOW': return 'badge-secondary';
      case 'MEDIUM': return 'badge-info';
      case 'HIGH': return 'badge-warning';
      case 'URGENT': return 'badge-danger';
      default: return 'badge-info';
    }
  }

  private mapFields(fields: any[]): any[] {
    return (fields || []).map((sf: any) => {
      const typeMap: Record<string, string> = {
        text: 'TEXT', textarea: 'TEXTAREA', number: 'NUMBER',
        money: 'CURRENCY', currency: 'CURRENCY', date: 'DATE', datetime: 'DATETIME',
        select: 'DROPDOWN', dropdown: 'DROPDOWN', multiselect: 'MULTI_SELECT', 'multi-select': 'MULTI_SELECT',
        radio: 'RADIO', checkbox: 'CHECKBOX'
      };
      const opts: string[] = Array.isArray(sf.options) ? sf.options : [];
      return {
        fieldKey: sf.key,
        fieldLabel: sf.label || sf.key,
        fieldType: typeMap[(sf.type || 'text').toLowerCase()] || 'TEXT',
        required: !!sf.required,
        placeholder: sf.placeholder || '',
        defaultValue: sf.defaultValue || '',
        options: opts.length ? JSON.stringify(opts) : ''
      };
    });
  }

  private parseJson(value: any): any {
    if (!value) return {};
    if (typeof value === 'object') return value;
    try { return JSON.parse(value) || {}; } catch { return {}; }
  }
}
