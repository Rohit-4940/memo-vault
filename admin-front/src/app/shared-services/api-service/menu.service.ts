import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';

@Injectable({ providedIn: 'root' })
export class MenuService {
  constructor(private http: HttpClient) {}

  getAllMenus(): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/menus`);
  }

  getMenusByRole(roleId: number): Observable<any[]> {
    return this.http.get<any[]>(`${ApiConfig.URL}/api/v1/menus/by-role/${roleId}`);
  }
}
