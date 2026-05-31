import { Component, OnInit, TemplateRef, inject } from '@angular/core';
import { NgForOf, NgIf, NgTemplateOutlet, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { UserService } from '../../../shared-services/api-service/user.service';
import { FileService } from '../../../shared-services/api-service/file.service';
import { RoleService } from '../../../shared-services/api-service/role.service';
import { BranchService } from '../../../shared-services/api-service/branch.service';
import { ToastrService } from 'ngx-toastr';
import { HttpClient } from '@angular/common/http';
import { ApiConfig } from '../../../@core/utils/api/ApiConfig';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [NgForOf, NgIf, FormsModule, NgbModalModule, DatePipe],
  templateUrl: './users.component.html',
  styleUrl: './users.component.scss'
})
export class UsersComponent implements OnInit {
  users: any[] = [];
  roles: any[] = [];
  branches: any[] = [];
  departments: any[] = [];
  searchTerm = '';
  selectedUser: any = {
    name: '', username: '', email: '', password: '', roleId: null,
    branchIds: [], departmentId: null,
    contactNo: '', address: '',
    sendEmail: true, forcePasswordChange: true
  };
  isEditing = false;
  signatureFile: File | null = null;
  signaturePreview: string | null = null;

  private modalService = inject(NgbModal);

  constructor(
    private userService: UserService,
    private fileService: FileService,
    private roleService: RoleService,
    private branchService: BranchService,
    private http: HttpClient,
    private toastr: ToastrService
  ) {}

  ngOnInit() { this.loadUsers(); this.loadRoles(); this.loadBranches(); this.loadDepartments(); }

  loadUsers() {
    this.userService.getAllUsers().subscribe({
      next: (data) => this.users = data,
      error: () => this.toastr.error('Failed to load users')
    });
  }

  loadRoles() {
    this.roleService.getAllRoles().subscribe({
      next: (data) => this.roles = data,
      error: () => {}
    });
  }

  loadBranches() {
    this.branchService.getAllBranches().subscribe({
      next: (data) => this.branches = data,
      error: () => {}
    });
  }

  loadDepartments() {
    this.http.get<any[]>(`${ApiConfig.URL}/api/v1/departments`).subscribe({
      next: (data) => this.departments = data || [],
      error: () => {}
    });
  }

  get filteredUsers() {
    return this.users.filter(u =>
      u.name?.toLowerCase().includes(this.searchTerm.toLowerCase()) ||
      u.username?.toLowerCase().includes(this.searchTerm.toLowerCase()) ||
      u.email?.toLowerCase().includes(this.searchTerm.toLowerCase())
    );
  }

  openCreateModal(content: TemplateRef<any>) {
    this.isEditing = false;
    this.signatureFile = null;
    this.signaturePreview = null;
    this.selectedUser = {
      name: '', username: '', email: '', password: '', roleId: null,
      branchIds: [], departmentId: null,
      contactNo: '', address: '',
      sendEmail: true, forcePasswordChange: true
    };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  openEditModal(content: TemplateRef<any>, user: any) {
    this.isEditing = true;
    this.signatureFile = null;
    this.signaturePreview = null;
    this.selectedUser = {
      ...user,
      roleId: user.roleId || user.role?.id || null,
      branchIds: user.branchIds || user.branches?.map((b: any) => b.id) || [],
      sendEmail: false,
      forcePasswordChange: false
    };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  onSignatureSelected(event: any) {
    const file = event.target.files[0];
    if (file) {
      this.signatureFile = file;
      const reader = new FileReader();
      reader.onload = (e) => this.signaturePreview = e.target?.result as string;
      reader.readAsDataURL(file);
    }
  }

  saveUser() {
    const payload: any = {
      name: this.selectedUser.name,
      username: this.selectedUser.username,
      email: this.selectedUser.email,
      roleId: Number(this.selectedUser.roleId),
      branchIds: this.selectedUser.branchIds,
      sendEmail: this.selectedUser.sendEmail,
      forcePasswordChange: this.selectedUser.forcePasswordChange
    };

    if (this.selectedUser.password) {
      payload.password = this.selectedUser.password;
    }
    if (this.selectedUser.contactNo) payload.contactNo = this.selectedUser.contactNo;
    if (this.selectedUser.address) payload.address = this.selectedUser.address;
    if (this.selectedUser.departmentId) payload.departmentId = Number(this.selectedUser.departmentId);

    const doSave = () => {
      if (this.isEditing) {
        this.userService.updateUser(this.selectedUser.id, payload).subscribe({
          next: () => { this.toastr.success('User updated'); this.modalService.dismissAll(); this.loadUsers(); },
          error: (err) => this.toastr.error(err.error?.message || 'Update failed')
        });
      } else {
        this.userService.createUser(payload).subscribe({
          next: () => { this.toastr.success('User created'); this.modalService.dismissAll(); this.loadUsers(); },
          error: (err) => this.toastr.error(err.error?.message || 'Create failed')
        });
      }
    };

    if (this.signatureFile) {
      this.fileService.uploadSignature(this.signatureFile).subscribe({
        next: () => doSave(),
        error: () => { this.toastr.warning('Signature upload failed, saving user without signature'); doSave(); }
      });
    } else {
      doSave();
    }
  }

  deleteUser(id: number) {
    if (confirm('Delete this user?')) {
      this.userService.deleteUser(id).subscribe({
        next: () => { this.toastr.success('User deleted'); this.loadUsers(); },
        error: () => this.toastr.error('Delete failed')
      });
    }
  }
}
