import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {ApiUtils} from '../../@core/utils/api/ApiUtils';
import {Employee} from '../../@core/model/employee';
import {HumanResources} from '../../@core/model/human-resources';

@Injectable({
  providedIn: 'root'
})
export class EmployeeService {

  static API = 'v1/employer';
  constructor(readonly http: HttpClient) {}
  protected getApi(): string {
    return EmployeeService.API;
  }
  public getAllEmployee(pageNumber: number, pageSize: number): Observable<any> {
    const req = ApiUtils.getRequest(`${this.getApi()}/list?page=${pageNumber}&size=${pageSize}`);
    return this.http.get(req.url);
  }
  public getById(id: any): Observable<any> {
    const req = ApiUtils.getRequest(`${this.getApi()}/id?id=${id}`);
    return this.http.get(req.url);
  }
  public createEmployee(employee: Employee): Observable<any> {
    const req = ApiUtils.getRequest(`${EmployeeService.API}/create`);
    return this.http.post(req.url, employee);
  }

  public updateEmployee(humanUpdate: HumanResources): Observable<any> {
    const req = ApiUtils.getRequest(`${EmployeeService.API}/update`);
    return this.http.post(req.url, humanUpdate);
  }
  public deleteEmployee(id: string): Observable<any> {
    const req = ApiUtils.getRequest(`${EmployeeService.API}/id?id=${id}`);
    return this.http.delete(req.url);
  }
  public disablemployee(employerId: any): Observable<any> {
    const req = ApiUtils.getRequest(`${EmployeeService.API}/toggleStatus/${employerId}`);
    return this.http.put(req.url, null);
  }
}
