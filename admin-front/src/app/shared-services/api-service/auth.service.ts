import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';
import { LocalStorageUtil } from '../../@core/utils/local-storage-util';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  static API = 'api/v1/auth';
  constructor(readonly http: HttpClient) {}

  public login(credentials: { username: string; password: string }): Observable<any> {
    return this.http.post(`${ApiConfig.URL}/${AuthService.API}/login`, credentials).pipe(
      tap((response: any) => {
        if (response.accessToken) {
          const storage = LocalStorageUtil.getStorage();
          storage.at = { accessToken: response.accessToken, refreshToken: response.refreshToken };
          storage.username = response.username || response.username;
          storage.userId = response.userId;
          storage.roleName = response.role;
          storage.roleId = response.roleId;
          LocalStorageUtil.setStorage(storage);
        }
      })
    );
  }

  public verifyOtp(userId: number, otpCode: string, trustDevice: boolean = false): Observable<any> {
    return this.http.post(`${ApiConfig.URL}/${AuthService.API}/verify-otp`, {
      userId, otpCode, trustDevice
    }).pipe(
      tap((response: any) => {
        if (response.accessToken) {
          const storage = LocalStorageUtil.getStorage();
          storage.at = { accessToken: response.accessToken, refreshToken: response.refreshToken };
          storage.username = response.username || response.username;
          storage.userId = response.userId;
          storage.roleName = response.role;
          storage.roleId = response.roleId;
          LocalStorageUtil.setStorage(storage);
        }
      })
    );
  }

  public refreshToken(): Observable<any> {
    const storage = LocalStorageUtil.getStorage();
    const refreshToken = storage?.at?.refreshToken;
    if (!refreshToken) {
      return new Observable(sub => {
        sub.error('No refresh token');
      });
    }
    return this.http.post(`${ApiConfig.URL}/${AuthService.API}/refresh`, {
      refreshToken
    }).pipe(
      tap((response: any) => {
        if (response.accessToken) {
          storage.at = { accessToken: response.accessToken, refreshToken: response.refreshToken };
          LocalStorageUtil.setStorage(storage);
        }
      })
    );
  }

  public changePassword(currentPassword: string, newPassword: string, confirmPassword: string): Observable<any> {
    return this.http.post(`${ApiConfig.URL}/${AuthService.API}/change-password`, {
      currentPassword, newPassword, confirmPassword
    });
  }

  public forgotPassword(email: string): Observable<any> {
    return this.http.post(`${ApiConfig.URL}/${AuthService.API}/forgot-password`, null, {
      params: { email }
    });
  }

  public resetPassword(token: string, newPassword: string, confirmPassword: string): Observable<any> {
    return this.http.post(`${ApiConfig.URL}/${AuthService.API}/reset-password`, {
      token, newPassword, confirmPassword
    });
  }

  public logout(): Observable<any> {
    return this.http.post(`${ApiConfig.URL}/${AuthService.API}/logout`, {});
  }
}
