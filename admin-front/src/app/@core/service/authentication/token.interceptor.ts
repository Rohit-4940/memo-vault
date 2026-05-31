import { HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { LocalStorageUtil } from '../../utils/local-storage-util';
import { jwtDecode } from 'jwt-decode';

export const TokenInterceptor: HttpInterceptorFn = (req, next) => {
  const authBasePath = '/api/v1/auth/';
  let header = req.headers.set('Content-Type', 'application/json');

  if (req.url.includes(authBasePath + 'login') ||
      req.url.includes(authBasePath + 'verify-otp') ||
      req.url.includes(authBasePath + 'forgot-password') ||
      req.url.includes(authBasePath + 'reset-password') ||
      req.url.includes(authBasePath + 'set-password') ||
      req.url.includes(authBasePath + 'refresh')) {
    return next(req);
  }

  const storage = LocalStorageUtil.getStorage();
  let authReq: HttpRequest<any>;

  if (storage?.at?.accessToken) {
    let decodedToken: any;
    try {
      decodedToken = jwtDecode(storage.at.accessToken);
    } catch (error) {
      console.error('Failed to decode access token:', error);
      return next(req);
    }
    const isAccessTokenExpired = decodedToken.exp * 1000 < Date.now();
    if (!isAccessTokenExpired) {
      authReq = req.clone({
        headers: header.set('Authorization', `Bearer ${storage.at.accessToken}`)
      });
      return next(authReq);
    }
  }
  return next(req);
};
