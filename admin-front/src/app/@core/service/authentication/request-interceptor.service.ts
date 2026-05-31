import { Injectable } from '@angular/core';
import {
  HttpBackend,
  HttpClient,
  HttpErrorResponse,
  HttpEvent,
  HttpHandler,
  HttpInterceptor,
  HttpRequest,
} from '@angular/common/http';
import { BehaviorSubject, Observable, of, throwError } from 'rxjs';
import { catchError, filter, switchMap, take, tap } from 'rxjs/operators';
import { ApiConfig } from '../../utils/api/ApiConfig';
import { LocalStorageUtil } from '../../utils/local-storage-util';
import { NgxSpinnerService } from 'ngx-spinner';
import { Router } from '@angular/router';

@Injectable({
  providedIn: 'root',
})
export class RequestInterceptor implements HttpInterceptor {
  private tokenSubject: BehaviorSubject<string | null> = new BehaviorSubject<string | null>(null);
  private isTokenRefreshing = false;

  constructor(
    private httpBackend: HttpBackend,
    private spinnerService: NgxSpinnerService,
    private router: Router
  ) {}

  static attachTokenToRequest(request: HttpRequest<any>) {
    const storage = LocalStorageUtil.getStorage();
    const accessToken = storage?.at?.accessToken;
    let headers: any = {};

    if (request?.headers?.get('content-type')) {
      headers = {
        Authorization: 'Bearer ' + accessToken,
        'Content-Type': 'application/json',
      };
    } else if (request?.headers?.get('enctype')) {
      headers = {
        Authorization: 'Bearer ' + accessToken,
        enctype: 'multipart/form-data',
      };
    } else {
      headers = {
        Authorization: 'Bearer ' + accessToken,
        'Content-Type': 'application/json',
      };
    }
    return request.clone({ setHeaders: headers });
  }

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const storage = LocalStorageUtil.getStorage();
    if (storage?.at?.accessToken) {
      return next.handle(req).pipe(
        catchError((err): Observable<any> => {
          if (err instanceof HttpErrorResponse) {
            this.spinnerService.hide();
            if (err.status === 401) {
              return this.handleHttpResponseError(req, next);
            }
            return throwError(() => err);
          }
          return throwError(() => err);
        })
      );
    } else {
      return next.handle(req).pipe(
        catchError((err): Observable<any> => {
          this.spinnerService.hide();
          if (err.status === 401) {
            LocalStorageUtil.clearStorage();
            this.router.navigate(['/login']);
            return throwError(() => err);
          }
          return throwError(() => err);
        })
      );
    }
  }

  private refreshAccessToken(): Observable<any> {
    const storage = LocalStorageUtil.getStorage();
    const refreshToken = storage?.at?.refreshToken;
    if (!refreshToken) {
      return throwError(() => new Error('No refresh token'));
    }
    return new HttpClient(this.httpBackend)
      .post(`${ApiConfig.URL}/api/v1/auth/refresh`, { refreshToken })
      .pipe(
        tap((response: any) => {
          if (response.accessToken) {
            storage.at = { accessToken: response.accessToken, refreshToken: response.refreshToken };
            LocalStorageUtil.setStorage(storage);
          }
        }),
        catchError(err => {
          LocalStorageUtil.clearStorage();
          this.router.navigate(['/login']);
          return throwError(() => err);
        })
      );
  }

  private handleHttpResponseError(request: HttpRequest<any>, next: HttpHandler) {
    if (!this.isTokenRefreshing) {
      this.isTokenRefreshing = true;
      this.tokenSubject.next(null);

      return this.refreshAccessToken().pipe(
        switchMap((tokenResponse: any) => {
          this.isTokenRefreshing = false;
          this.tokenSubject.next(tokenResponse);
          return next.handle(RequestInterceptor.attachTokenToRequest(request));
        })
      );
    } else {
      return this.tokenSubject.pipe(
        filter(token => token != null),
        take(1),
        switchMap(() => {
          return next.handle(RequestInterceptor.attachTokenToRequest(request));
        })
      );
    }
  }
}
