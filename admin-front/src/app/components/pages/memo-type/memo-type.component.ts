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
    { key: 'fieldCount', label: 'Fields', sortable: false, width: '90px' },
    { key: 'status', label: 'Status', sortable: true, filterable: true, filterType: 'select', filterOptions: [{label:'Active',value:'ACTIVE'},{label:'Inactive',value:'INACTIVE'}] }
  ];
  /** Phase 4c/6 — widget types the dynamic form renderer understands. */
  fieldTypes = ['text', 'textarea', 'number', 'money', 'date', 'datetime', 'select', 'multiselect', 'radio', 'checkbox'];
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
        this.data = (res.content || []).map((t: any) => ({ ...t, fieldCount: this.parseSchema(t.formSchema).length }));
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
    this.selectedMemoType = { name: '', description: '', status: 'ACTIVE', schemaFields: [] };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  openEditModal(content: any, memoType: any) {
    this.isEditing = true;
    this.selectedMemoType = { ...memoType, schemaFields: this.parseSchema(memoType.formSchema) };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  /** Parses a memo type's form_schema JSON string into editable field rows (tolerant of null/bad JSON). */
  parseSchema(formSchema: string | null | undefined): any[] {
    if (!formSchema) return [];
    try {
      const parsed = JSON.parse(formSchema);
      if (!Array.isArray(parsed)) return [];
      return parsed.map((f: any) => ({
        key: f.key || '',
        label: f.label || '',
        type: f.type || 'text',
        required: !!f.required,
        placeholder: f.placeholder || '',
        defaultValue: f.defaultValue || '',
        options: Array.isArray(f.options) ? f.options : []
      }));
    } catch {
      return [];
    }
  }

  addSchemaField() {
    if (!this.selectedMemoType.schemaFields) this.selectedMemoType.schemaFields = [];
    this.selectedMemoType.schemaFields.push({ key: '', label: '', type: 'text', required: false, placeholder: '', defaultValue: '', options: [] });
  }

  removeSchemaField(index: number) {
    this.selectedMemoType.schemaFields?.splice(index, 1);
  }

  isOptionType(type: string): boolean {
    return ['select', 'multiselect', 'radio', 'checkbox'].includes((type || '').toLowerCase());
  }

  optionsToText(field: any): string {
    return (field.options || []).join(', ');
  }

  textToOptions(text: string): string[] {
    return (text || '').split(',').map((s: string) => s.trim()).filter((s: string) => s.length > 0);
  }

  saveMemoType() {
    const fields = this.selectedMemoType.schemaFields || [];
    const seen = new Set<string>();
    for (const f of fields) {
      const key = (f.key || '').trim();
      if (!key) { this.toastr.error('Each form field needs a key'); return; }
      if (!(f.label || '').trim()) { this.toastr.error(`Field '${key}' needs a label`); return; }
      if (seen.has(key)) { this.toastr.error(`Duplicate field key '${key}'`); return; }
      seen.add(key);
    }

    const payload: any = { ...this.selectedMemoType };
    delete payload.schemaFields;
    payload.formSchema = fields.length
      ? JSON.stringify(fields.map((f: any) => ({
          key: (f.key || '').trim(),
          label: (f.label || '').trim(),
          type: f.type || 'text',
          required: !!f.required,
          placeholder: f.placeholder || '',
          defaultValue: f.defaultValue || '',
          options: f.options || []
        })))
      // An empty array (not null) is sent so that clearing every field is persisted by the
      // backend update path, which only applies form_schema when the value is non-null.
      : '[]';

    const obs = this.isEditing
      ? this.memoTypeService.updateMemoType(payload.id, payload)
      : this.memoTypeService.createMemoType(payload);
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
