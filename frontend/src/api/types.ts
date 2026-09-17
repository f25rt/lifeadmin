// Types mirroring the backend auth contract (API_SPEC §1).

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
  timezone?: string;
  country?: string;
}

export interface RegisterResponse {
  userId: string;
  accountId: string;
  email: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  userId: string;
  accountId: string;
  name: string | null;
}

export interface CurrentUser {
  userId: string;
  accountId: string;
  name: string;
  email: string;
  timezone: string;
  country: string;
  role: string;
  plan: string;
}

export interface ApiErrorBody {
  timestamp?: string;
  status?: number;
  code?: string;
  message?: string;
  path?: string;
  correlationId?: string;
}

// --- Documents (Phase 2) ---
export type DocumentStatus =
  | 'UPLOADED'
  | 'PROCESSING'
  | 'REVIEW_REQUIRED'
  | 'ACTIVE'
  | 'ARCHIVED'
  | 'FAILED';

export type DocumentType =
  | 'PASSPORT'
  | 'DRIVERS_LICENSE'
  | 'VEHICLE_REGISTRATION'
  | 'INSURANCE'
  | 'WARRANTY'
  | 'RECEIPT'
  | 'CONTRACT'
  | 'PROPERTY_DOCUMENT'
  | 'GOVERNMENT_DOCUMENT'
  | 'LICENSE'
  | 'CERTIFICATE'
  | 'BILL'
  | 'SUBSCRIPTION'
  | 'OTHER';

export interface DocumentSummary {
  id: string;
  title: string;
  fileName: string;
  documentType: DocumentType | null;
  status: DocumentStatus;
  fileSize: number;
  createdAt: string;
}

export interface ArtifactView {
  kind: string;
  mimeType: string;
  fileSize: number;
}

export interface FieldView {
  id: string;
  fieldName: string;
  fieldValue: string | null;
  rawValue: string | null;
  confidence: number | null;
  source: string;
  verified: boolean;
}

export interface DateView {
  id: string;
  dateType: string;
  dateValue: string;
  confidence: number | null;
  source: string;
  derived: boolean;
}

export interface ActionView {
  id: string;
  label: string;
  done: boolean;
}

export interface DocumentDetail {
  id: string;
  title: string;
  fileName: string;
  mimeType: string;
  fileSize: number;
  documentType: DocumentType | null;
  classificationConfidence: number | null;
  status: DocumentStatus;
  failureReason: string | null;
  personId: string | null;
  artifacts: ArtifactView[];
  fields: FieldView[];
  dates: DateView[];
  suggestedActions: ActionView[];
  createdAt: string;
}

export interface VerifyRequest {
  documentType?: DocumentType;
  fields?: { fieldName: string; fieldValue: string }[];
  dates?: { dateType: string; dateValue: string }[];
}

export const DOCUMENT_TYPES: DocumentType[] = [
  'PASSPORT', 'DRIVERS_LICENSE', 'VEHICLE_REGISTRATION', 'INSURANCE', 'WARRANTY',
  'RECEIPT', 'CONTRACT', 'PROPERTY_DOCUMENT', 'GOVERNMENT_DOCUMENT', 'LICENSE',
  'CERTIFICATE', 'BILL', 'SUBSCRIPTION', 'OTHER',
];

export interface UploadResponse {
  id: string;
  title: string;
  status: DocumentStatus;
  fileName: string;
}

export interface DownloadUrlResponse {
  url: string;
  expiresInSeconds: number;
}

/** Spring Data Page envelope. */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// --- Reminders & notifications (Phase 4) ---
export type ReminderChannel = 'IN_APP' | 'EMAIL';
export type ReminderStatus = 'SCHEDULED' | 'SENT' | 'CANCELLED' | 'FAILED';

export interface ReminderResponse {
  id: string;
  documentId: string;
  title: string;
  reminderLocalDate: string;
  scheduledForUtc: string;
  channel: ReminderChannel;
  status: ReminderStatus;
  sentAt: string | null;
}

export type ReminderDirection = 'BEFORE' | 'AFTER';

export interface CreateReminderRequest {
  documentId: string;
  importantDateId?: string;
  offsetsDaysBefore?: number[];
  reminderDate?: string;
  channel?: ReminderChannel;
  direction?: ReminderDirection;
}

export interface CreateReminderResponse {
  created: ReminderResponse[];
}

export type NotificationType = 'REMINDER' | 'SYSTEM' | 'NEEDS_ATTENTION';

export interface NotificationResponse {
  id: string;
  type: NotificationType;
  title: string;
  body: string | null;
  read: boolean;
  referenceType: string | null;
  referenceId: string | null;
  createdAt: string;
}

export interface UnreadCountResponse {
  unread: number;
}

// --- Dashboard (Phase 4) ---
export interface DashboardCounts {
  totalDocuments: number;
  activeDocuments: number;
  needsReview: number;
  expiringSoon: number;
}

export interface UpcomingDate {
  importantDateId: string;
  documentId: string;
  documentTitle: string;
  documentType: DocumentType | null;
  dateType: string;
  dateValue: string;
  daysUntil: number;
  source: string;
  hasReminder: boolean;
}

export interface AttentionItem {
  documentId: string;
  documentTitle: string;
  reason: string;
  detail: string;
}

export interface DashboardResponse {
  counts: DashboardCounts;
  upcoming: UpcomingDate[];
  needsAttention: AttentionItem[];
}
