import { Injectable } from '@angular/core';
import { MenuService } from './menu.service';
import { LocalStorageUtil } from '../../@core/utils/local-storage-util';

@Injectable({ providedIn: 'root' })
export class MenuPermissionService {
  private allowedUrls: string[] | null = null;

  constructor(private menuService: MenuService) {}

  loadPermissions(): Promise<void> {
    const storage = LocalStorageUtil.getStorage();
    const roleId = storage?.roleId;
    if (!roleId) {
      this.allowedUrls = ['/layout/dashboard'];
      return Promise.resolve();
    }
    return new Promise((resolve) => {
      this.menuService.getMenusByRole(roleId).subscribe({
        next: (menus) => {
          this.allowedUrls = (menus || []).map((m: any) => m.frontUrl).filter(Boolean);
          if (!this.allowedUrls.includes('/layout/dashboard')) {
            this.allowedUrls.push('/layout/dashboard');
          }
          resolve();
        },
        error: () => {
          this.allowedUrls = ['/layout/dashboard'];
          resolve();
        }
      });
    });
  }

  isUrlAllowed(url: string): boolean {
    if (!this.allowedUrls) return true;
    if (url === '/layout/dashboard') return true;
    return this.allowedUrls.some(allowed => url.startsWith(allowed));
  }

  getAllowedUrls(): string[] {
    return this.allowedUrls || ['/layout/dashboard'];
  }

  setAllowedUrls(urls: string[]) {
    this.allowedUrls = urls;
    if (!this.allowedUrls.includes('/layout/dashboard')) {
      this.allowedUrls.push('/layout/dashboard');
    }
  }
}
