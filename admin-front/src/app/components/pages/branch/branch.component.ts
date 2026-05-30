import { Component, OnInit, inject } from '@angular/core';
import { NgForOf, NgIf } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { BranchService } from '../../../shared-services/api-service/branch.service';
import { ToastrService } from 'ngx-toastr';

@Component({
  selector: 'app-branch',
  standalone: true,
  imports: [NgForOf, NgIf, FormsModule, NgbModalModule],
  templateUrl: './branch.component.html',
  styleUrl: './branch.component.scss'
})
export class BranchComponent implements OnInit {
  branches: any[] = [];
  selectedBranch: any = { name: '', branchCode: '', streetName: '', wardNumber: '', branchEmail: '' };
  isEditing = false;
  private modalService = inject(NgbModal);
  constructor(private branchService: BranchService, private toastr: ToastrService) {}

  ngOnInit() { this.loadBranches(); }

  loadBranches() {
    this.branchService.getAllBranches().subscribe({
      next: (data) => this.branches = data,
      error: () => this.toastr.error('Failed to load branches')
    });
  }

  openCreateModal(content: any) {
    this.isEditing = false;
    this.selectedBranch = { name: '', branchCode: '', streetName: '', wardNumber: '', branchEmail: '' };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  openEditModal(content: any, branch: any) {
    this.isEditing = true;
    this.selectedBranch = { ...branch };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  saveBranch() {
    const obs = this.isEditing
      ? this.branchService.updateBranch(this.selectedBranch.id, this.selectedBranch)
      : this.branchService.createBranch(this.selectedBranch);
    obs.subscribe({
      next: () => { this.toastr.success(this.isEditing ? 'Branch updated' : 'Branch created'); this.modalService.dismissAll(); this.loadBranches(); },
      error: (err) => this.toastr.error(err.error?.message || 'Failed')
    });
  }

  deleteBranch(id: number) {
    if (confirm('Delete this branch?')) {
      this.branchService.deleteBranch(id).subscribe({
        next: () => { this.toastr.success('Branch deleted'); this.loadBranches(); },
        error: () => this.toastr.error('Delete failed')
      });
    }
  }
}
