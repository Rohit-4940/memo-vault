export class ObjectUtil {
  static isEmpty(input: any) {
    return (
      input === undefined ||
      input === null ||
      input === '' ||
      input === 'null' ||
      input === 'undefined' ||
      input === 'Infinity' ||
      (typeof input === 'object' && Object.keys(input).length === 0) ||
      (Array.isArray(input) && input.length === 0) ||
      (typeof input === 'string' && input.trim().length === 0)
    );
  }

  public static setUndefinedIfNull(input: any): any {
    return this.isEmpty(input) ? undefined : input;
  }

  public static setInputOrElseNext(input: any, next: any): any {
    return this.isEmpty(input) ? next : input;
  }

  public static separateFirstDash(input: any) {
    if (this.isEmpty(input)) {
      return undefined;
    }
    const value = input.split('-');
    return value[0] ? value[0] : undefined;
  }

  public static scrollToFirstInvalidControl(el: any) {
    const firstInvalidControl: HTMLElement =
      el.querySelector('input.ng-invalid') || el.querySelector('select.ng-invalid');
    if (!this.isEmpty(firstInvalidControl)) {
      window.scroll({
        top: this.getTopOffset(firstInvalidControl),
        left: 0,
        behavior: 'smooth',
      });
      firstInvalidControl.focus();
    }
  }

  private static getTopOffset(controlEl: HTMLElement): number {
    const labelOffset = 50;
    return controlEl.getBoundingClientRect().top + window.scrollY - labelOffset;
  }
}
