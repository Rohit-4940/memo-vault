import { Component, OnInit, TemplateRef, inject } from '@angular/core';
import { NgForOf, NgIf, NgTemplateOutlet, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { UserService } from '../../../shared-services/api-service/user.service';
import { RoleService } from '../../../shared-services/api-service/role.service';
import { ToastrService } from 'ngx-toastr';

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
  searchTerm = '';
  selectedUser: any = { name: '', username: '', email: '', roleId: null, branchIds: [] };
  isEditing = false;

  private modalService = inject(NgbModal);
  constructor(
    private userService: UserService,
    private roleService: RoleService,
    private toastr: ToastrService
  ) {}

  ngOnInit() { this.loadUsers(); this.loadRoles(); }

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

  get filteredUsers() {
    return this.users.filter(u =>
      u.name?.toLowerCase().includes(this.searchTerm.toLowerCase()) ||
      u.username?.toLowerCase().includes(this.searchTerm.toLowerCase()) ||
      u.email?.toLowerCase().includes(this.searchTerm.toLowerCase())
    );
  }

  openCreateModal(content: TemplateRef<any>) {
    this.isEditing = false;
    this.selectedUser = { name: '', username: '', email: '', roleId: null, branchIds: [] };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  openEditModal(content: TemplateRef<any>, user: any) {
    this.isEditing = true;
    this.selectedUser = { ...user, roleId: user.roleId };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  saveUser() {
    if (this.isEditing) {
      this.userService.updateUser(this.selectedUser.id, this.selectedUser).subscribe({
        next: () => { this.toastr.success('User updated'); this.modalService.dismissAll(); this.loadUsers(); },
        error: (err) => this.toastr.error(err.error?.message || 'Update failed')
      });
    } else {
      this.userService.createUser(this.selectedUser).subscribe({
        next: () => { this.toastr.success('User created'); this.modalService.dismissAll(); this.loadUsers(); },
        error: (err) => this.toastr.error(err.error?.message || 'Create failed')
      });
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
