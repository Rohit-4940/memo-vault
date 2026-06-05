import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';
import { LocalStorageUtil } from '../../@core/utils/local-storage-util';

@Injectable({ providedIn: 'root' })
export class AuditLogService {
  constructor(private http: HttpClient) {}

  private get isAdmin(): boolean {
    const storage = LocalStorageUtil.getStorage();
    const role = storage?.roleName?.toUpperCase() || '';
    return role === 'SUPER_ADMIN' || role === 'ADMIN';
  }

  getAuditLogs(params: {
    action?: string;
    status?: string;
    username?: string;
    dateFrom?: string;
    dateTo?: string;
    page?: number;
    size?: number;
    sort?: string;
    dir?: string;
  }): Observable<any> {
    const queryParams: any = {};
    if (params.action) queryParams.action = params.action;
    if (params.status) queryParams.status = params.status;
    if (params.username) queryParams.username = params.username;
    if (params.dateFrom) queryParams.dateFrom = params.dateFrom;
    if (params.dateTo) queryParams.dateTo = params.dateTo;
    queryParams.page = params.page ?? 0;
    queryParams.size = params.size ?? 20;
    queryParams.sort = params.sort ?? 'createdDate';
    queryParams.dir = params.dir ?? 'desc';

    const endpoint = this.isAdmin ? '/api/v1/audit-logs' : '/api/v1/audit-logs/my';
    return this.http.get<any>(`${ApiConfig.URL}${endpoint}`, { params: queryParams });
  }
}
