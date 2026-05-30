import {Component, inject, OnInit} from '@angular/core';
import {EmployeeService} from '../../../shared-services/api-service/employee.service';
import {JsonPipe, NgClass, NgFor, NgIf} from '@angular/common';
import {FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators} from '@angular/forms';
import {Employee} from '../../../@core/model/employee';
import {Status} from '../../../@core/emun/status';
import {NgbCollapse, NgbModal} from '@ng-bootstrap/ng-bootstrap';
import {ToastrService} from 'ngx-toastr';
import {SpinnerService} from '../../../@theme/spinner/service/spinner.service';
import {HumanResourcesService} from '../../../shared-services/api-service/human-resources.service';
import {DeleteConfirmationComponent} from '../../@common-component/delete-confirmation/delete-confirmation.component';
import {ShowMoreLessComponent} from '../../@common-component/show-more-less/show-more-less.component';
import {SbPaginationComponent} from "../../../@theme/sb-pagination/sb-pagination.component";
import {Pageable} from '../../../@core/model/pagable';
import {PaginationUtils} from '../../../@core/utils/PaginationUtils';
import {NgSelectComponent} from '@ng-select/ng-select';
import {EmployerLoanConfigService} from '../../../shared-services/api-service/employer-loan-config.service';
import {LocalStorageUtil} from '../../../@core/utils/local-storage-util';
import {Router} from '@angular/router';
import {EmailValidatorDirective} from '../../../@core/directives/email-validators.directive';

@Component({
  selector: 'app-employee',
  standalone: true,
  imports: [NgFor, FormsModule, NgIf, ReactiveFormsModule, ShowMoreLessComponent, SbPaginationComponent, NgSelectComponent, NgbCollapse, EmailValidatorDirective, NgClass],
  templateUrl: './employee.component.html',
  styleUrl: './employee.component.scss'
})
export class EmployeeComponent implements OnInit {
  router: Router = new Router();
  employeeForm: FormGroup = new FormGroup<any>({});
  userForm: FormGroup = new FormGroup<any>({});
  employerLoanConfigurationForm: FormGroup = new FormGroup<any>({});
  employeeList: Array<any> = new Array<any>();
  humanResourcesList: Array<any> = new Array<any>();
  statusOptions = Object.keys(Status);
  selectEmployee: Employee | null = null;
  spinnerService: SpinnerService = inject(SpinnerService);
  pageable: Pageable = new Pageable();
  collapseAccountFiled = true;
  showPassword: boolean = false;



  constructor(private formBuilder: FormBuilder,
              private employeeService: EmployeeService,
              private employerLoanConfigService: EmployerLoanConfigService,
              private humanResourcesServices: HumanResourcesService,
              private modalService: NgbModal,
              private toastService: ToastrService) {
  }

  ngOnInit() {
    this.buildForm();
    this.buildUserForm();
    this.getEmployeeList();
    this.getHumanResourcesList();
    const storage = LocalStorageUtil.getStorage();
    this.employerLoanConfigurationForm.patchValue({modifiedBy: storage.username});
  }

  buildForm() {
    this.employeeForm = this.formBuilder.group({
      name: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      status: ['', Validators.required],
      address: ['', Validators.required],
      hrId: ['', Validators.required],
      userName: ['', Validators.required],
      userEmail: ['', Validators.required],
      password: ['', Validators.required],
      description: ['', Validators.maxLength(256)]
    });

    this.employerLoanConfigurationForm = this.formBuilder.group({
      employerId: [''],
      newConfig: this.formBuilder.group({
        serviceChargeEnabled: [false, Validators.required],
        serviceChargeType: ['', Validators.required],
        serviceChargeValue: ['', Validators.required],
        interestEnabled: [false, Validators.required],
        interestType: ['', Validators.required],
        interestValue: ['', Validators.required],
        renewalAllowed: [false, Validators.required],
        penaltyEnabled: [false, Validators.required],
        penaltyType: ['', Validators.required],
        penaltyValue: ['', Validators.required],
        gracePeriodDays: ['', Validators.required],
        settlementDay: ['', Validators.required],
        settlementType: ['', Validators.required],
        partialSettlementAllowed: [false, Validators.required],
      }),
      modifiedBy: [''],
      reason: [''],
    });
  }

  getEmployeeList() {
    this.spinnerService.show();
    this.employeeService.getAllEmployee(this.pageable.pageNumber, this.pageable.pageSize)
      .subscribe({
        next: (response) => {
          this.employeeList = response?.data || [];
          this.pageable = PaginationUtils.getPageable(response.meta);
          this.toastService.success('Success', 'Employer Fetch Successfully');
        },
        error: (error) => {
          this.spinnerService.hide();
          this.toastService.error('Error', 'Unable to fetch employee ');
          console.error('Error fetching Employee:', error?.message);
        },
        complete: () => {
          this.spinnerService.hide();
        }
      });
  }

  public createEmployee() {
    if (this.employeeForm.valid) {
      const employee: Employee = this.employeeForm.value;
      employee.hrId = this.employeeForm.get('hrId')?.value?.id;
      this.employeeService.createEmployee(employee).subscribe(
        response => {
          this.toastService.success('Success', 'EmployerCreated Successfully');
          this.modalService.dismissAll();
          this.getEmployeeList();
          this.employeeForm.reset();
        },
        error => {
          this.toastService.error('Error', 'Cannot create Employee');
          console.error('Error creating Employee:', error);
        }
      );
    } else {
      this.toastService.info('Error', 'Form is invalid please fill the form');
      console.log('Form is invalid');
    }
  }

  public updateEmployee(): void {
    if (this.employeeForm.valid && this.selectEmployee) {
      const emp: Employee = {
        ...this.selectEmployee,
        ...this.employeeForm.value,
        hr: undefined,
        hrId: this.employeeForm.value.hrId?.id
      };
      this.employeeService.updateEmployee(emp).subscribe(
        response => {
          this.toastService.success('Success', 'Employer Updated Successfully');
          this.modalService.dismissAll();
          this.getEmployeeList();
          this.employeeForm.reset();
          this.selectEmployee = null;
        },
        error => {
          console.error('Error updating Employee:', error);
          this.toastService.error('Error', 'Failed to update Employee');
        }
      );
    } else {
      this.toastService.info('Success', 'Form is invalid please select the Data user');
      console.log('Form is invalid or no Employee is selected');
    }
  }

  public createEmployerLoanConfiguration() {
    if (this.employerLoanConfigurationForm.valid) {
      this.employerLoanConfigService.
      create(this.employerLoanConfigurationForm.value,
        this.employerLoanConfigurationForm.get('employerId')?.value).subscribe(
        response => {
          this.toastService.success('Success', 'Employer Loan Configuration Successfully');
          this.modalService.dismissAll();
          this.getEmployeeList();
          this.employeeForm.reset();
        },
        error => {
          this.toastService.error('Error', 'Cannot create Employer Loan Configuration');
          console.error('Error creating Employer Loan Configuration:', error);
        }
      );
    } else {
      this.toastService.info('Error', 'From is invalid please fill the form');
      console.log('Form is invalid');
    }
  }

  public openModal(modal: any, employee?: Employee) {
    if (employee) {
      this.selectEmployee = employee;
      this.employeeForm.patchValue({
        ...employee,
        hrId: employee?.hr
      });
    } else {
      this.selectEmployee = null;
      this.employeeForm.reset();
    }

    this.modalService.open(modal, {
      ariaLabelledBy: 'modal-basic-title',
      size: 'lg',
      backdrop: 'static'
    });
  }
  // public openUserModal(modal: any, employee?: Employee) {
  //   if (employee) {
  //     this.selectEmployee = employee;
  //   } else {
  //     this.selectEmployee = null;
  //   }
  //   this.modalService.open(modal, {
  //     ariaLabelledBy: 'modal-basic-title',
  //     size: 'lg',
  //     backdrop: 'static'
  //   });
  // }

  public openConfigurationModal(modal: any, employerId: any) {
    this.employerLoanConfigurationForm.patchValue({employerId: employerId});
    this.modalService.open(modal, {
      ariaLabelledBy: 'modal-basic-title',
      size: 'lg',
      backdrop: 'static'
    });
  }

  public getHumanResourcesList() {
    this.humanResourcesServices.getAllHumanResourceAll().subscribe({
      next: (human) => {
        this.humanResourcesList = human.data;
      },
      error: (err: any) => {
        console.error('Error fetching human resources data:', err?.message);
      },
      complete: () => {
        console.log('complete');
      }
    });
  }
  openDisableModal(employee: any){
    if (employee) {
      this.selectEmployee = employee;
    }
    const modalRef = this.modalService.open(DeleteConfirmationComponent, {
      size: 'm',
      backdrop: 'static',
      ariaLabelledBy: 'modal-basic-title',
    });
    modalRef.componentInstance.id = employee;
    if(employee?.status === 'ACTIVE'){
      modalRef.componentInstance.title = 'Disable Employee'
      modalRef.componentInstance.message = 'Are you sure do you want to disable Employer?';
    } else{
      modalRef.componentInstance.title = 'Enable Employer'
      modalRef.componentInstance.message = 'Are you sure do you want to enable Employer?';
    }

    modalRef.result.then((confirmed) => {
      if (confirmed) {
        this.employeeService.disablemployee(this.selectEmployee?.id).subscribe({
          next: (response) => {
            this.toastService.success('Success', 'Employer Status Changed successfully');
            window.location.reload();
            console.log('Disabled successfully', response);
          },
          error: (err) => {
            this.toastService.error('Error', 'Failed to disable Employer');
            console.error('Disable failed', err);
          },
        });
      }
    }).catch(() => {
    });
  }

  openDeleteModal(employeeId: string) {
    const modalRef = this.modalService.open(DeleteConfirmationComponent, {
      size: 'm',
      backdrop: 'static',
      ariaLabelledBy: 'modal-basic-title',
    });
    modalRef.componentInstance.id = employeeId;
    modalRef.componentInstance.title = 'Confirm Delete'
    modalRef.componentInstance.message = 'Are you sure do you want to delete Employee record ?';
    modalRef.result.then((confirmed) => {
      if (confirmed) {
        this.employeeService.deleteEmployee(employeeId).subscribe({
          next: (response) => {
            this.employeeList = this.employeeList.filter((employee: any) => employee?.id !== employeeId);
            this.toastService.success('Success', 'Employer deleted successfully');
            console.log('Deleted successfully', response);
          },
          error: (err) => {
            this.toastService.error('Error', 'Failed to delete Employee');
            console.error('Delete failed', err);
          },
        });
      }
    }).catch(() => {
    });
  }
  onPageChange(page: number): void {
    this.pageable.pageNumber = page;
    this.getEmployeeList();
  }

  onPageSizeChange(pageSize: number): void {
    this.pageable.pageSize = pageSize;
    this.getEmployeeList();
  }

  goToEmployerDetailsPage(id: any) {
    this.router.navigate([`/layout/employers-detail/${id}`])
  }

  buildUserForm(): void{
    this.userForm = this.formBuilder.group(
      {
        username: [undefined, Validators.required],
        password: [undefined, Validators.required]
      }
    );
  }

}
