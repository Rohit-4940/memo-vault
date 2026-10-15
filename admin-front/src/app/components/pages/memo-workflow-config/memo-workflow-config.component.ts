import { Component, OnInit } from '@angular/core';
import { NgForOf, NgIf } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { ToastrService } from 'ngx-toastr';
import { MemoWorkflowService } from '../../../shared-services/api-service/memo-workflow.service';
import { MemoTypeService } from '../../../shared-services/api-service/memo-type.service';
import { UserService } from '../../../shared-services/api-service/user.service';
import { RoleService } from '../../../shared-services/api-service/role.service';
import { LocalStorageUtil } from '../../../@core/utils/local-storage-util';

@Component({
  selector: 'app-memo-workflow-config',
  standalone: true,
  imports: [NgForOf, NgIf, FormsModule, NgbModalModule],
  templateUrl: './memo-workflow-config.component.html',
  styleUrl: './memo-workflow-config.component.scss'
})
export class MemoWorkflowConfigComponent implements OnInit {
  memoTypes: any[] = [];
  users: any[] = [];
  roles: any[] = [];
  selectedMemoTypeId: number | null = null;

  config: any = null;
  configId: number | null = null;
  stages: any[] = [];
  permissions: any[] = [];
  formFields: any[] = [];
  newField: any = this.emptyField();
  editingFieldId: number | null = null;

  selectedTab = 'config';
  isAdmin = false;
  newStageUserId: number | null = null;
  newStageRoleId: number | null = null;
  userBased = true;
  /** Phase 6c — guards applied to stages added from the panel. */
  newStageRequireComment = true;
  newStageRequireDocument = false;

  /** Phase 4c/6 — the widget hints stored on MemoType.formSchema (lowercase, matches the backend/FormField). */
  fieldTypes = ['text', 'textarea', 'number', 'money', 'date', 'datetime', 'select', 'multiselect', 'radio', 'checkbox'];

  constructor(
    private workflowService: MemoWorkflowService,
    private memoTypeService: MemoTypeService,
    private userService: UserService,
    private roleService: RoleService,
    private toastr: ToastrService,
    private modalService: NgbModal
  ) {}

  /** Field row shape used by this tab; it is mapped to/from the backend FormField JSON on load/save. */
  emptyField(): any {
    return { id: null, fieldKey: '', fieldLabel: '', fieldType: 'text', required: false, defaultValue: '', placeholder: '', options: '' };
  }

  ngOnInit() {
    const storage = LocalStorageUtil.getStorage();
    this.isAdmin = storage.roleName === 'ADMIN' || storage.roleName === 'SUPER_ADMIN';
    this.memoTypeService.getAllMemoTypes().subscribe({ next: (d) => this.memoTypes = d });
    this.userService.getAllUsers().subscribe({ next: (d) => this.users = d });
    this.roleService.getAllRoles().subscribe({ next: (d) => this.roles = d });
  }

  onMemoTypeChange() {
    if (!this.selectedMemoTypeId) { this.resetAll(); return; }
    this.loadConfig();
  }

  resetAll() {
    this.config = null;
    this.configId = null;
    this.stages = [];
    this.permissions = [];
    this.formFields = [];
    this.newField = this.emptyField();
    this.editingFieldId = null;
  }

  loadConfig() {
    this.resetAll();
    this.workflowService.getConfig(this.selectedMemoTypeId!).subscribe({
      next: (data) => {
        if (data) {
          this.config = data;
          this.configId = data.id;
          this.userBased = data.assignmentType === 'USER_BASED';
          this.loadStages();
        } else {
          this.config = { assignmentType: 'USER_BASED', flowType: 'SEQUENTIAL', requiredFields: '', isActive: true };
        }
        this.loadPermissions();
      },
      error: () => {
        this.config = { assignmentType: 'USER_BASED', flowType: 'SEQUENTIAL', requiredFields: '', isActive: true };
        this.loadPermissions();
      }
    });
  }

  loadStages() {
    if (!this.configId) return;
    this.workflowService.getStages(this.configId).subscribe({
      next: (d) => this.stages = d || [],
      error: () => this.stages = []
    });
  }

  loadPermissions() {
    if (!this.selectedMemoTypeId) return;
    this.workflowService.getPermissions(this.selectedMemoTypeId).subscribe({
      next: (d) => this.permissions = d || [],
      error: () => this.permissions = []
    });
  }

  saveConfig() {
    if (!this.selectedMemoTypeId) return;
    this.config.assignmentType = this.userBased ? 'USER_BASED' : 'ROLE_BASED';
    this.workflowService.saveConfig(this.selectedMemoTypeId, this.config).subscribe({
      next: (res) => {
        this.toastr.success('Workflow config saved');
        this.config = res;
        this.configId = res.id;
        this.loadStages();
      },
      error: () => this.toastr.error('Failed to save config')
    });
  }

  addStage() {
    if (!this.configId) {
      this.toastr.warning('Save the workflow config first');
      return;
    }
    if (this.userBased && !this.newStageUserId) return;
    if (!this.userBased && !this.newStageRoleId) return;

    const user = this.users.find(u => u.id === this.newStageUserId);
    const role = this.roles.find(r => r.id === this.newStageRoleId);
    const stage: any = {
      stageOrder: this.stages.length + 1,
      stageName: '',
      assigneeType: this.userBased ? 'USER' : 'ROLE',
      assigneeId: this.userBased ? this.newStageUserId : this.newStageRoleId,
      requireComment: this.newStageRequireComment,
      requireDocument: this.newStageRequireDocument,
      assigneeName: this.userBased
        ? (user ? user.name || user.userName : 'User ' + this.newStageUserId)
        : (role ? role.roleName : 'Role ' + this.newStageRoleId)
    };
    this.stages.push(stage);
    this.newStageUserId = null;
    this.newStageRoleId = null;
    this.newStageRequireComment = true;
    this.newStageRequireDocument = false;
  }

  removeStage(index: number) {
    this.stages.splice(index, 1);
  }

  saveStages() {
    if (!this.configId) return;
    this.workflowService.saveStages(this.configId, this.stages).subscribe({
      next: () => {
        this.toastr.success('Stages saved');
        this.loadStages();
      },
      error: () => this.toastr.error('Failed to save stages')
    });
  }

  togglePermission(perm: any, field: string) {
    perm[field] = !perm[field];
  }

  addPermission(roleId: number) {
    if (!this.selectedMemoTypeId) return;
    const already = this.permissions.some(p => p.role?.id === roleId);
    if (already) { this.toastr.info('Permission already exists for this role'); return; }
    const role = this.roles.find(r => r.id === roleId);
    this.permissions.push({
      role: { id: roleId },
      canCreate: false,
      canEdit: false,
      canApprove: false,
      canReject: false,
      canView: true
    });
  }

  removePermission(index: number) {
    this.permissions.splice(index, 1);
  }

  savePermissions() {
    if (!this.selectedMemoTypeId) return;
    this.workflowService.savePermissions(this.selectedMemoTypeId, this.permissions).subscribe({
      next: () => {
        this.toastr.success('Permissions saved');
        this.loadPermissions();
      },
      error: () => this.toastr.error('Failed to save permissions')
    });
  }

  switchTab(tab: string) {
    this.selectedTab = tab;
    if (tab === 'form-fields') this.loadFormFields();
  }

  getUnassignedRoles(): any[] {
    const assignedIds = this.permissions.map(p => p.role?.id);
    return this.roles.filter(r => !assignedIds.includes(r.id));
  }

  getUserDisplay(user: any): string {
    if (!user) return '';
    return user.name || user.userName || '';
  }

  /** Loads the memo type's real form schema (Phase 4c) into the editable field rows. */
  loadFormFields() {
    if (!this.selectedMemoTypeId) return;
    this.memoTypeService.getFormSchema(this.selectedMemoTypeId).subscribe({
      next: (fields) => {
        this.formFields = (fields || []).map((f: any, i: number) => ({
          id: i,
          fieldKey: f.key || '',
          fieldLabel: f.label || '',
          fieldType: f.type || 'text',
          required: !!f.required,
          defaultValue: f.defaultValue || '',
          placeholder: f.placeholder || '',
          options: (f.options && f.options.length) ? JSON.stringify(f.options) : ''
        }));
        this.resetFieldForm();
      },
      error: () => this.formFields = []
    });
  }

  resetFieldForm() {
    this.newField = this.emptyField();
    this.editingFieldId = null;
  }

  openFieldModal(modal: any) {
    this.resetFieldForm();
    this.modalService.open(modal, { size: 'lg', centered: true, scrollable: true, windowClass: 'field-form-modal' });
  }

  editFieldInModal(modal: any, field: any) {
    this.editingFieldId = field.id;
    this.newField = { ...field };
    this.modalService.open(modal, { size: 'lg', centered: true, scrollable: true, windowClass: 'field-form-modal' });
  }

  saveFieldClose(modal: any) {
    if (!this.newField.fieldKey || !this.newField.fieldLabel) {
      this.toastr.warning('Field key and label are required');
      return;
    }
    const key = String(this.newField.fieldKey).trim();
    const duplicate = this.formFields.some((f, i) => f.id !== this.editingFieldId && String(f.fieldKey).trim() === key);
    if (duplicate) {
      this.toastr.warning(`Field key '${key}' is already used`);
      return;
    }
    modal.close();
    if (this.editingFieldId !== null && this.editingFieldId !== undefined) {
      const idx = this.formFields.findIndex(f => f.id === this.editingFieldId);
      if (idx >= 0) this.formFields[idx] = { ...this.newField, id: this.editingFieldId };
    } else {
      this.formFields.push({ ...this.newField, id: this.nextFieldId() });
    }
    this.persistFields();
  }

  deleteField(id: number) {
    if (!confirm('Delete this field?')) return;
    this.formFields = this.formFields.filter(f => f.id !== id);
    this.persistFields();
  }

  saveAllFormFields() {
    this.persistFields();
  }

  private nextFieldId(): number {
    return this.formFields.reduce((max, f) => Math.max(max, Number(f.id) || 0), 0) + 1;
  }

  /** Parses the options input (JSON array, or comma-separated fallback) into a string list. */
  private parseOptions(options: any): string[] {
    if (!options) return [];
    if (Array.isArray(options)) return options;
    try {
      const parsed = JSON.parse(options);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return String(options).split(',').map((s: string) => s.trim()).filter((s: string) => s.length > 0);
    }
  }

  /** Persists the field rows to the memo type's form_schema JSON via the memo-types API. */
  private persistFields() {
    if (!this.selectedMemoTypeId) return;
    const type = this.memoTypes.find(t => t.id === Number(this.selectedMemoTypeId));
    if (!type) { this.toastr.error('Memo type not found'); return; }
    const schema = this.formFields.map((f: any) => ({
      key: String(f.fieldKey || '').trim(),
      label: String(f.fieldLabel || '').trim(),
      type: f.fieldType || 'text',
      required: !!f.required,
      placeholder: f.placeholder || '',
      defaultValue: f.defaultValue || '',
      options: this.parseOptions(f.options)
    }));
    this.memoTypeService.updateMemoType(type.id, { ...type, formSchema: JSON.stringify(schema) }).subscribe({
      next: () => { this.toastr.success('Form fields saved'); this.loadFormFields(); },
      error: (err) => this.toastr.error(err.error?.message || 'Failed to save fields')
    });
  }

  moveField(index: number, direction: number) {
    const target = index + direction;
    if (target < 0 || target >= this.formFields.length) return;
    [this.formFields[index], this.formFields[target]] = [this.formFields[target], this.formFields[index]];
  }
}
