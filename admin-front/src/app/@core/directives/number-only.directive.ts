import {Directive, ElementRef, HostListener} from '@angular/core';

@Directive({
  selector: '[appNumberOnly]',
  standalone: true
})
export class NumberOnlyDirective {
  constructor(private el: ElementRef) {}

  @HostListener('input', ['$event']) onInput(event: Event): void {
    const inputElement = this.el.nativeElement as HTMLInputElement;
    const currentValue = inputElement.value;

    // Remove any non-numeric characters
    inputElement.value = currentValue.replace(/[^0-9]/g, '');

    // Trigger an input event to notify Angular of the value change
    inputElement.dispatchEvent(new Event('input'));
  }

}
