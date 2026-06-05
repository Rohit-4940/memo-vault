import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject } from 'rxjs';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private pendingCountSubject = new BehaviorSubject<number>(0);
  private pendingMemosSubject = new BehaviorSubject<any[]>([]);
  pendingCount$ = this.pendingCountSubject.asObservable();
  pendingMemos$ = this.pendingMemosSubject.asObservable();

  constructor(private http: HttpClient) {}

  refreshPendingCount() {
    this.http.get<any[]>(`${ApiConfig.URL}/api/v1/memos/pending-approvals`).subscribe({
      next: (data) => {
        const list = Array.isArray(data) ? data : [];
        this.pendingMemosSubject.next(list);
        this.pendingCountSubject.next(list.length);
      },
      error: () => {
        this.pendingMemosSubject.next([]);
        this.pendingCountSubject.next(0);
      }
    });
  }
}
