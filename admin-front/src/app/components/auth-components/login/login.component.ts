import { Component } from '@angular/core';
import { NgIf } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { AuthService } from '../../../shared-services/api-service/auth.service';
import { LocalStorageUtil } from '../../../@core/utils/local-storage-util';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, NgIf],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent {
  spinner = false;
  msg: string | null = null;
  loginError = false;
  showPassword = false;

  step: 'credentials' | 'otp' = 'credentials';

  logInObj = { username: '', password: '' };
  otpObj = { code: '', trustDevice: false };
  currentUserId: number | null = null;

  constructor(
    private http: HttpClient,
    private router: Router,
    private authService: AuthService
  ) {}

  onLogin(form: any) {
    if (form.invalid) return;
    this.spinner = true;
    this.loginError = false;
    this.msg = null;

    this.authService.login({
      username: this.logInObj.username,
      password: this.logInObj.password
    }).subscribe({
      next: (response) => {
        this.spinner = false;
        if (response.requiresOtp) {
          this.currentUserId = response.userId;
          this.step = 'otp';
          this.msg = response.message;
        } else {
          this.handleLoginSuccess(response);
        }
      },
      error: (error) => {
        this.spinner = false;
        this.handleLoginError(error);
      }
    });
  }

  onVerifyOtp() {
    if (!this.otpObj.code || this.otpObj.code.length < 4) return;
    this.spinner = true;
    this.loginError = false;
    this.msg = null;

    this.authService.verifyOtp(this.currentUserId!, this.otpObj.code, this.otpObj.trustDevice)
      .subscribe({
        next: (response) => {
          this.spinner = false;
          this.handleLoginSuccess(response);
        },
        error: (error) => {
          this.spinner = false;
          this.msg = error.error?.message || 'Invalid OTP code. Please try again.';
          this.loginError = true;
        }
      });
  }

  private handleLoginSuccess(response: any) {
    const storage = LocalStorageUtil.getStorage();
    storage.at = { accessToken: response.accessToken, refreshToken: response.refreshToken };
    storage.username = response.username;
    storage.userId = response.userId;
    storage.roleName = response.role;
    storage.roleId = response.roleId;
    storage.roleAccess = response.role;
    LocalStorageUtil.setStorage(storage);
    this.router.navigate(['/layout/dashboard']);
  }

  private handleLoginError(error: any) {
    this.loginError = true;
    if (error.status === 401 || error.status === 400) {
      this.msg = 'Invalid username or password';
    } else if (error.status === 423) {
      this.msg = error.error?.message || 'Account is locked. Contact an administrator.';
    } else if (error.error?.message) {
      this.msg = error.error.message;
    } else {
      this.msg = 'Connection error. Please try again.';
    }
  }

  backToCredentials() {
    this.step = 'credentials';
    this.otpObj.code = '';
    this.msg = null;
    this.loginError = false;
  }

  resendOtp() {
    if (!this.currentUserId) return;
    this.spinner = true;
    this.loginError = false;
    this.authService.login({
      username: this.logInObj.username,
      password: this.logInObj.password
    }).subscribe({
      next: (response) => {
        this.spinner = false;
        this.msg = 'A new OTP has been sent to your email.';
      },
      error: (error) => {
        this.spinner = false;
        this.msg = 'Failed to resend OTP. Please try again.';
        this.loginError = true;
      }
    });
  }

  togglePassword() {
    this.showPassword = !this.showPassword;
  }
}
