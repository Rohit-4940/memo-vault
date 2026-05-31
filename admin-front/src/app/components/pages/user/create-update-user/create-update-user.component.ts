import {Component, inject, Input, OnInit} from '@angular/core';
import {FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {NgbActiveModal} from '@ng-bootstrap/ng-bootstrap';
import {User} from '../../../../@core/model/user';
import {UserService} from '../../../../shared-services/api-service/user.service';
import {FileService} from '../../../../shared-services/api-service/file.service';
import {NgLabelTemplateDirective, NgOptionTemplateDirective, NgSelectComponent} from '@ng-select/ng-select';
import {UserType} from '../../../../@core/emun/user-type';
import {ToastrService} from 'ngx-toastr';
import {SpinnerService} from '../../../../@theme/spinner/service/spinner.service';
import {Status} from '../../../../@core/emun/status';
import {NgForOf, NgIf} from '@angular/common';

@Component({
  selector: 'app-create-update-user',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    NgLabelTemplateDirective,
    NgOptionTemplateDirective,
    NgSelectComponent,
    NgForOf,
    NgIf
  ],
  templateUrl: './create-update-user.component.html',
  styleUrl: './create-update-user.component.scss'
})
export class CreateUpdateUserComponent implements OnInit{

  @Input() userData: any;
  @Input() userId: any;
  @Input() isEditMode: boolean = false;

  userForm: FormGroup = new FormGroup<any>({});

  activeModal: NgbActiveModal = inject(NgbActiveModal);
  userService: UserService = inject(UserService);
  fileService: FileService = inject(FileService);
  toasterService: ToastrService = inject(ToastrService);
  spinnerService: SpinnerService = inject(SpinnerService);
  signatureFile: File | null = null;
  signaturePreview: string | null = null;
  statusOptions = Object.keys(Status);

  userType = UserType.enumObject();
  userList: any[] = [];
  usernameExists: boolean = false;

  ngOnInit() {
    this.buildForm();
    if (this.userData && this.isEditMode) {
      this.patchUserForm(this.userData);
    }
    this.getAllUserData();
  }

  buildForm() {
    this.userForm = new FormGroup({
      name: new FormControl(null, Validators.required),
      userName: new FormControl(null, Validators.required),
      password: new FormControl(null, Validators.required),
      email: new FormControl(null, [Validators.required, Validators.email]),
      contactNo: new FormControl(null, [Validators.required, Validators.pattern('^[0-9]*$'), Validators.maxLength(10),]),
      userType: new FormControl(null, Validators.required),
      roles: new FormControl(null, Validators.required),
      branch: new FormControl(null, Validators.required),
      status: new FormControl(null, Validators.required),
      department: new FormControl(null, Validators.required),
      address: new FormControl(null, Validators.required),
    });
  }

  onSubmit() {
    this.spinnerService.show();
    if (this.userForm.valid) {
      const data = {
        name: this.userForm?.get('name')?.value,
        username: this.userForm?.get('userName')?.value,
        password: this.userForm?.get('password')?.value,
        email: this.userForm?.get('email')?.value,
        contactNo: this.userForm?.get('contactNo')?.value,
        address: this.userForm?.get('address')?.value,
        userType: [this.userForm?.get('userType')?.value],
        roles: [this.userForm?.get('roles')?.value],
        attributes: {},
        department: this.userForm?.get('department')?.value,
        branch: this.userForm?.get('branch')?.value,
        active: true
      }

      if (!this.isEditMode) {
        this.uploadSignature().then(() => {
        this.userService.createUser(data).subscribe({
          next: res => {
            console.log('User create successfully', res?.data);
            this.toasterService.success('User create successfully', 'Success');
            this.activeModal.close('success');
            this.spinnerService.hide();
          }, error: err => {
            this.toasterService.error(err?.error?.message, 'Error');
            this.spinnerService.hide();
          }
        });
        });
      } else {
        const username = this.userForm?.get('userName')?.value;
          this.userService.updateUser(username, data).subscribe({
            next: res => {
              this.toasterService.success(res?.message, 'Success');
              this.activeModal.close('success');
              this.spinnerService.hide();
            }, error: err => {
              console.error(err?.message);
              this.toasterService.error(err?.error?.message, 'Error');
              this.spinnerService.hide();
            }
          });
      }
    }
    // this.spinnerService.hide();
  }

  patchUserForm(data: User) {
    this.userForm.patchValue({
      name: data?.name,
      userName: data?.username,
      email: data?.email,
      roles: data?.roles?.length ? data?.roles[0] : '',
      userType: data?.userType?.length ? data?.userType[0] : '',
      contactNo: data?.contactNo,
      branch: data?.branch,
      status: data?.active,
      department: data?.department,
      address: data?.address,
    });
  }

  getAllUserData() {
    this.userService.fetchAllUser().subscribe({
      next: (res: any) => {
        this.userList = res?.data;
      },
      error: (err: any) => {
        this.toasterService.error(err?.error?.message, 'Error');
      },
      complete: () => {
      },
    });
  }

  onSignatureSelected(event: any) {
    const file = event.target.files[0];
    if (file) {
      this.signatureFile = file;
      const reader = new FileReader();
      reader.onload = (e) => this.signaturePreview = e.target?.result as string;
      reader.readAsDataURL(file);
    }
  }

  uploadSignature(): Promise<void> {
    if (!this.signatureFile) return Promise.resolve();
    return new Promise((resolve, reject) => {
      this.fileService.uploadSignature(this.signatureFile!).subscribe({
        next: () => { resolve(); },
        error: (err) => { this.toasterService.error('Signature upload failed'); reject(err); }
      });
    });
  }

  checkIfUsernameExists(data: any) {
    const userName = data?.target?.value;
    console.log('userName', userName);
    console.log('userList', this.userList);
    this.usernameExists = this.userList.some(user => user?.username.trim().toLowerCase() === userName.trim().toLowerCase());
    console.log('usernameExists', this.usernameExists);

  }

}
