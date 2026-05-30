import {Component, inject, OnInit} from '@angular/core';
import {LoanService} from '../../../shared-services/api-service/loan.service';
import {SpinnerService} from '../../../@theme/spinner/service/spinner.service';
import {ToastrService} from 'ngx-toastr';
import {LoanShortName} from '../../../@core/emun/loan-short-name';
import {NgFor, NgForOf, NgIf, TitleCasePipe} from '@angular/common';
import {NgbPagination} from '@ng-bootstrap/ng-bootstrap';
import {Pageable} from '../../../@core/model/pagable';
import {PaginationUtils} from '../../../@core/utils/PaginationUtils';
import {SbPaginationComponent} from '../../../@theme/sb-pagination/sb-pagination.component';
import {Router} from '@angular/router';

@Component({
  selector: 'app-loan-status',
  standalone: true,
  imports: [
    TitleCasePipe,
    NgIf,
    NgbPagination,
    SbPaginationComponent,
    NgForOf,
    NgFor
  ],
  templateUrl: './loan-status.component.html',
  styleUrl: './loan-status.component.scss'
})
export class LoanStatusComponent implements OnInit{

  loanList: Array<any> = [];
  spinnerService = inject(SpinnerService);
  toasterService = inject(ToastrService);
  loanService = inject(LoanService);
  loanShortName = LoanShortName;
  pageable: Pageable = new Pageable();
  router = inject(Router);

  ngOnInit() {
    this.getAllLoanList();
  }

  getAllLoanList() {
    this.spinnerService.show();
    this.loanService.getAllLoan(this.pageable.pageNumber, this.pageable.pageSize)
      .subscribe({
        next: (response) => {
          this.loanList = response?.data || [];
          console.log('loanlist', this.loanList);
          this.pageable = PaginationUtils.getPageable(response.meta);
          this.toasterService.success('Success', 'Loans fetched successfully');
        },
        error: (error) => {
          this.spinnerService.hide();
          this.toasterService.error('Error', 'Unable to retrieve loans');
          console.error('Error fetching loans:', error?.message);
        },
        complete: () => {
          this.spinnerService.hide();
        }
      });
  }

  onPageChange(page: number): void {
    this.pageable.pageNumber = page;
    this.getAllLoanList();
  }

  onPageSizeChange(pageSize: number): void {
    this.pageable.pageSize = pageSize;
    this.getAllLoanList();
  }
  openLoanSummary(loan: any): void {
    if (loan && loan.id) {
      console.log('Navigating to loan ID:', loan.id);
      this.router.navigate(['layout/loan-summary', loan.id]);
    } else {
      console.error('Loan ID is missing');
    }
  }

}
