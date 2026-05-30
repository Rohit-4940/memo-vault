import {Component, input, OnInit, output} from '@angular/core';
import {NgStyle} from '@angular/common';
import {ShowMoreLessComponent} from '../show-more-less/show-more-less.component';

@Component({
  selector: 'app-common-table',
  standalone: true,
  imports: [
    NgStyle,
    ShowMoreLessComponent
  ],
  templateUrl: './common-table.component.html',
  styleUrl: './common-table.component.scss'
})
export class CommonTableComponent implements OnInit{
  headerArray = input<Array<any>>();
  gridArray = input<Array<any>>();
  isAction = input<boolean>(false);

  onView = output<any>();
  onEdit = output<any>();
  onDelete = output<any>();

  ngOnInit(): void {
    console.log(this.headerArray);
  }

  onViewEvent(event: number) {
    this.onView.emit(event);
  }

  onEditEvent(event: number) {
    this.onEdit.emit(event);
  }

  onDeleteEvent(event: number) {
    this.onDelete.emit(event);
  }

}
