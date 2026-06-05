import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';

@Injectable({ providedIn: 'root' })
export class DepartmentService {
  constructor(private http: HttpClient) {}

  getAllDepartments(): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/departments`);
  }

  getDepartment(id: number): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/departments/${id}`);
  }

  createDepartment(data: any): Observable<any> {
    return this.http.post<any>(`${ApiConfig.URL}/api/v1/departments`, data);
  }

  updateDepartment(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/departments/${id}`, data);
  }

  deleteDepartment(id: number): Observable<void> {
    return this.http.delete<void>(`${ApiConfig.URL}/api/v1/departments/${id}`);
  }

  getPaginated(search: string, page: number, size: number, sortBy: string, sortDir: string, filters?: any): Observable<any> {
    let params = `?page=${page - 1}&size=${size}&sortBy=${sortBy}&sortDir=${sortDir}`;
    if (search) params += `&search=${encodeURIComponent(search)}`;
    if (filters) Object.keys(filters).forEach(k => { if (filters[k]) params += `&filters[${k}]=${encodeURIComponent(filters[k])}`; });
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/departments/paginated${params}`);
  }
}
