import { api } from './client';
import type {
  CreateReminderRequest,
  CreateReminderResponse,
  ReminderResponse,
} from './types';

export const remindersApi = {
  list: (upcoming = false) =>
    api.get<ReminderResponse[]>('/reminders', { params: { upcoming } }).then((r) => r.data),

  create: (body: CreateReminderRequest) =>
    api.post<CreateReminderResponse>('/reminders', body).then((r) => r.data),

  update: (id: string, body: { reminderDate?: string; channel?: string; cancel?: boolean }) =>
    api.patch<ReminderResponse>(`/reminders/${id}`, body).then((r) => r.data),

  remove: (id: string) => api.delete(`/reminders/${id}`).then(() => undefined),
};
