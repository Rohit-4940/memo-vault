import {ApplicationConfig, provideZoneChangeDetection} from '@angular/core';
import {provideRouter} from '@angular/router';
import {routes} from './app.routes';
import {provideClientHydration} from '@angular/platform-browser';
import {provideHttpClient, withFetch, withInterceptors, withInterceptorsFromDi} from '@angular/common/http';
import {TokenInterceptor} from './@core/service/authentication/token.interceptor';
import {ApiResponseInterceptor} from './@core/service/authentication/api-response.interceptor';
import {RequestInterceptor} from './@core/service/authentication/request-interceptor.service';
import {provideToastr} from 'ngx-toastr';
import {provideAnimations} from '@angular/platform-browser/animations';
import {provideAnimationsAsync} from '@angular/platform-browser/animations/async';
import {NgbModal} from '@ng-bootstrap/ng-bootstrap';
import {NgSelectModule} from '@ng-select/ng-select';
import {HTTP_INTERCEPTORS} from '@angular/common/http';
import {provideTranslateService} from '@ngx-translate/core';
import {provideTranslateHttpLoader} from '@ngx-translate/http-loader';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideClientHydration(),
    provideHttpClient(
      withInterceptors([TokenInterceptor, ApiResponseInterceptor]),
      withInterceptorsFromDi(),
      withFetch()
    ),
    {
      provide: HTTP_INTERCEPTORS,
      useClass: RequestInterceptor,
      multi: true
    },
    provideAnimations(),
    provideToastr(),
    provideAnimationsAsync(),
    provideTranslateService({
      defaultLanguage: 'en'
    }),
    ...provideTranslateHttpLoader(),
    NgbModal,
    NgSelectModule
  ]
};
