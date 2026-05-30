import { Injectable } from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {ApiUtils} from '../../@core/utils/api/ApiUtils';

@Injectable({
  providedIn: 'root'
})
export class EmployerLoanConfigService {

  static API = 'api/configurations';
  constructor(readonly http: HttpClient) {}
  protected getApi(): string {
    return EmployerLoanConfigService.API;
  }
  public get(employerId: any): Observable<any> {
    const req = ApiUtils.getRequest(`${this.getApi()}/${employerId}`);
    return this.http.get(req.url);
  }
  public create(loanConfig: any, employerId: any): Observable<any> {
    const req = ApiUtils.getRequest(`${EmployerLoanConfigService.API}/${employerId}`);
    return this.http.put(req.url, loanConfig);
  }
  public updateEmployee(humanUpdate: any): Observable<any> {
    const req = ApiUtils.getRequest(`${EmployerLoanConfigService.API}/update`);
    return this.http.post(req.url, humanUpdate);
  }
  public deleteEmployee(id: string): Observable<any> {
    const req = ApiUtils.getRequest(`${EmployerLoanConfigService.API}/id?id=${id}`);
    return this.http.delete(req.url);
  }
}
