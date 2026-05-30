import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TimeOutPopUpComponent } from './time-out-pop-up.component';

describe('TimeOutPopUpComponent', () => {
  let component: TimeOutPopUpComponent;
  let fixture: ComponentFixture<TimeOutPopUpComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TimeOutPopUpComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(TimeOutPopUpComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
