export class Employee {
  id?: number;
  createdAt?: Date;
  updatedAt?: Date;
  createdBy?: Date;
  updatedBy?: Date;
  deleted?: boolean;
  deletedAt?: Date;
  deletedBy?: Date;
  name?: string;
  email?: string;
  description?: string;
  address?: string;
  status?: string;
  hrId? : string;
  hr?: {
    name: string;
  }
}
