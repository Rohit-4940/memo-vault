import { HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { map, throwError } from 'rxjs';

export const ApiResponseInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.url.includes('/api/v1/auth/')) {
    return next(req);
  }

  return next(req).pipe(
    map(event => {
      if (event instanceof HttpResponse && event.body && typeof event.body === 'object') {
        const body = event.body as Record<string, unknown>;
        if ('success' in body && 'data' in body) {
          if (body['success'] === false) {
            throw new Error((body['message'] as string) || 'Request failed');
          }
          return event.clone({ body: body['data'] });
        }
      }
      return event;
    })
  );
};
