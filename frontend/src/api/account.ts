import { api } from './client';
import type { UsageView } from './types';

export const accountApi = {
  usage: () => api.get<UsageView>('/account/usage').then((r) => r.data),

  /** Fetch the full account export as a JSON blob (for download). */
  exportData: () =>
    api.get('/account/export', { responseType: 'blob' }).then((r) => r.data as Blob),

  /** Permanently delete the caller's account. Requires the caller's email as confirmation. */
  deleteAccount: (confirmEmail: string) =>
    api.delete('/account', { data: { confirmEmail } }).then(() => undefined),
};
