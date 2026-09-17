import { api } from './client';
import type { NotificationResponse, UnreadCountResponse } from './types';

export const notificationsApi = {
  list: (unread = false) =>
    api.get<NotificationResponse[]>('/notifications', { params: { unread } }).then((r) => r.data),

  unreadCount: () =>
    api.get<UnreadCountResponse>('/notifications/unread-count').then((r) => r.data),

  markRead: (id: string) =>
    api.post<NotificationResponse>(`/notifications/${id}/read`).then((r) => r.data),

  markAllRead: () =>
    api.post<{ updated: number }>('/notifications/read-all').then((r) => r.data),
};
