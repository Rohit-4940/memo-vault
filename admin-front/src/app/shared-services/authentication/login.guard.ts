import { CanActivateFn, Router } from '@angular/router';
import { LocalStorageUtil } from '../../@core/utils/local-storage-util';
import { inject } from '@angular/core';
import { LoadingService } from '../api-service/loading.service';

export const loginGuard: CanActivateFn = (route, state) => {
  const router = inject(Router);
  const loadingService = inject(LoadingService);

  loadingService.show();

  const localStorage = LocalStorageUtil.getStorage();
  const hasToken = localStorage?.at != null;

  loadingService.hide();

  if (hasToken) {
    router.navigate(['/layout']);
    return false;
  }
  return true;
};
