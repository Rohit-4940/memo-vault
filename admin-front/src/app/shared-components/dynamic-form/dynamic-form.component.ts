import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';
import { NgForOf, NgIf, NgStyle, KeyValuePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-dynamic-form',
  standalone: true,
  imports: [NgForOf, NgIf, NgStyle, FormsModule, KeyValuePipe],
  template: `
    @if (!fields || fields.length === 0) {
      <p class="text-muted">No form fields configured for this memo type.</p>
    }
    @for(field of fields; track field.id || field.fieldKey) {
      @if (isFieldVisible(field)) {
        <div class="form-group mb-3">
          <label [for]="field.fieldKey" class="form-label">
            {{ field.fieldLabel }}
            @if (field.required) { <span class="text-danger">*</span> }
          </label>

          @switch (field.fieldType) {
            @case ('TEXT') {
              <input [id]="field.fieldKey" type="text" class="form-control"
                [placeholder]="field.placeholder || ''"
                [(ngModel)]="formValues[field.fieldKey]"
                [required]="field.required"
                [readonly]="field.readOnly"
                (ngModelChange)="onFieldChange()" />
            }
            @case ('NUMBER') {
              <input [id]="field.fieldKey" type="number" class="form-control"
                [placeholder]="field.placeholder || ''"
                [(ngModel)]="formValues[field.fieldKey]"
                [required]="field.required"
                [readonly]="field.readOnly"
                (ngModelChange)="onFieldChange()" />
            }
            @case ('CURRENCY') {
              <div class="input-group">
                <span class="input-group-text">NPR</span>
                <input [id]="field.fieldKey" type="number" class="form-control"
                  [placeholder]="field.placeholder || ''"
                  [(ngModel)]="formValues[field.fieldKey]"
                  [required]="field.required"
                  [readonly]="field.readOnly"
                  (ngModelChange)="onFieldChange()" />
              </div>
            }
            @case ('DATE') {
              <input [id]="field.fieldKey" type="date" class="form-control"
                [(ngModel)]="formValues[field.fieldKey]"
                [required]="field.required"
                [readonly]="field.readOnly"
                (ngModelChange)="onFieldChange()" />
            }
            @case ('DATETIME') {
              <input [id]="field.fieldKey" type="datetime-local" class="form-control"
                [(ngModel)]="formValues[field.fieldKey]"
                [required]="field.required"
                [readonly]="field.readOnly"
                (ngModelChange)="onFieldChange()" />
            }
            @case ('DROPDOWN') {
              <select [id]="field.fieldKey" class="form-control"
                [(ngModel)]="formValues[field.fieldKey]"
                [required]="field.required"
                [disabled]="field.readOnly"
                (ngModelChange)="onFieldChange()">
                <option value="">-- Select --</option>
                @for(opt of getFieldOptions(field); track opt) {
                  <option [value]="opt">{{ opt }}</option>
                }
              </select>
            }
            @case ('MULTI_SELECT') {
              <select [id]="field.fieldKey" class="form-control" multiple
                [(ngModel)]="formValues[field.fieldKey]"
                [required]="field.required"
                [disabled]="field.readOnly"
                (ngModelChange)="onFieldChange()">
                @for(opt of getFieldOptions(field); track opt) {
                  <option [value]="opt">{{ opt }}</option>
                }
              </select>
            }
            @case ('RADIO') {
              <div>
                @for(opt of getFieldOptions(field); track opt) {
                  <label class="radio-inline d-block mb-1">
                    <input type="radio" [name]="field.fieldKey" [value]="opt"
                      [(ngModel)]="formValues[field.fieldKey]"
                      [required]="field.required"
                      [disabled]="field.readOnly"
                      (ngModelChange)="onFieldChange()" />
                    {{ opt }}
                  </label>
                }
              </div>
            }
            @case ('CHECKBOX') {
              <div>
                @for(opt of getFieldOptions(field); track opt) {
                  <label class="checkbox-inline d-block mb-1">
                    <input type="checkbox" [value]="opt"
                      [checked]="isCheckboxChecked(field, opt)"
                      (change)="toggleCheckbox(field, opt, $event)"
                      [disabled]="field.readOnly" />
                    {{ opt }}
                  </label>
                }
              </div>
            }
            @case ('TEXTAREA') {
              <textarea [id]="field.fieldKey" class="form-control" rows="4"
                [placeholder]="field.placeholder || ''"
                [(ngModel)]="formValues[field.fieldKey]"
                [required]="field.required"
                [readonly]="field.readOnly"
                (ngModelChange)="onFieldChange()"></textarea>
            }
            @default {
              <input [id]="field.fieldKey" type="text" class="form-control"
                [placeholder]="field.placeholder || ''"
                [(ngModel)]="formValues[field.fieldKey]"
                [required]="field.required"
                [readonly]="field.readOnly"
                (ngModelChange)="onFieldChange()" />
            }
          }

          @if (field.helpText) {
            <small class="form-text text-muted">{{ field.helpText }}</small>
          }
          @if (field.required && showValidation && !formValues[field.fieldKey]) {
            <small class="text-danger">{{ field.fieldLabel }} is required</small>
          }
        </div>
      }
    }
  `,
  styles: ['.radio-inline, .checkbox-inline { cursor: pointer; }']
})
export class DynamicFormComponent implements OnChanges {
  @Input() fields: any[] = [];
  @Input() initialData: any = {};
  @Input() showValidation = false;
  @Output() formDataChange = new EventEmitter<any>();

  formValues: any = {};

  ngOnChanges(changes: SimpleChanges) {
    if (changes['initialData'] && this.initialData) {
      this.formValues = { ...this.initialData };
    }
    if (changes['fields']) {
      this.applyDefaults();
    }
  }

  applyDefaults() {
    if (this.fields) {
      for (const field of this.fields) {
        if (!(field.fieldKey in this.formValues) && field.defaultValue) {
          this.formValues[field.fieldKey] = field.defaultValue;
        }
      }
    }
  }

  onFieldChange() {
    this.formDataChange.emit(this.formValues);
  }

  getFieldOptions(field: any): string[] {
    if (!field.options) return [];
    try { return JSON.parse(field.options); } catch { return field.options.split(',').map((s: string) => s.trim()); }
  }

  isCheckboxChecked(field: any, opt: string): boolean {
    const val = this.formValues[field.fieldKey];
    return Array.isArray(val) && val.includes(opt);
  }

  toggleCheckbox(field: any, opt: string, event: any) {
    if (!this.formValues[field.fieldKey]) this.formValues[field.fieldKey] = [];
    const arr = this.formValues[field.fieldKey] as string[];
    if (event.target.checked) {
      if (!arr.includes(opt)) arr.push(opt);
    } else {
      const idx = arr.indexOf(opt);
      if (idx >= 0) arr.splice(idx, 1);
    }
    this.onFieldChange();
  }

  isFieldVisible(field: any): boolean {
    if (!field.conditionalLogic) return true;
    try {
      const rule = JSON.parse(field.conditionalLogic);
      return this.formValues[rule.fieldKey] === rule.value;
    } catch { return true; }
  }

  isValid(): boolean {
    if (!this.fields) return true;
    for (const field of this.fields) {
      if (field.required) {
        const val = this.formValues[field.fieldKey];
        if (val === null || val === undefined || val === '') return false;
        if (Array.isArray(val) && val.length === 0) return false;
      }
    }
    return true;
  }

  getFormDataJson(): string {
    return JSON.stringify(this.formValues);
  }
}
