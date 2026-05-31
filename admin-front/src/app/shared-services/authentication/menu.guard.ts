import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { MenuPermissionService } from '../api-service/menu-permission.service';

export const menuGuard: CanActivateFn = (route, state) => {
  const router = inject(Router);
  const menuPermission = inject(MenuPermissionService);

  if (menuPermission.isUrlAllowed(state.url)) {
    return true;
  }

  router.navigate(['/layout/dashboard']);
  return false;
};
