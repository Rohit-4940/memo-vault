import {Component, inject, OnInit} from '@angular/core';
import {UserService} from '../../../shared-services/api-service/user.service';
import {SpinnerService} from '../../../@theme/spinner/service/spinner.service';
import {NgbModal} from '@ng-bootstrap/ng-bootstrap';
import {CreateUpdateUserComponent} from './create-update-user/create-update-user.component';
import {ToastrService} from 'ngx-toastr';
import {DeleteConfirmationComponent} from '../../@common-component/delete-confirmation/delete-confirmation.component';
import {AdvancedTableComponent, TableColumn} from '../../../shared-components/advanced-table/advanced-table.component';

@Component({
  selector: 'app-user',
  standalone: true,
  imports: [
    AdvancedTableComponent
  ],
  templateUrl: './user.component.html',
  styleUrl: './user.component.scss'
})
export class UserComponent implements OnInit {

  columns: TableColumn[] = [
    { key: 'id', label: 'S.N', sortable: true, width: '60px' },
    { key: 'name', label: 'Name', sortable: true, filterable: true, filterType: 'text' },
    { key: 'email', label: 'Email', sortable: true, filterable: true, filterType: 'text' },
    { key: 'username', label: 'Username', sortable: true, filterable: true, filterType: 'text' },
    { key: 'userType', label: 'User Type', sortable: true },
    { key: 'roles', label: 'Role', sortable: false },
    { key: 'branch', label: 'Branch', sortable: false },
    { key: 'status', label: 'Status', sortable: true, filterable: true, filterType: 'select', filterOptions: [{label:'Active',value:'ACTIVE'},{label:'Inactive',value:'INACTIVE'}] },
    { key: 'department', label: 'Department', sortable: false },
    { key: 'address', label: 'Address', sortable: false }
  ];
  data: any[] = [];
  loading = false;
  pageNumber = 1;
  pageSize = 20;
  totalElements = 0;
  totalPages = 1;
  sortBy = 'id';
  sortDir: 'asc' | 'desc' = 'desc';
  searchQuery = '';
  filters = new Map<string, string>();

  userService: UserService = inject(UserService);
  spinnerService: SpinnerService = inject(SpinnerService);
  modalService: NgbModal = inject(NgbModal);
  toasterService: ToastrService = inject(ToastrService);

  isEditClicked = false;

  ngOnInit() {
    this.loadUsers();
  }

  loadUsers() {
    this.loading = true;
    const filterObj: any = {};
    this.filters.forEach((v, k) => { if (v) filterObj[k] = v; });
    this.userService.getPaginated(this.searchQuery, this.pageNumber, this.pageSize, this.sortBy, this.sortDir, filterObj).subscribe({
      next: (res) => {
        this.data = res.content || [];
        this.totalElements = res.totalElements || 0;
        this.totalPages = res.totalPages || 1;
        this.loading = false;
      },
      error: () => { this.toasterService.error('Error', 'Unable to retrieve users'); this.loading = false; }
    });
  }

  onSearch(value: string) {
    this.searchQuery = value;
    this.pageNumber = 1;
    this.loadUsers();
  }

  onPageChange(page: number) {
    this.pageNumber = page;
    this.loadUsers();
  }

  onSizeChange(size: number) {
    this.pageSize = size;
    this.pageNumber = 1;
    this.loadUsers();
  }

  onSortChange(sort: any) {
    this.sortBy = sort.sortBy;
    this.sortDir = sort.sortDir;
    this.loadUsers();
  }

  onFilterChange(filters: Map<string, string>) {
    this.filters = filters;
    this.pageNumber = 1;
    this.loadUsers();
  }

  openModule(id?: any, userData?: any) {
    const modalRef = this.modalService.open(CreateUpdateUserComponent, {
      backdrop:'static',
      backdropClass:'customBackdrop',
      size: 'lg',
    });

    if (this.isEditClicked) {
      modalRef.componentInstance.userData = userData;
      modalRef.componentInstance.id = id;
      modalRef.componentInstance.isEditMode = this.isEditClicked;
    }

    modalRef.closed.subscribe((result) => {
      if (result === 'success') {
        this.loadUsers();
      }
    });
  }

  onCreateUserData()  {
    this.isEditClicked = false;
    this.openModule();
  }

  onEditUserData(id: any, userData: any)  {
    this.isEditClicked = true;
    this.openModule(id, userData);
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
            this.loadUsers();
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
}
