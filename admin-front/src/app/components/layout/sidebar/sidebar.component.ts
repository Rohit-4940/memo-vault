import {Component, OnInit} from '@angular/core';
import {RouterLink, RouterLinkActive} from '@angular/router';
import {SidebarService} from './sidebar.service';
import {MenuService} from '../../../shared-services/api-service/menu.service';
import {LocalStorageUtil} from '../../../@core/utils/local-storage-util';
import {NgClass, NgForOf, NgIf} from '@angular/common';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, NgClass, NgForOf, NgIf],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss'
})
export class SidebarComponent implements OnInit {
  isExpanded = true;
  menuItems: any[] = [];

  constructor(
    public sidebarService: SidebarService,
    private menuService: MenuService
  ) {
    this.sidebarService.isExpanded$.subscribe(v => this.isExpanded = v);
  }

  ngOnInit() {
    this.loadMenus();
  }

  private loadMenus() {
    const storage = LocalStorageUtil.getStorage();
    const roleId = storage?.roleId;
    if (roleId) {
      this.menuService.getMenusByRole(roleId).subscribe({
        next: (menus) => this.menuItems = menus,
        error: () => this.menuItems = this.getDefaultMenus()
      });
    } else {
      this.menuItems = this.getDefaultMenus();
    }
  }

  private getDefaultMenus(): any[] {
    return [
      { menuName: 'Dashboard', faIcon: 'fas fa-chart-pie', frontUrl: '/layout/dashboard' },
      { menuName: 'Users', faIcon: 'fas fa-users', frontUrl: '/layout/users' },
      { menuName: 'Role & Permission', faIcon: 'fas fa-shield-alt', frontUrl: '/layout/role-permission' },
      { menuName: 'Branch', faIcon: 'fas fa-building', frontUrl: '/layout/branch' },
      { menuName: 'Department', faIcon: 'fas fa-sitemap', frontUrl: '/layout/department' },
      { menuName: 'Memo Catalogue', faIcon: 'fas fa-folder-tree', frontUrl: '/layout/memo-catalogue' },
      { menuName: 'Memo Type', faIcon: 'fas fa-tags', frontUrl: '/layout/memo-type' },
      { menuName: 'Document List', faIcon: 'fas fa-list-check', frontUrl: '/layout/document-list' },
      { menuName: 'Budget', faIcon: 'fas fa-coins', frontUrl: '/layout/budget' },
      { menuName: 'Template', faIcon: 'fas fa-copy', frontUrl: '/layout/template' },
      { menuName: 'Transfer Document', faIcon: 'fas fa-right-left', frontUrl: '/layout/transfer-document' },
      { menuName: 'Document Compressor', faIcon: 'fas fa-compress', frontUrl: '/layout/document-compressor' },
      { menuName: 'Task Manager', faIcon: 'fas fa-list-check', frontUrl: '/layout/task-manager' },
      { menuName: 'Announcement', faIcon: 'fas fa-bullhorn', frontUrl: '/layout/announcement' },
      { menuName: 'Request For Budget', faIcon: 'fas fa-hand-holding-dollar', frontUrl: '/layout/request-budget' },
      { menuName: 'System Configuration', faIcon: 'fas fa-gears', frontUrl: '/layout/system-config' },
    ];
  }
}
