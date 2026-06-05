import { Component } from '@angular/core';
import { NgIf } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../shared-services/api-service/auth.service';
import { ToastrService } from 'ngx-toastr';
import { TranslateService } from '@ngx-translate/core';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [NgIf, FormsModule, TranslatePipe],
  templateUrl: './change-password.component.html',
  styleUrl: './change-password.component.scss'
})
export class ChangePasswordComponent {
  spinner = false;
  force = false;
  obj = { currentPassword: '', newPassword: '', confirmPassword: '' };

  constructor(
    private authService: AuthService,
    private router: Router,
    private toastr: ToastrService,
    private translate: TranslateService
  ) {
    this.force = this.router.url.includes('force');
  }

  onSubmit(): void {
    if (!this.obj.currentPassword || !this.obj.newPassword || !this.obj.confirmPassword) {
      this.toastr.warning(this.translate.instant('changePassword.allFieldsRequired'));
      return;
    }
    if (this.obj.newPassword !== this.obj.confirmPassword) {
      this.toastr.warning(this.translate.instant('changePassword.passwordsDoNotMatch'));
      return;
    }
    if (this.obj.newPassword.length < 8) {
      this.toastr.warning(this.translate.instant('changePassword.minLength'));
      return;
    }
    this.spinner = true;
    this.authService.changePassword(this.obj.currentPassword, this.obj.newPassword, this.obj.confirmPassword).subscribe({
      next: () => {
        this.spinner = false;
        this.toastr.success(this.translate.instant('changePassword.success'));
        if (this.force) {
          this.router.navigate(['/layout/dashboard']);
        }
      },
      error: (err) => {
        this.spinner = false;
        this.toastr.error(err.error?.message || this.translate.instant('changePassword.failed'));
      }
    });
  }
}
