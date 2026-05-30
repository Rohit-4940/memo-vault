import { Component } from '@angular/core';
import {LocalStorageUtil} from '../../utils/local-storage-util';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {Router} from '@angular/router';
import {ApiConfig} from '../../utils/api/ApiConfig';
import {User} from '../../model/user';
import {UserService} from '../../../shared-services/api-service/user-service';

@Component({
  selector: 'app-time-out-pop-up',
  standalone: true,
  imports: [],
  templateUrl: './time-out-pop-up.component.html',
  styleUrl: './time-out-pop-up.component.scss'
})
export class TimeOutPopUpComponent {

  spinner = false;
  msg;
  private securityUrl = ApiConfig.TOKEN;

  constructor(
    private http: HttpClient,
    private router: Router,
    private userService: UserService,
  ) {
  }

  private headers = new HttpHeaders({
    'Content-Type': 'application/x-www-form-urlencoded',
    'Authorization': 'Basic Y3Atc29sdXRpb246Y3Bzb2x1dGlvbjEyMyoj',
  });

  ngOnInit(): void {
  }


  onLogin(datavalue) {
    this.spinner = true;
    const userName = LocalStorageUtil.getStorage().username;
    const loginData: { email: string, password: string } = datavalue.value;
    const datas = `grant_type=password&username=${userName}&password=${loginData.password}`;
    this.http.post(this.securityUrl, datas, {headers: this.headers})
      .subscribe(async (loginResponse: any) => {
          this.spinner = false;
          LocalStorageUtil.clearStorage();
          const storage = LocalStorageUtil.getStorage();
          storage.at = loginResponse.access_token;
          storage.rt = loginResponse.refresh_token;
          storage.ty = loginResponse.token_type;
          storage.et = loginResponse.expires_in;
          LocalStorageUtil.setStorage(storage);

          await this.userService.userLogin().toPromise().then((res: any) => {
            const user: User = res.detail;
            storage.userId = (user.id).toString();
            storage.username = user.username;
            storage.userFullName = user.name;
            storage.userProfilePicture = user.profilePicture;
            // storage.roleAccess = user.role.roleAccess;
            // storage.roleName = user.role.roleName;
            // storage.roleType = user.role.roleType;
            // storage.roleId = (user.role.id).toString();
            LocalStorageUtil.setStorage(storage);
          }, error => console.error(error));

          await this.userService.getAuthenticatedUserBranches().toPromise().then((response: any) => {
            storage.branch = response.detail;
            LocalStorageUtil.setStorage(storage);
          }, error => console.error(error));
        },
        ((error) => {
          this.spinner = false;
          if (error.status === 401) {
            LocalStorageUtil.clearStorage();
            this.router.navigate(['/login']);
          } else if (error.status === 400) {
            LocalStorageUtil.clearStorage();
            this.router.navigate(['/login']);
          }
        })
      );
  }
}
