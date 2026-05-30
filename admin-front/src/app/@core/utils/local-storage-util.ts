import { CryptoJsUtil } from './crypto-js-util';
import { environment } from '../../../environments/environment';
import { ObjectUtil } from './ObjectUitls';

export class LocalStorageUtil {
  /**
   * @description Get an instance of LocalStorage.
   */
  public static getStorage(): LocalStorage {
    if (typeof window === 'undefined' || !window.localStorage) {
      return new LocalStorage(); // Return a new instance if not in a browser environment
    }

    const storageItem = localStorage.getItem(environment.LOCAL_STORAGE_NAME);
    if (ObjectUtil.isEmpty(storageItem)) {
      return new LocalStorage();
    }

    try {
      return JSON.parse(CryptoJsUtil.decrypt(storageItem));
    } catch (error) {
      console.error('Failed to parse localStorage data', error);
      return new LocalStorage(); // Return a new instance on error
    }
  }

  /**
   * @param data A local storage instance to save.
   * @description Make sure you use LocalStorageUtil.getStorage() method before
   * to get an instance of LocalStorage. Edit the instance and use
   * LocalStorageUtil.setStorage() to set in the browser's localStorage.
   */
  public static setStorage(data: LocalStorage): void {
    if (typeof window === 'undefined' || !window.localStorage) {
      console.warn('localStorage is not available. Data will not be saved.');
      return;
    }
    try {
      localStorage.setItem(environment.LOCAL_STORAGE_NAME, CryptoJsUtil.encrypt(JSON.stringify(data)));
    } catch (error) {
      console.error('Failed to set localStorage data', error);
    }
  }

  /**
   * @description Passes empty JSON to clear the storage.
   */
  public static clearStorage(): void {
    LocalStorageUtil.setStorage(new LocalStorage());
  }
  public static removeKey(key: string): void {
    if (typeof window === 'undefined' || !window.localStorage) {
      console.warn('localStorage is not available. Key will not be removed.');
      return;
    }
    localStorage.removeItem(key);
  }
}

export class LocalStorage {
  at: {
    accessToken: any;
    refreshToken: any;
  } | undefined;
  rt: any;
  ty: any;
  et: any;
  username: any;
  roleName: any;
  roleType: any;
  roleAccess: any;
  userId: any;
  userFullName: any;
  userProfilePicture: any;
  roleId: any;
  productMode: any;
  branch: any;
  calendar: any;
  defaultPassword: any;
  email: any;
}
