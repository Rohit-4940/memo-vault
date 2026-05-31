import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private pendingCountSubject = new BehaviorSubject<number>(0);
  pendingCount$ = this.pendingCountSubject.asObservable();

  constructor(private http: HttpClient) {}

  refreshPendingCount() {
    this.http.get<any[]>(`${ApiConfig.URL}/api/v1/memos/pending-approvals`).subscribe({
      next: (data) => this.pendingCountSubject.next(Array.isArray(data) ? data.length : 0),
      error: () => this.pendingCountSubject.next(0)
    });
  }
}
