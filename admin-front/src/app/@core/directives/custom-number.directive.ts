import {Directive, ElementRef, HostListener} from '@angular/core';

@Directive({
  selector: '[appCustomNumber]',
  standalone: true
})
export class CustomNumberDirective {
  constructor(private el: ElementRef) {}

  @HostListener('input', ['$event']) onInput(event: Event): void {
    const inputElement = this.el.nativeElement as HTMLInputElement;
    const currentValue = inputElement.value;

    // Allow numbers, a single dot, and percentage
    inputElement.value = currentValue.replace(/[^0-9.%]/g, '');

    // Prevent more than one dot or misplaced percentage
    if ((inputElement.value.match(/\./g) || []).length > 1) {
      inputElement.value = inputElement.value.replace(/\.+$/, ''); // Remove extra dots
    }

    if ((inputElement.value.match(/%/g) || []).length > 1 || inputElement.value.indexOf('%') !== inputElement.value.length - 1) {
      inputElement.value = inputElement.value.replace(/%/g, ''); // Remove misplaced or multiple %
    }

    // Trigger an input event to notify Angular of the value change
    inputElement.dispatchEvent(new Event('input'));
  }

}
