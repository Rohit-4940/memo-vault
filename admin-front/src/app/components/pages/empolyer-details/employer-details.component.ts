import {Component, inject, OnInit} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {EmployeeService} from '../../../shared-services/api-service/employee.service';
import {EmployerLoanConfigService} from '../../../shared-services/api-service/employer-loan-config.service';
import {HumanResourcesService} from '../../../shared-services/api-service/human-resources.service';
import {NgbModal} from '@ng-bootstrap/ng-bootstrap';
import {ToastrService} from 'ngx-toastr';
import {PaginationUtils} from '../../../@core/utils/PaginationUtils';
import {SpinnerService} from '../../../@theme/spinner/service/spinner.service';
import {ObjectUtil} from '../../../@core/utils/ObjectUitls';
import {NgIf} from '@angular/common';
import {NbAccordionModule} from '@nebular/theme';
import {LocalStorageUtil} from '../../../@core/utils/local-storage-util';
import {NumberOnlyDirective} from '../../../@core/directives/number-only.directive';
import {CustomNumberDirective} from '../../../@core/directives/custom-number.directive';

@Component({
  selector: 'app-employer-details',
  standalone: true,
  imports: [
    NgIf,
    ReactiveFormsModule,
    NbAccordionModule,
    NumberOnlyDirective,
    CustomNumberDirective
  ],
  templateUrl: './employer-details.component.html',
  styleUrl: './employer-details.component.scss'
})
export class EmployerDetailsComponent implements OnInit {
  employerLoanConfigurationForm: FormGroup = new FormGroup<any>({});
  employeeForm: FormGroup = new FormGroup<any>({});
  id!: string;
  employerDetail: any;
  spinnerService: SpinnerService = inject(SpinnerService);
  isServiceFlag: boolean = false;
  isInterestFlag: boolean = false;
  isPenaltyFlag: boolean = false;


  constructor(private formBuilder: FormBuilder,
              private employeeService: EmployeeService,
              private employerLoanConfigService: EmployerLoanConfigService,
              private humanResourcesServices: HumanResourcesService,
              private modalService: NgbModal,
              private toastService: ToastrService,
              private route: ActivatedRoute) {
  }

  ngOnInit(): void {
    this.buildForm();
    const storage = LocalStorageUtil.getStorage();
    this.employerLoanConfigurationForm.patchValue({modifiedBy: storage.username})
    this.id = this.route.snapshot.paramMap.get('id')!;
    this.route.paramMap.subscribe(params => {
      this.id = params.get('id')!;
    });
    if(!ObjectUtil.isEmpty(this.id)){
      this.loadEmployer();
      this.employerLoanConfigurationForm.patchValue({employerId: this.id});
    }
  }

  buildForm(){
    this.employerLoanConfigurationForm = this.formBuilder.group({
      employerId: [''],
      newConfig: this.formBuilder.group({
        serviceChargeEnabled: [false],
        serviceChargeType: ['PERCENTAGE'],
        serviceChargeValue: [''],
        interestEnabled: [false],
        interestType: ['PERCENTAGE'],
        interestValue: [''],
        renewalAllowed: [false],
        penaltyEnabled: [false],
        penaltyType: ['FLAT'],
        penaltyValue: [''],
        gracePeriodDays: ['', Validators.required],
        settlementDay: ['', Validators.required],
        settlementType: ['', Validators.required],
        partialSettlementAllowed: [false],
        eligiblePercentage: ['', Validators.required],
        minimumAmount: ['', Validators.required],
        totalMaximumAmountPerMonth: ['', Validators.required],
        individualMaximumAmount: ['', Validators.required],
        payDay: ['', Validators.required],
      }),
      reason: ['xxx'],
      modifiedBy: [''],
    });
  }
  public loadEmployer() {
    this.spinnerService.show();

    // Fetch the employer details from API
    this.employeeService.getById(this.id).subscribe({
      next: (response) => {
        this.employerDetail = response?.data || null;
        this.employerLoanConfigurationForm.patchValue({
          modifiedBy: LocalStorageUtil.getStorage().username,
          ...this.employerDetail
        });

        this.toastService.success('Success', 'Employer Fetch Successfully');
      },
      error: (error) => {
        this.spinnerService.hide();
        this.toastService.error('Error', 'Unable to fetch employer');
        console.error('Error fetching Employer:', error?.message);
      },
      complete: () => {
        this.spinnerService.hide();
      }
    });

    // Fetch employer loan configuration and patch the form
    this.employerLoanConfigService.get(this.id).subscribe({
      next: (response) => {
        if (response?.data) {
          this.employerLoanConfigurationForm.patchValue({newConfig: response?.data});
          this.toastService.success('Success', 'Employer Loan Configuration Fetched Successfully');

          // Check if serviceChargeType exists and enable toggle
          if (response?.data?.serviceChargeEnabled) {
            this.isServiceFlag = true;
            this.toggleServiceCharge({ target: { checked: true } });
          } else {
            this.isServiceFlag = false;
            this.toggleServiceCharge({ target: { checked: false } });
          }

          if (response?.data?.interestEnabled) {
            this.isInterestFlag = true;
            this.toggleInterestType({ target: { checked: true } });
          }else {
            this.isInterestFlag = false;
            this.toggleInterestType({ target: { checked: false } });
          }

          if (response?.data?.penaltyEnabled) {
            this.isPenaltyFlag = true;
            this.togglePenalty({ target: { checked: true } });
          }else{
            this.isPenaltyFlag = false;
            this.togglePenalty({ target: { checked: false } });
          }
        }
      },
      error: (error) => {
        this.spinnerService.hide();
        this.toastService.error('Error', 'Unable to fetch employer loan configuration');
        console.error('Error fetching Employer Loan Configuration:', error?.message);
      },
      complete: () => {
        this.spinnerService.hide();
      }
    });
  }

  public createEmployerLoanConfiguration() {
    if (this.employerLoanConfigurationForm.valid) {
      this.employerLoanConfigService.create(this.employerLoanConfigurationForm.value, this.id).subscribe(
        (response) => {
          this.toastService.success('Success', 'Employer Loan Configuration Successfully');
          this.modalService.dismissAll();
          localStorage.setItem('employerLoanConfigData', JSON.stringify(this.employerLoanConfigurationForm.value));

          this.loadEmployer();
        },
        (error) => {
          this.toastService.error('Error', 'Cannot create Employer Loan Configuration');
          console.error('Error creating Employer Loan Configuration:', error);
        }
      );
    } else {
      this.toastService.info('Error', 'Form is invalid, please fill the form');
      console.log('Form is invalid');
    }
  }




  toggleServiceCharge(event: any) {
    const isChecked = event.target.checked;
    const serviceChargeTypeControl = this.employerLoanConfigurationForm.get('newConfig.serviceChargeType');
    const serviceChargeValueControl = this.employerLoanConfigurationForm.get('newConfig.serviceChargeValue');

    if (isChecked) {
      this.isServiceFlag = true;
      serviceChargeTypeControl?.setValidators([Validators.required]);
      serviceChargeValueControl?.setValidators([Validators.required]);
    } else {
      this.isServiceFlag = false;
      serviceChargeTypeControl?.clearValidators();
      serviceChargeValueControl?.clearValidators();
    }
    serviceChargeTypeControl?.updateValueAndValidity();
    serviceChargeValueControl?.updateValueAndValidity();
  }


  toggleInterestType(event: any) {
    const isChecked1 = event.target.checked;
    const interestTypeControl = this.employerLoanConfigurationForm.get('newConfig.interestType');
    const interestValueControl = this.employerLoanConfigurationForm.get('newConfig.interestValue');

    if (isChecked1) {
      this.isInterestFlag = true;
      interestTypeControl?.setValidators([Validators.required]);
      interestValueControl?.setValidators([Validators.required]);
    } else {
      this.isInterestFlag = false;
      interestTypeControl?.clearValidators();
      interestValueControl?.clearValidators();
    }
    interestTypeControl?.updateValueAndValidity();
    interestValueControl?.updateValueAndValidity();
  }

  togglePenalty(event: any) {
    const isChecked2 = event.target.checked;
    const penaltyTypeControl = this.employerLoanConfigurationForm.get('newConfig.penaltyType');
    const penaltyValueControl = this.employerLoanConfigurationForm.get('newConfig.penaltyValue');

    if (isChecked2) {
      this.isPenaltyFlag = true;
      penaltyTypeControl?.setValidators([Validators.required]);
      penaltyValueControl?.setValidators([Validators.required]);
    } else {
      this.isPenaltyFlag = false;
      penaltyTypeControl?.clearValidators();
      penaltyValueControl?.clearValidators();
    }
    penaltyTypeControl?.updateValueAndValidity();
    penaltyValueControl?.updateValueAndValidity();
  }

}
