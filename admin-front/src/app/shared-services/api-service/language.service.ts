import { Injectable, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private translate = inject(TranslateService);
  private currentLangSubject = new BehaviorSubject<string>('en');
  currentLang$ = this.currentLangSubject.asObservable();

  constructor() {
    const saved = localStorage.getItem('nexmemo_lang') || 'en';
    this.translate.setDefaultLang('en');
    this.translate.use(saved);
    this.currentLangSubject.next(saved);
  }

  get currentLang(): string {
    return this.currentLangSubject.value;
  }

  switchLang(lang: string) {
    this.translate.use(lang);
    localStorage.setItem('nexmemo_lang', lang);
    this.currentLangSubject.next(lang);
    document.documentElement.lang = lang;
  }
}
