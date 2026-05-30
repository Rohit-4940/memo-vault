export enum LoanShortName {
  LOAN_AGAINST_SALARY = 'Loan Against Share',
  PDL = 'Payday Loan',
  HL = 'Home Loan',
  AL = 'Auto Loan'
}

export namespace LoanShortName {
  /**
   * Returns all the keys of the LoanShortName enum.
   */
  export function values(): string[] {
    return Object.keys(LoanShortName).filter(
      (type) => isNaN(type as any) && type !== 'values' && type !== 'enumObject' && type !== 'getKeyByEnumValue' && type !== 'getLoanTypeValue'
    );
  }

  /**
   * Returns an array of objects containing key-value pairs of the LoanShortName enum.
   */
  export function enumObject(): { key: string; value: any }[] {
    const enums: { key: string; value: any }[] = [];
    values().forEach((elem) => {
      enums.push({
        key: elem,
        value: LoanShortName[elem as keyof typeof LoanShortName]
      });
    });
    return enums;
  }

  /**
   * Finds and returns the key of a given enum value.
   * @param enumValue The value of the enum to find the key for.
   */
  export function getKeyByEnumValue(enumValue: any): string | undefined {
    return Object.keys(LoanShortName).find(
      key => LoanShortName[key as keyof typeof LoanShortName] === enumValue
    );
  }

  /**
   * Returns the value associated with a given key from the LoanShortName enum.
   * @param key The key of the enum.
   */
  export function getLoanTypeValue(key: string): any {
    const validKeys = Object.keys(LoanShortName) as Array<keyof typeof LoanShortName>;

    if (validKeys.includes(key as keyof typeof LoanShortName)) {
      return LoanShortName[key as keyof typeof LoanShortName];
    }
    return undefined;
  }

}
