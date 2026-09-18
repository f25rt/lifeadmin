import { api } from './client';
import type {
  AdminDocumentRow,
  AdminOverview,
  AdminUserRow,
  AnalyticsView,
  DocumentTypeView,
  Page,
  UploadRulesView,
} from './types';

export const adminApi = {
  overview: () => api.get<AdminOverview>('/admin/overview').then((r) => r.data),

  analytics: () => api.get<AnalyticsView>('/admin/analytics').then((r) => r.data),

  users: () =>
    api.get<{ users: AdminUserRow[] }>('/admin/users').then((r) => r.data.users),

  updateUserRole: (userId: string, role: string) =>
    api
      .patch<AdminUserRow>(`/admin/users/${userId}/role`, { role })
      .then((r) => r.data),

  updateUserStatus: (userId: string, disabled: boolean) =>
    api
      .patch<AdminUserRow>(`/admin/users/${userId}/status`, { disabled })
      .then((r) => r.data),

  documents: (page = 0, size = 25, accountId?: string) =>
    api
      .get<Page<AdminDocumentRow>>('/admin/documents', {
        params: { page, size, ...(accountId ? { accountId } : {}) },
      })
      .then((r) => r.data),

  uploadRules: () =>
    api.get<UploadRulesView>('/admin/settings/upload').then((r) => r.data),

  updateUploadRules: (body: {
    allowedMimeTypes?: string[];
    maxFileSizeBytes?: number;
    maxPdfPages?: number;
  }) => api.put<UploadRulesView>('/admin/settings/upload', body).then((r) => r.data),

  documentTypes: () =>
    api.get<{ types: DocumentTypeView[] }>('/admin/document-types').then((r) => r.data.types),

  updateDocumentType: (
    typeCode: string,
    body: {
      label?: string;
      enabled?: boolean;
      keywords?: string;
      relevantDateTypes?: string;
      defaultOffsetsDays?: string;
    },
  ) => api.patch<DocumentTypeView>(`/admin/document-types/${typeCode}`, body).then((r) => r.data),

  createDocumentType: (body: {
    typeCode: string;
    label: string;
    keywords?: string;
    relevantDateTypes?: string;
    defaultOffsetsDays?: string;
    sortOrder?: number;
  }) => api.post<DocumentTypeView>('/admin/document-types', body).then((r) => r.data),

  deleteDocumentType: (typeCode: string) =>
    api.delete(`/admin/document-types/${typeCode}`).then(() => undefined),
};
