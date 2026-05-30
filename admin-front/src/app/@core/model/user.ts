export class User {
  id: number | undefined;
  name: string | undefined;
  email: string | undefined;
  username: string | undefined;
  contactNo: string | undefined;
  address: string | undefined;
  userType: string | undefined;
  roles: string | undefined;
  attributes: {} | undefined;
  department: string | undefined;
  branch: string | undefined;
  active: boolean | undefined;
  createdAt?: Date;
  lastModifiedAt?: Date;

  password?: string;
  profilePicture?: string;
  signatureImage?: string;
  status?: string;
  fingerPrint?: [];
  resetPasswordToken?: string;
  resetPasswordTokenExpiry?: Date;
  roleList?: [];
  primaryUserId?: any;
  defaultPassword?: boolean;
}
