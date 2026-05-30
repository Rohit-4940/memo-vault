import { Component, OnInit, inject } from '@angular/core';
import { NgForOf, NgIf } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { RoleService } from '../../../shared-services/api-service/role.service';
import { MenuService } from '../../../shared-services/api-service/menu.service';
import { ToastrService } from 'ngx-toastr';

@Component({
  selector: 'app-role-permission',
  standalone: true,
  imports: [NgForOf, NgIf, FormsModule, NgbModalModule],
  templateUrl: './role-permission.component.html',
  styleUrl: './role-permission.component.scss'
})
export class RolePermissionComponent implements OnInit {
  roles: any[] = [];
  menus: any[] = [];
  selectedRole: any = { roleName: '', roleType: 'USER', roleAccess: 'OWN', menuIds: [] };
  isEditing = false;

  private modalService = inject(NgbModal);
  constructor(
    private roleService: RoleService,
    private menuService: MenuService,
    private toastr: ToastrService
  ) {}

  ngOnInit() { this.loadRoles(); this.loadMenus(); }

  loadRoles() {
    this.roleService.getAllRoles().subscribe({
      next: (data) => this.roles = data,
      error: () => this.toastr.error('Failed to load roles')
    });
  }

  loadMenus() {
    this.menuService.getAllMenus().subscribe({
      next: (data) => this.menus = data,
      error: () => {}
    });
  }

  openCreateModal(content: any) {
    this.isEditing = false;
    this.selectedRole = { roleName: '', roleType: 'USER', roleAccess: 'OWN', isRoleApprovable: false, menuIds: [] };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  openEditModal(content: any, role: any) {
    this.isEditing = true;
    this.selectedRole = {
      id: role.id, roleName: role.roleName, roleType: role.roleType || 'USER',
      roleAccess: role.roleAccess || 'OWN', isRoleApprovable: role.isRoleApprovable || false,
      roleApprovalLimitAmount: role.roleApprovalLimitAmount, menuIds: [...(role.menuIds || [])]
    };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  toggleMenu(menuId: number) {
    const idx = this.selectedRole.menuIds.indexOf(menuId);
    if (idx > -1) this.selectedRole.menuIds.splice(idx, 1);
    else this.selectedRole.menuIds.push(menuId);
  }

  isMenuSelected(menuId: number): boolean {
    return this.selectedRole.menuIds.includes(menuId);
  }

  saveRole() {
    if (this.isEditing) {
      this.roleService.updateRole(this.selectedRole.id, this.selectedRole).subscribe({
        next: () => { this.toastr.success('Role updated'); this.modalService.dismissAll(); this.loadRoles(); },
        error: (err) => this.toastr.error(err.error?.message || 'Update failed')
      });
    } else {
      this.roleService.createRole(this.selectedRole).subscribe({
        next: () => { this.toastr.success('Role created'); this.modalService.dismissAll(); this.loadRoles(); },
        error: (err) => this.toastr.error(err.error?.message || 'Create failed')
      });
    }
  }

  deleteRole(id: number) {
    if (confirm('Delete this role? This will affect all users with this role.')) {
      this.roleService.deleteRole(id).subscribe({
        next: () => { this.toastr.success('Role deleted'); this.loadRoles(); },
        error: () => this.toastr.error('Delete failed')
      });
    }
  }
}
