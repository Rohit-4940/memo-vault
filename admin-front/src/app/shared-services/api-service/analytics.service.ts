import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  constructor(private http: HttpClient) {}

  getDashboardSummary(): Observable<any> {
    return this.http.get(`${ApiConfig.URL}/api/v1/analytics/dashboard-summary`);
  }

  getExpenseByCategory(): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/analytics/expense-by-category`);
  }

  getStatusBreakdown(): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/analytics/status-breakdown`);
  }

  getMonthlyTrend(): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/analytics/monthly-trend`);
  }

  getUserStats(userId: number): Observable<any> {
    return this.http.get(`${ApiConfig.URL}/api/v1/analytics/user-stats/${userId}`);
  }
}
