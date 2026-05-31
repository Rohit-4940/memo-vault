import { Component, OnInit } from '@angular/core';
import { NgForOf, NgIf } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { ToastrService } from 'ngx-toastr';
import { MemoWorkflowService } from '../../../shared-services/api-service/memo-workflow.service';
import { MemoTypeService } from '../../../shared-services/api-service/memo-type.service';
import { UserService } from '../../../shared-services/api-service/user.service';
import { RoleService } from '../../../shared-services/api-service/role.service';
import { MemoFormFieldService } from '../../../shared-services/api-service/memo-form-field.service';
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

  fieldTypes = ['TEXT', 'NUMBER', 'CURRENCY', 'DATE', 'DATETIME', 'DROPDOWN', 'MULTI_SELECT', 'RADIO', 'CHECKBOX', 'TEXTAREA'];

  constructor(
    private workflowService: MemoWorkflowService,
    private memoTypeService: MemoTypeService,
    private userService: UserService,
    private roleService: RoleService,
    private formFieldService: MemoFormFieldService,
    private toastr: ToastrService,
    private modalService: NgbModal
  ) {}

  emptyField(): any {
    return { fieldKey: '', fieldLabel: '', fieldType: 'TEXT', fieldOrder: 0, sectionName: '', required: false, readOnly: false, defaultValue: '', options: '', validationRules: '', conditionalLogic: '', placeholder: '', helpText: '', isActive: true };
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
      assigneeName: this.userBased
        ? (user ? user.name || user.userName : 'User ' + this.newStageUserId)
        : (role ? role.roleName : 'Role ' + this.newStageRoleId)
    };
    this.stages.push(stage);
    this.newStageUserId = null;
    this.newStageRoleId = null;
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

  loadFormFields() {
    if (!this.selectedMemoTypeId) return;
    this.formFieldService.getFieldsByMemoType(this.selectedMemoTypeId).subscribe({
      next: (d) => { this.formFields = d || []; this.resetFieldForm(); },
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
    modal.close();
    const field = { ...this.newField, fieldOrder: this.formFields.length + 1 };
    if (this.editingFieldId) {
      this.formFieldService.updateField(this.editingFieldId, this.selectedMemoTypeId!, field).subscribe({
        next: () => { this.toastr.success('Field updated'); this.loadFormFields(); },
        error: () => this.toastr.error('Failed to update field')
      });
    } else {
      this.formFieldService.createField(this.selectedMemoTypeId!, field).subscribe({
        next: () => { this.toastr.success('Field added'); this.loadFormFields(); },
        error: () => this.toastr.error('Failed to add field')
      });
    }
  }

  deleteField(id: number) {
    if (!confirm('Delete this field?')) return;
    this.formFieldService.deleteField(id).subscribe({
      next: () => { this.toastr.success('Field deleted'); this.loadFormFields(); },
      error: () => this.toastr.error('Failed to delete field')
    });
  }

  saveAllFormFields() {
    if (!this.selectedMemoTypeId) return;
    this.formFieldService.saveAllFields(this.selectedMemoTypeId, this.formFields).subscribe({
      next: () => { this.toastr.success('All fields saved'); this.loadFormFields(); },
      error: () => this.toastr.error('Failed to save fields')
    });
  }

  moveField(index: number, direction: number) {
    const target = index + direction;
    if (target < 0 || target >= this.formFields.length) return;
    [this.formFields[index], this.formFields[target]] = [this.formFields[target], this.formFields[index]];
  }
}
