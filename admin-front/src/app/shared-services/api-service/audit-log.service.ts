import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiConfig } from '../../@core/utils/api/ApiConfig';

@Injectable({ providedIn: 'root' })
export class AuditLogService {
  constructor(private http: HttpClient) {}

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
    return this.http.get<any>(`${ApiConfig.URL}/api/v1/audit-logs`, { params: queryParams });
  }
}
