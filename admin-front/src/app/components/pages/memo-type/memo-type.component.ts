import { Component, OnInit, inject } from '@angular/core';
import { NgForOf, NgIf } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { MemoTypeService } from '../../../shared-services/api-service/memo-type.service';
import { ToastrService } from 'ngx-toastr';
import { LocalStorageUtil } from '../../../@core/utils/local-storage-util';

@Component({
  selector: 'app-memo-type',
  standalone: true,
  imports: [NgForOf, NgIf, FormsModule, NgbModalModule],
  templateUrl: './memo-type.component.html',
  styleUrl: './memo-type.component.scss'
})
export class MemoTypeComponent implements OnInit {
  memoTypes: any[] = [];
  selectedMemoType: any = { name: '', description: '', status: 'ACTIVE' };
  isEditing = false;
  isAdmin = false;
  private modalService = inject(NgbModal);

  constructor(
    private memoTypeService: MemoTypeService,
    private toastr: ToastrService
  ) {}

  ngOnInit() {
    const storage = LocalStorageUtil.getStorage();
    this.isAdmin = storage.roleName === 'ADMIN' || storage.roleName === 'SUPER_ADMIN';
    this.loadMemoTypes();
  }

  loadMemoTypes() {
    this.memoTypeService.getAllMemoTypes().subscribe({
      next: (data) => this.memoTypes = data,
      error: () => this.toastr.error('Failed to load memo types')
    });
  }

  openCreateModal(content: any) {
    this.isEditing = false;
    this.selectedMemoType = { name: '', description: '', status: 'ACTIVE' };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  openEditModal(content: any, memoType: any) {
    this.isEditing = true;
    this.selectedMemoType = { ...memoType };
    this.modalService.open(content, { size: 'lg', centered: true });
  }

  saveMemoType() {
    const obs = this.isEditing
      ? this.memoTypeService.updateMemoType(this.selectedMemoType.id, this.selectedMemoType)
      : this.memoTypeService.createMemoType(this.selectedMemoType);
    obs.subscribe({
      next: () => {
        this.toastr.success(this.isEditing ? 'Memo type updated' : 'Memo type created');
        this.modalService.dismissAll();
        this.loadMemoTypes();
      },
      error: (err) => this.toastr.error(err.error?.message || 'Failed')
    });
  }

  deleteMemoType(id: number) {
    if (confirm('Delete this memo type?')) {
      this.memoTypeService.deleteMemoType(id).subscribe({
        next: () => { this.toastr.success('Memo type deleted'); this.loadMemoTypes(); },
        error: () => this.toastr.error('Delete failed')
      });
    }
  }
}
