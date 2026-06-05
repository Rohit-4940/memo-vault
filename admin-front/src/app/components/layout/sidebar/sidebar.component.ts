import {Component, OnInit} from '@angular/core';
import {RouterLink, RouterLinkActive} from '@angular/router';
import {SidebarService} from './sidebar.service';
import {MenuService} from '../../../shared-services/api-service/menu.service';
import {MenuPermissionService} from '../../../shared-services/api-service/menu-permission.service';
import {LocalStorageUtil} from '../../../@core/utils/local-storage-util';
import {NgClass, NgForOf, NgIf} from '@angular/common';
import {TranslatePipe} from '@ngx-translate/core';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, NgClass, NgForOf, NgIf, TranslatePipe],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss'
})
export class SidebarComponent implements OnInit {
  isExpanded = true;
  menuItems: any[] = [];

  constructor(
    public sidebarService: SidebarService,
    private menuService: MenuService,
    private menuPermission: MenuPermissionService
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
        next: (menus) => {
          this.menuItems = menus || [];
          this.menuPermission.setAllowedUrls((menus || []).map((m: any) => m.frontUrl).filter(Boolean));
        },
        error: () => this.menuItems = []
      });
    } else {
      this.menuItems = [];
    }
  }
}
