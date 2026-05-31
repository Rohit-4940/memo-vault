import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIf } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../shared-services/api-service/auth.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [FormsModule, NgIf, RouterLink],
  templateUrl: './forgot-password.component.html',
  styleUrl: './forgot-password.component.scss'
})
export class ForgotPasswordComponent {
  email = '';
  submitted = false;
  loading = false;
  error = '';

  constructor(private authService: AuthService) {}

  onSubmit() {
    if (!this.email) return;
    this.loading = true;
    this.error = '';
    this.authService.forgotPassword(this.email).subscribe({
      next: () => {
        this.submitted = true;
        this.loading = false;
      },
      error: (err) => {
        this.error = err.error?.message || 'Failed to send reset email';
        this.loading = false;
      }
    });
  }
}
