export enum UserType {
  SUPER_ADMIN = 'Super Admin',
  ADMIN = 'Admin',
  CUSTOMER = 'Customer',
  EMPLOYEE = 'Employee',
  EMPLOYER = 'Employer',
  MANAGER = 'Manager',
  SUPERVISOR = 'Supervisor',
  DIRECTOR = 'Director'
}

export namespace UserType {
  /**
   * Returns all the keys of the UserType enum.
   */
  export function values(): string[] {
    return Object.keys(UserType).filter(
      (type) => isNaN(type as any) && type !== 'values' && type !== 'enumObject' && type !== 'getKeyByEnumValue' && type !== 'getLoanTypeValue'
    );
  }

  /**
   * Returns an array of objects containing key-value pairs of the UserType enum.
   */
  export function enumObject(): { key: string; value: any }[] {
    const enums: { key: string; value: any }[] = [];
    values().forEach((elem) => {
      enums.push({
        key: elem,
        value: UserType[elem as keyof typeof UserType]
      });
    });
    return enums;
  }

  /**
   * Finds and returns the key of a given enum value.
   * @param enumValue The value of the enum to find the key for.
   */
  export function getKeyByEnumValue(enumValue: any): string | undefined {
    return Object.keys(UserType).find(
      key => UserType[key as keyof typeof UserType] === enumValue
    );
  }
}
