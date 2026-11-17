// These types mirror the JSON the Spring Boot API returns (see Dtos.java).

export type Role = 'REVIEWER' | 'OPERATIONS_LEAD' | 'READ_ONLY';

export type ApplicationStatus =
  | 'SUBMITTED'
  | 'IN_REVIEW'
  | 'NEEDS_MORE_INFO'
  | 'APPROVED'
  | 'REJECTED'
  | 'LIVE'
  | 'SUSPENDED';

export type CheckResult = 'PENDING' | 'PASS' | 'FAIL';
export type QueueLevel = 'NONE' | 'OK' | 'WARN' | 'CRITICAL';
export type DocumentType = 'PHARMACY_LICENSE' | 'BUSINESS_REGISTRATION' | 'OWNER_ID';

export interface User {
  id: string;
  email: string;
  fullName: string;
  role: Role;
}

export interface LoginResponse {
  token: string;
  expiresAt: string;
  user: User;
}

export interface ApplicationSummary {
  id: string;
  pharmacyId: string;
  businessName: string;
  ownerName: string;
  city: string;
  state: string;
  status: ApplicationStatus;
  assignedReviewer: string | null;
  submittedAt: string;
  statusChangedAt: string;
  hoursInQueue: number | null;
  queueLevel: QueueLevel;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Pharmacy {
  id: string;
  businessName: string;
  ownerName: string;
  ownerEmail: string;
  phone: string | null;
  addressLine: string;
  city: string;
  state: string;
  postalCode: string;
  licenseNumber: string;
  licenseState: string;
  licenseHolderName: string;
  licenseExpiration: string;
  deliveryRadiusMiles: number | null;
  storefrontName: string;
  storefrontSlug: string;
}

export interface DocumentItem {
  id: string;
  docType: DocumentType;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  uploadedAt: string;
}

export interface ChecklistItem {
  key: string;
  label: string;
  required: boolean;
  automatic: boolean;
  result: CheckResult;
  detail: string | null;
  checkedBy: string | null;
  checkedAt: string | null;
}

export interface HistoryEntry {
  id: number;
  from: ApplicationStatus | null;
  to: ApplicationStatus;
  actor: string | null;
  reason: string | null;
  at: string;
}

export interface ApplicationDetail {
  id: string;
  status: ApplicationStatus;
  pharmacy: Pharmacy;
  assignedReviewer: string | null;
  submittedAt: string;
  statusChangedAt: string;
  hoursInQueue: number | null;
  queueLevel: QueueLevel;
  documents: DocumentItem[];
  checklist: ChecklistItem[];
  history: HistoryEntry[];
  availableTransitions: ApplicationStatus[];
  allRequiredChecksPass: boolean;
}

export interface DailyOrders {
  day: string;
  orders: number;
  totalCents: number;
}

export interface AttentionItem {
  applicationId: string;
  pharmacyName: string;
  status: ApplicationStatus;
  code: string;
  message: string;
  severity: 'WARNING' | 'CRITICAL';
}

export interface Overview {
  totalPharmacies: number;
  pharmaciesByStatus: Record<ApplicationStatus, number>;
  ordersPerDay: DailyOrders[];
  needsAttention: AttentionItem[];
}

export interface CreateApplicationRequest {
  businessName: string;
  ownerName: string;
  ownerEmail: string;
  phone: string;
  addressLine: string;
  city: string;
  state: string;
  postalCode: string;
  licenseNumber: string;
  licenseState: string;
  licenseHolderName: string;
  licenseExpiration: string;
  deliveryRadiusMiles: number | null;
  storefrontName: string;
  storefrontSlug: string;
}
