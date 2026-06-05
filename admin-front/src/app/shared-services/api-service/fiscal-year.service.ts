import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';

@Injectable({ providedIn: 'root' })
export class FiscalYearService {
  constructor(private http: HttpClient) {}

  getAllFiscalYears(): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/fiscal-years`);
  }

  getFiscalYear(id: number): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/fiscal-years/${id}`);
  }

  createFiscalYear(data: any): Observable<any> {
    return this.http.post<any>(`${ApiConfig.URL}/api/v1/fiscal-years`, data);
  }

  updateFiscalYear(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/fiscal-years/${id}`, data);
  }

  deleteFiscalYear(id: number): Observable<void> {
    return this.http.delete<void>(`${ApiConfig.URL}/api/v1/fiscal-years/${id}`);
  }

  setActiveFiscalYear(id: number): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/fiscal-years/${id}/activate`, {});
  }

  getPaginated(search: string, page: number, size: number, sortBy: string, sortDir: string, filters?: any): Observable<any> {
    let params = `?page=${page - 1}&size=${size}&sortBy=${sortBy}&sortDir=${sortDir}`;
    if (search) params += `&search=${encodeURIComponent(search)}`;
    if (filters) Object.keys(filters).forEach(k => { if (filters[k]) params += `&filters[${k}]=${encodeURIComponent(filters[k])}`; });
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/fiscal-years/paginated${params}`);
  }
}
