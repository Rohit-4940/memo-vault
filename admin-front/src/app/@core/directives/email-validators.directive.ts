import {
  NG_VALIDATORS,
  Validator,
  AbstractControl,
  ValidationErrors
} from '@angular/forms';
import { Directive } from '@angular/core';

@Directive({
  selector: '[appValidEmail]',
  providers: [
    {
      provide: NG_VALIDATORS,
      useExisting: EmailValidatorDirective,
      multi: true
    }
  ],
  standalone: true // Modern Angular: Allows standalone usage
})
export class EmailValidatorDirective implements Validator {
  validate(control: AbstractControl<string>): ValidationErrors | null {
    const emailRegex =
      /^[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}$/i; // Improved regex with case insensitivity
    const isValid = emailRegex.test(control.value || '');
    return isValid
      ? null
      : { validEmail: { valid: false, message: 'Invalid email format' } };
  }
}
