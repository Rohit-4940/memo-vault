import {Component, OnInit, OnDestroy} from '@angular/core';
import {RouterLink, RouterOutlet} from '@angular/router';
import {AsyncPipe, NgClass, NgIf} from '@angular/common';
import {SidebarComponent} from './sidebar/sidebar.component';
import {SidebarService} from './sidebar/sidebar.service';
import {FooterComponent} from './footer/footer.component';
import {LocalStorageUtil} from '../../@core/utils/local-storage-util';
import {environment} from '../../../environments/environment.imepay';
import {LoadingService} from '../../shared-services/api-service/loading.service';
import {Observable, Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';
import {jwtDecode} from 'jwt-decode';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterOutlet, FooterComponent, RouterLink, NgClass, NgIf, SidebarComponent, AsyncPipe, FooterComponent],
  templateUrl: './layout.component.html',
  styleUrl: './layout.component.scss'
})
export class LayoutComponent implements OnInit, OnDestroy {
  isExpanded = true;
  userName = 'User';
  userEmail = '';
  roleTitle = '';
  showDropdown = false;
  isLoading$: Observable<boolean>;
  private destroy$ = new Subject<void>();

  constructor(
    private sidebarService: SidebarService,
    private loadingService: LoadingService
  ) {
    this.sidebarService.isExpanded$.pipe(takeUntil(this.destroy$)).subscribe(
      (isExpanded) => this.isExpanded = isExpanded
    );
    this.isLoading$ = this.loadingService.loading$;
  }

  ngOnInit() {
    const storage = LocalStorageUtil.getStorage();
    this.userName = storage.username || 'User';
    this.userEmail = storage.email || '';
    this.roleTitle = storage.roleName || '';
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  toggleSidebar() {
    this.sidebarService.toggleSidebar();
  }

  viewProfile() {}

  logout() {
    LocalStorageUtil.removeKey(environment.LOCAL_STORAGE_NAME);
    LocalStorageUtil.clearStorage();
  }
}
