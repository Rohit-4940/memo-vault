import {Component, OnInit, OnDestroy, ViewChild, inject} from '@angular/core';
import {Router, RouterLink, RouterOutlet} from '@angular/router';
import {AsyncPipe, NgClass, NgIf, DatePipe} from '@angular/common';
import {NgbModal, NgbModalModule} from '@ng-bootstrap/ng-bootstrap';
import {SidebarComponent} from './sidebar/sidebar.component';
import {SidebarService} from './sidebar/sidebar.service';
import {FooterComponent} from './footer/footer.component';
import {ChatWidgetComponent} from '../widgets/chat-widget/chat-widget.component';
import {LocalStorageUtil} from '../../@core/utils/local-storage-util';
import {environment} from '../../../environments/environment';
import {LoadingService} from '../../shared-services/api-service/loading.service';
import {NotificationService} from '../../shared-services/api-service/notification.service';
import {AuthService} from '../../shared-services/api-service/auth.service';
import {SessionTimeoutService} from '../../shared-services/authentication/session-timeout.service';
import {LanguageService} from '../../shared-services/api-service/language.service';
import {Observable, Subject, of} from 'rxjs';
import {takeUntil, catchError} from 'rxjs/operators';
import {TranslatePipe} from '@ngx-translate/core';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterOutlet, FooterComponent, RouterLink, NgClass, NgIf, SidebarComponent, AsyncPipe, ChatWidgetComponent, NgbModalModule, DatePipe, TranslatePipe],
  templateUrl: './layout.component.html',
  styleUrl: './layout.component.scss'
})
export class LayoutComponent implements OnInit, OnDestroy {
  isExpanded = true;
  userName = 'User';
  userEmail = '';
  roleTitle = '';
  showDropdown = false;
  showNotifications = false;
  pendingCount = 0;
  pendingMemos: any[] = [];
  currentLang = 'en';
  isLoading$: Observable<boolean>;
  @ViewChild('sessionTimeoutModal') sessionTimeoutModal: any;
  private destroy$ = new Subject<void>();
  private modalService = inject(NgbModal);

  constructor(
    private sidebarService: SidebarService,
    private loadingService: LoadingService,
    private notificationService: NotificationService,
    private authService: AuthService,
    private sessionTimeout: SessionTimeoutService,
    private router: Router,
    public languageService: LanguageService
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
    this.notificationService.pendingMemos$.pipe(takeUntil(this.destroy$)).subscribe(
      memos => this.pendingMemos = memos
    );
    this.notificationService.refreshPendingCount();
    this.languageService.currentLang$.pipe(takeUntil(this.destroy$)).subscribe(
      lang => this.currentLang = lang
    );
    this.sessionTimeout.onWarning = () => this.showTimeoutWarning();
    this.sessionTimeout.onExpired = () => this.clearSession();
  }

  ngOnDestroy() {
    this.sessionTimeout.stop();
    this.destroy$.next();
    this.destroy$.complete();
  }

  toggleSidebar() {
    this.sidebarService.toggleSidebar();
  }

  toggleNotifications() {
    this.showNotifications = !this.showNotifications;
    if (this.showDropdown) this.showDropdown = false;
    if (this.showDropdown) this.showDropdown = false;
  }

  toggleLang() {
    const newLang = this.currentLang === 'en' ? 'ne' : 'en';
    this.languageService.switchLang(newLang);
  }

  viewProfile() {}

  logout() {
    this.authService.logout().pipe(
      catchError(() => of(null))
    ).subscribe(() => {
      this.clearSession();
    });
  }

  private showTimeoutWarning() {
    this.modalService.open(this.sessionTimeoutModal, { centered: true, backdrop: 'static', keyboard: false });
  }

  stayActive() {
    this.modalService.dismissAll();
    this.sessionTimeout['resetTimer']();
  }

  private clearSession() {
    this.modalService.dismissAll();
    LocalStorageUtil.removeKey(environment.LOCAL_STORAGE_NAME);
    LocalStorageUtil.clearStorage();
    this.router.navigate(['/login']);
  }
}
