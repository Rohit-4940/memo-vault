import {Component, Input} from '@angular/core';
import {NgbActiveModal} from '@ng-bootstrap/ng-bootstrap';

@Component({
  selector: 'app-delete-confirmation',
  standalone: true,
  imports: [],
  templateUrl: './delete-confirmation.component.html',
  styleUrl: './delete-confirmation.component.scss'
})
export class DeleteConfirmationComponent {
  @Input() id: any;
  @Input() message: string = 'Are you sure you want to delete this record ?'
  @Input() title: string = 'Confirm Disable'


  constructor(public activeModal: NgbActiveModal) {}
  confirmDelete() {
    this.activeModal.close(true);
  }
  cancel() {
    this.activeModal.dismiss(false);
  }
}
