import {Component, inject, OnInit, signal, WritableSignal} from '@angular/core';
import {User} from '../../../@core/model/user';
import {UserService} from '../../../shared-services/api-service/user.service';
import {SpinnerService} from '../../../@theme/spinner/service/spinner.service';
import {NgbModal} from '@ng-bootstrap/ng-bootstrap';
import {CreateUpdateUserComponent} from './create-update-user/create-update-user.component';
import {ToastrService} from 'ngx-toastr';
import {DeleteConfirmationComponent} from '../../@common-component/delete-confirmation/delete-confirmation.component';
import {PaginationUtils} from '../../../@core/utils/PaginationUtils';
import {Pageable} from '../../../@core/model/pagable';
import {SbPaginationComponent} from '../../../@theme/sb-pagination/sb-pagination.component';

@Component({
  selector: 'app-user',
  standalone: true,
  imports: [
    SbPaginationComponent
  ],
  templateUrl: './user.component.html',
  styleUrl: './user.component.scss'
})
export class UserComponent implements OnInit {

  userService: UserService = inject(UserService);
  spinnerService: SpinnerService = inject(SpinnerService);
  modalService: NgbModal = inject(NgbModal);
  toasterService: ToastrService = inject(ToastrService);

  userList: WritableSignal<Array<User>> = signal<Array<User>>([]);

  isEditClicked: WritableSignal<boolean> = signal<boolean>(false);
  pageable: Pageable = new Pageable();

  ngOnInit() {
    this.getAllUserData();
  }
  getAllUserData() {
    this.spinnerService.show();
    this.userService.getAllUserByPage(this.pageable.pageNumber, this.pageable.pageSize)
      .subscribe({
        next: (response) => {
          this.userList.update(() => response?.data || []);
          this.pageable = PaginationUtils.getPageable(response.meta);
          this.toasterService.success('Success', 'User Fetch Successfully');
        },
        error: (error) => {
          this.spinnerService.hide();
          this.toasterService.error('Error', 'Unable to retrieve user ');
          console.error('Error fetching Human user:', error?.message);
        },
        complete: () => {
          this.spinnerService.hide();
        }
      });
  }

  openModule(id?: any, data?: any) {
    const modalRef = this.modalService.open(CreateUpdateUserComponent, {
      backdrop:'static',
      backdropClass:'customBackdrop',
      size: 'lg',
    });

    if (this.isEditClicked()) {
      modalRef.componentInstance.userData = data;
      modalRef.componentInstance.id = id;
      modalRef.componentInstance.isEditMode = this.isEditClicked();
    }

    modalRef.closed.subscribe((result) => {
      console.log('result', result);
      if (result === 'success') {
        this.getAllUserData();
      }
    });
  }

  onCreateUserData()  {
    this.isEditClicked.set(false);
    this.openModule();
  }

  onEditUserData(id :any, data: any)  {
    this.isEditClicked.set(true);
    this.openModule(id, data);
  }

  deleteUserData(id: any) {
    const modalRef = this.modalService.open(DeleteConfirmationComponent, {
      size: 'sm',
      backdrop: 'static',
      ariaLabelledBy: 'modal-basic-title',
    });
    modalRef.componentInstance.id = id;
    modalRef.componentInstance.message = 'Are you sure do you want to delete User record ?';
    modalRef.result.then((confirmed) => {
      if (confirmed) {
        this.spinnerService.show();
        this.userService.deleteUser(id).subscribe({
          next: res => {
            this.getAllUserData();
            this.toasterService.success(res?.message, 'Success');
            this.spinnerService.hide();
          }, error: err => {
            this.toasterService.error(err?.message, 'Error');
            this.spinnerService.hide();
          }
        });
      }
    }).catch(() => {
    });
  }
  onPageChange(page: number): void {
    this.pageable.pageNumber = page;
    this.getAllUserData();
  }

  onPageSizeChange(pageSize: number): void {
    this.pageable.pageSize = pageSize;
    this.getAllUserData();
  }
}
