import { Injectable } from '@angular/core';
import { Resolve, Router } from '@angular/router';
import { LocalStorageUtil } from '../../@core/utils/local-storage-util';

@Injectable({
  providedIn: 'root'
})
export class AuthResolver implements Resolve<boolean> {
  constructor(private router: Router) {}

  resolve(): boolean {
    const localStorage = LocalStorageUtil.getStorage();
    const hasToken = localStorage?.at != null;

    if (!hasToken) {
      this.router.navigate(['/login']);
      return false;
    }

    return true;
  }
}
