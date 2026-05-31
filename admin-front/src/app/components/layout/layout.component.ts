import {Component, OnInit, OnDestroy} from '@angular/core';
import {Router, RouterLink, RouterOutlet} from '@angular/router';
import {AsyncPipe, NgClass, NgIf} from '@angular/common';
import {SidebarComponent} from './sidebar/sidebar.component';
import {SidebarService} from './sidebar/sidebar.service';
import {FooterComponent} from './footer/footer.component';
import {ChatWidgetComponent} from '../widgets/chat-widget/chat-widget.component';
import {LocalStorageUtil} from '../../@core/utils/local-storage-util';
import {environment} from '../../../environments/environment';
import {LoadingService} from '../../shared-services/api-service/loading.service';
import {NotificationService} from '../../shared-services/api-service/notification.service';
import {AuthService} from '../../shared-services/api-service/auth.service';
import {Observable, Subject, of} from 'rxjs';
import {takeUntil, catchError} from 'rxjs/operators';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterOutlet, FooterComponent, RouterLink, NgClass, NgIf, SidebarComponent, AsyncPipe, ChatWidgetComponent],
  templateUrl: './layout.component.html',
  styleUrl: './layout.component.scss'
})
export class LayoutComponent implements OnInit, OnDestroy {
  isExpanded = true;
  userName = 'User';
  userEmail = '';
  roleTitle = '';
  showDropdown = false;
  pendingCount = 0;
  isLoading$: Observable<boolean>;
  private destroy$ = new Subject<void>();

  constructor(
    private sidebarService: SidebarService,
    private loadingService: LoadingService,
    private notificationService: NotificationService,
    private authService: AuthService,
    private router: Router
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
    this.notificationService.pendingCount$.pipe(takeUntil(this.destroy$)).subscribe(
      count => this.pendingCount = count
    );
    this.notificationService.refreshPendingCount();
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
    this.authService.logout().pipe(
      catchError(() => of(null))
    ).subscribe(() => {
      this.clearSession();
    });
  }

  private clearSession() {
    LocalStorageUtil.removeKey(environment.LOCAL_STORAGE_NAME);
    LocalStorageUtil.clearStorage();
    this.router.navigate(['/login']);
  }
}
