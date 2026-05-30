import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';

@Injectable({ providedIn: 'root' })
export class UserService {
  constructor(private http: HttpClient) {}

  getAllUsers(): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/admin/users`);
  }

  getUser(id: number): Observable<any> {
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/admin/users/${id}`);
  }

  createUser(data: any): Observable<any> {
    return this.http.post<any>(`${ApiConfig.URL}/api/v1/admin/users`, data);
  }

  updateUser(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${ApiConfig.URL}/api/v1/admin/users/${id}`, data);
  }

  deleteUser(id: number): Observable<void> {
    return this.http.delete<void>(`${ApiConfig.URL}/api/v1/admin/users/${id}`);
  }
}
