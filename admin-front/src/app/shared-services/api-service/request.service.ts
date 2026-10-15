import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';

/**
 * Phase 5b/6 — self-service request API.
 *
 * A "request" is a memo underneath, so this service is a thin client over the
 * `/requests` surface of the common request engine. The generic API-response
 * interceptor unwraps the `{ success, data }` envelope (except the paged
 * endpoint, which returns a PageResponse directly).
 */
@Injectable({ providedIn: 'root' })
export class RequestService {
  private readonly base = `${ApiConfig.URL}/api/v1/requests`;

  constructor(private http: HttpClient) {}

  /** Request types the caller may raise, each with its dynamic form schema. */
  getCatalogue(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/catalogue`);
  }

  /** The caller's own requests (newest first); returns a PageResponse. */
  getMyRequests(page = 0, size = 20): Observable<any> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<any>(`${this.base}/my`, { params });
  }

  /** Raise a request; set submitNow to submit it for approval immediately. */
  createRequest(request: any, submitNow: boolean): Observable<any> {
    return this.http.post<any>(this.base, { request, submitNow });
  }

  getRequest(id: number): Observable<any> {
    return this.http.get<any>(`${this.base}/${id}`);
  }

  /** Edit a request that is still a draft / returned for revision. */
  updateRequest(id: number, request: any): Observable<any> {
    return this.http.put<any>(`${this.base}/${id}`, request);
  }

  submitRequest(id: number): Observable<any> {
    return this.http.post<any>(`${this.base}/${id}/submit`, {});
  }

  cancelRequest(id: number): Observable<any> {
    return this.http.post<any>(`${this.base}/${id}/cancel`, {});
  }

  /** Status + approval history + workflow stages in one call. */
  getTracking(id: number): Observable<any> {
    return this.http.get<any>(`${this.base}/${id}/tracking`);
  }
}
