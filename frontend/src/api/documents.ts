import { api } from './client';
import type {
  DocumentDetail,
  DocumentStatus,
  DocumentSummary,
  DownloadUrlResponse,
  Page,
  TypeOption,
  UploadResponse,
  VerifyRequest,
} from './types';

export const documentsApi = {
  list: (page = 0, size = 20, status?: DocumentStatus, q?: string) =>
    api
      .get<Page<DocumentSummary>>('/documents', {
        params: { page, size, ...(status ? { status } : {}), ...(q ? { q } : {}) },
      })
      .then((r) => r.data),

  get: (id: string) => api.get<DocumentDetail>(`/documents/${id}`).then((r) => r.data),

  upload: (file: File, onProgress?: (pct: number) => void) => {
    const form = new FormData();
    form.append('file', file);
    return api
      .post<UploadResponse>('/documents', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
        onUploadProgress: (e) => {
          if (onProgress && e.total) {
            onProgress(Math.round((e.loaded / e.total) * 100));
          }
        },
      })
      .then((r) => r.data);
  },

  update: (id: string, body: { title?: string; archive?: boolean }) =>
    api.patch<DocumentDetail>(`/documents/${id}`, body).then((r) => r.data),

  remove: (id: string) => api.delete(`/documents/${id}`).then(() => undefined),

  downloadUrl: (id: string) =>
    api.get<DownloadUrlResponse>(`/documents/${id}/download`).then((r) => r.data),

  verify: (id: string, body: VerifyRequest) =>
    api.post<DocumentDetail>(`/documents/${id}/verify`, body).then((r) => r.data),

  reprocess: (id: string) => api.post(`/documents/${id}/process`).then(() => undefined),

  /** Enabled document types (code + label) for the type picker, incl. admin-created ones. */
  types: () => api.get<TypeOption[]>('/documents/types').then((r) => r.data),
};
