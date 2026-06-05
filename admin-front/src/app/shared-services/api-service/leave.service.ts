import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';

@Injectable({ providedIn: 'root' })
export class LeaveService {
  constructor(private http: HttpClient) {}

  getAllLeaves(): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/leaves`);
  }

  getMyLeaves(): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/leaves/my`);
  }

  getLeave(id: number): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/leaves/${id}`);
  }

  createLeave(data: any): Observable<any> {
    return this.http.post<any>(`${ApiConfig.URL}/api/v1/leaves`, data);
  }

  approveLeave(id: number, remarks?: string): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/leaves/${id}/approve`, { remarks });
  }

  rejectLeave(id: number, remarks?: string): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/leaves/${id}/reject`, { remarks });
  }

  cancelLeave(id: number): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/leaves/${id}/cancel`, {});
  }

  getActiveLeaves(): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/leaves/active`);
  }

  getPaginated(search: string, page: number, size: number, sortBy: string, sortDir: string, filters?: any): Observable<any> {
    let params = `?page=${page - 1}&size=${size}&sortBy=${sortBy}&sortDir=${sortDir}`;
    if (search) params += `&search=${encodeURIComponent(search)}`;
    if (filters) Object.keys(filters).forEach(k => { if (filters[k]) params += `&filters[${k}]=${encodeURIComponent(filters[k])}`; });
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/leaves/paginated${params}`);
  }
}
