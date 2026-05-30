import { Component, inject, NgModule, OnInit } from '@angular/core';
import { HumanResourcesService } from '../../../shared-services/api-service/human-resources.service';
import { NgFor, NgIf } from '@angular/common';
import { HumanResources } from '../../../@core/model/human-resources';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Status } from '../../../@core/emun/status';
import { ToastrService } from 'ngx-toastr';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import {SpinnerService} from '../../../@theme/spinner/service/spinner.service';
import {DeleteConfirmationComponent} from '../../@common-component/delete-confirmation/delete-confirmation.component';
import {SbPaginationComponent} from "../../../@theme/sb-pagination/sb-pagination.component";
import {Pageable} from '../../../@core/model/pagable';
import {PaginationUtils} from '../../../@core/utils/PaginationUtils';
import {NgSelectComponent} from '@ng-select/ng-select';
import {ShowMoreLessComponent} from '../../@common-component/show-more-less/show-more-less.component';

@Component({
  selector: 'app-human-resources',
  standalone: true,
  imports: [
    NgFor,
    ReactiveFormsModule,
    NgIf,
    SbPaginationComponent,
    NgSelectComponent,
    ShowMoreLessComponent
  ],
  templateUrl: './human-resources.component.html',
  styleUrls: ['./human-resources.component.scss']
})
export class HumanResourcesComponent implements OnInit {
  humanResourcesForm: FormGroup = new FormGroup<any>({});
  humanResourcesList: Array<any> = new Array<any>();
  statusOptions = Object.keys(Status);
  selectedHumanResource: HumanResources | null = null;
  spinnerService: SpinnerService = inject(SpinnerService);

  pageable: Pageable = new Pageable();

  constructor(private formBuilder: FormBuilder,
              private humanResourcesService: HumanResourcesService,
              private modalService: NgbModal,
              private toastrService: ToastrService) { }

  ngOnInit() {
    this.buildForm();
    this.getHumanResourcesList();
  }

  buildForm() {
    this.humanResourcesForm = this.formBuilder.group({
      name: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      status: ['', Validators.required],
      address: ['', Validators.required],
      description: ['', Validators.maxLength(256)]
    });
  }
  getHumanResourcesList() {
    this.spinnerService.show();
    this.humanResourcesService.getAllHumanResources(this.pageable.pageNumber, this.pageable.pageSize)
      .subscribe({
        next: (response) => {
          this.humanResourcesList = response?.data || [];
          this.pageable = PaginationUtils.getPageable(response.meta);
          this.toastrService.success('Success', 'Human Resource Fetch Successfully');
        },
        error: (error) => {
          this.spinnerService.hide();
          this.toastrService.error('Error', 'Unable to retrieve Human resources Data ');
          console.error('Error fetching Human Resources:', error?.message);
        },
        complete: () => {
          this.spinnerService.hide();
        }
      });
  }
  public createHumanResources() {
    if (this.humanResourcesForm.valid) {
      const human: HumanResources = this.humanResourcesForm.value;
      this.humanResourcesService.createHumanResources(human).subscribe(
        response => {
          this.toastrService.success('Success', 'Human Resources Created Successfully');
          this.modalService.dismissAll();
          this.getHumanResourcesList();
          this.humanResourcesForm.reset();
        },
        error => {
          this.toastrService.error('Error', 'Cannot create Human Resources');
          console.error('Error creating Human Resources:', error);
        }
      );
    } else {
      this.toastrService.info('Error', 'From is invalid please fill the form');
      console.log('Form is invalid');
    }
  }

  public updateHumanResources(): void {
    if (this.humanResourcesForm.valid && this.selectedHumanResource) {
      const human: HumanResources = {
        ...this.selectedHumanResource,
        ...this.humanResourcesForm.value
      };
      this.humanResourcesService.updateHumanResources(human).subscribe(
        response => {
          this.toastrService.success('Success', 'Human Resource Updated Successfully');
          this.modalService.dismissAll();
          this.getHumanResourcesList();
          this.humanResourcesForm.reset();
          this.selectedHumanResource = null;},
        error => {
          console.error('Error updating human resource:', error);
          this.toastrService.error('Error', 'Failed to update Human Resource');
        }
      );
    } else {
      this.toastrService.info('Success', 'Form is invalid please select the Data user');
      console.log('Form is invalid or no human resource is selected');
    }
  }
  public openModal(modal: any, humanResource?: HumanResources) {
    if (humanResource) {
      this.selectedHumanResource = humanResource;
      this.humanResourcesForm.patchValue(humanResource);
    } else {
      this.selectedHumanResource = null;
      this.humanResourcesForm.reset();
    }

    this.modalService.open(modal, {
      ariaLabelledBy: 'modal-basic-title',
      size: 'lg',
      backdrop: 'static'
    });
  }
  openDeleteModal(humanId: string) {
    const modalRef = this.modalService.open(DeleteConfirmationComponent, {
      size: 'sm',
      backdrop: 'static',
      ariaLabelledBy: 'modal-basic-title',
    });
    modalRef.componentInstance.id = humanId;
    modalRef.componentInstance.message = 'Are you sure do you want to delete Human Resource record ?';
    modalRef.result.then((confirmed) => {
      if (confirmed) {
        this.humanResourcesService.deleteHumanResources(humanId).subscribe({
          next: (response) => {
            this.humanResourcesList = this.humanResourcesList.filter(human => human?.id !== humanId);
            this.toastrService.success('Success', 'Human Resource deleted successfully');
            console.log('Deleted successfully', response);
          },
          error: (err) => {
            this.toastrService.error('Error', 'Failed to delete Human Resource');
            console.error('Delete failed', err);
          },
        });
      }
    }).catch(() => {
    });
  }

  onPageChange(page: number): void {
    this.pageable.pageNumber = page;
    this.getHumanResourcesList();
  }

  onPageSizeChange(pageSize: number): void {
    this.pageable.pageSize = pageSize;
    this.getHumanResourcesList();
  }
}
