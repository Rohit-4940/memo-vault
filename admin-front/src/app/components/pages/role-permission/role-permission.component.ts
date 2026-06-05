import { Component, OnInit, inject } from '@angular/core';
import { NgForOf, NgIf } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { RoleService } from '../../../shared-services/api-service/role.service';
import { MenuService } from '../../../shared-services/api-service/menu.service';
import { ToastrService } from 'ngx-toastr';
import { AdvancedTableComponent, TableColumn } from '../../../shared-components/advanced-table/advanced-table.component';

@Component({
  selector: 'app-role-permission',
  standalone: true,
  imports: [NgForOf, NgIf, FormsModule, NgbModalModule, AdvancedTableComponent],
  templateUrl: './role-permission.component.html',
  styleUrl: './role-permission.component.scss'
})
export class RolePermissionComponent implements OnInit {
  columns: TableColumn[] = [
    { key: 'id', label: 'ID', sortable: true, width: '80px' },
    { key: 'roleName', label: 'Role Name', sortable: true, filterable: true, filterType: 'text' },
    { key: 'roleType', label: 'Type', sortable: true, filterable: true, filterType: 'select', filterOptions: [{label:'Super Admin',value:'SUPER_ADMIN'},{label:'Admin',value:'ADMIN'},{label:'Maker',value:'MAKER'},{label:'Approval',value:'APPROVAL'},{label:'Committee',value:'COMMITTEE'},{label:'User',value:'USER'}] },
    { key: 'roleAccess', label: 'Access', sortable: true, filterable: true, filterType: 'select', filterOptions: [{label:'All',value:'ALL'},{label:'Own',value:'OWN'},{label:'Specific',value:'SPECIFIC'}] },
    { key: 'isRoleApprovable', label: 'Approvable', sortable: true, filterable: true, filterType: 'boolean' },
    { key: 'roleApprovalLimitAmount', label: 'Approval Limit', sortable: true, type: 'number' },
    { key: 'menuCount', label: 'Menus', sortable: false }
  ];
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
  menus: any[] = [];
  selectedRole: any = { roleName: '', roleType: 'USER', roleAccess: 'OWN', menuIds: [] };
  isEditing = false;

  private modalService = inject(NgbModal);
  constructor(
    private roleService: RoleService,
    private menuService: MenuService,
    private toastr: ToastrService
  ) {}

  ngOnInit() { this.loadData(); this.loadMenus(); }

  loadData() {
    this.loading = true;
    const filterObj: any = {};
    this.filters.forEach((v, k) => { if (v) filterObj[k] = v; });
    this.roleService.getPaginated(this.searchQuery, this.pageNumber, this.pageSize, this.sortBy, this.sortDir, filterObj).subscribe({
      next: (res) => {
        this.data = res.content || [];
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
        next: () => { this.toastr.success('Role updated'); this.modalService.dismissAll(); this.loadData(); },
        error: (err) => this.toastr.error(err.error?.message || 'Update failed')
      });
    } else {
      this.roleService.createRole(this.selectedRole).subscribe({
        next: () => { this.toastr.success('Role created'); this.modalService.dismissAll(); this.loadData(); },
        error: (err) => this.toastr.error(err.error?.message || 'Create failed')
      });
    }
  }

  deleteRole(id: number) {
    if (confirm('Delete this role? This will affect all users with this role.')) {
      this.roleService.deleteRole(id).subscribe({
        next: () => { this.toastr.success('Role deleted'); this.loadData(); },
        error: () => this.toastr.error('Delete failed')
      });
    }
  }
}
