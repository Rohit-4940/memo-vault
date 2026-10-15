import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';

/**
 * Phase 4b/6 — financial period administration.
 *
 * A financial period (quarter/phase) belongs to a fiscal year and carries an
 * open / close / lock lifecycle. At most one period is OPEN at a time; memo
 * creation is gated on an open period once periods are configured.
 */
@Injectable({ providedIn: 'root' })
export class FinancialPeriodService {
  private readonly base = `${ApiConfig.URL}/api/v1/financial-periods`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<any[]> {
    return this.http.get<any[]>(this.base);
  }

  getByFiscalYear(fiscalYearId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/fiscal-year/${fiscalYearId}`);
  }

  getCurrentOpen(): Observable<any> {
    return this.http.get<any>(`${this.base}/current`);
  }

  create(period: any): Observable<any> {
    return this.http.post<any>(this.base, period);
  }

  update(id: number, period: any): Observable<any> {
    return this.http.put<any>(`${this.base}/${id}`, period);
  }

  delete(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/${id}`);
  }

  open(id: number): Observable<any> {
    return this.http.put<any>(`${this.base}/${id}/open`, {});
  }

  close(id: number): Observable<any> {
    return this.http.put<any>(`${this.base}/${id}/close`, {});
  }

  lock(id: number): Observable<any> {
    return this.http.put<any>(`${this.base}/${id}/lock`, {});
  }
}
