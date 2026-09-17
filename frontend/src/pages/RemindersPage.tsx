import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AppHeader } from '../components/AppHeader';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { remindersApi } from '../api/reminders';
import { errorMessage } from '../api/client';
import type { ReminderResponse } from '../api/types';

const STATUS_STYLES: Record<string, string> = {
  SCHEDULED: 'bg-blue-50 text-blue-700',
  SENT: 'bg-green-50 text-green-700',
  CANCELLED: 'bg-slate-100 text-slate-500',
  FAILED: 'bg-red-50 text-red-700',
};

/** A confirmable action on a specific reminder. */
type PendingAction = { kind: 'cancel' | 'delete'; reminder: ReminderResponse } | null;

export function RemindersPage() {
  const queryClient = useQueryClient();
  const [pending, setPending] = useState<PendingAction>(null);

  const { data, isLoading, error } = useQuery({
    queryKey: ['reminders', 'all'],
    queryFn: () => remindersApi.list(false),
  });

  const cancel = useMutation({
    mutationFn: (id: string) => remindersApi.update(id, { cancel: true }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reminders'] });
      setPending(null);
    },
  });

  const remove = useMutation({
    mutationFn: (id: string) => remindersApi.remove(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reminders'] });
      setPending(null);
    },
  });

  const reminders = data ?? [];
  const scheduled = reminders.filter((r) => r.status === 'SCHEDULED');
  const others = reminders.filter((r) => r.status !== 'SCHEDULED');

  const busy = cancel.isPending || remove.isPending;
  const confirmAction = () => {
    if (!pending) return;
    if (pending.kind === 'cancel') cancel.mutate(pending.reminder.id);
    else remove.mutate(pending.reminder.id);
  };

  return (
    <div className="min-h-screen bg-slate-50">
      <AppHeader />
      <main className="mx-auto max-w-3xl px-4 py-8 sm:px-6 sm:py-10">
        <h1 className="text-2xl font-semibold text-slate-900">Reminders</h1>
        <p className="mt-1 text-slate-600">Upcoming and past reminders across your documents.</p>

        {error && (
          <div className="mt-4 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">{errorMessage(error)}</div>
        )}

        {isLoading ? (
          <p className="mt-8 text-slate-500">Loading…</p>
        ) : reminders.length === 0 ? (
          <div className="mt-8 rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center">
            <p className="text-slate-500">No reminders yet. Open a document to set one on an important date.</p>
            <Link
              to="/documents"
              className="mt-4 inline-block rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
            >
              View documents
            </Link>
          </div>
        ) : (
          <div className="mt-8 space-y-8">
            <Section
              title="Upcoming"
              items={scheduled}
              onRequestCancel={(r) => setPending({ kind: 'cancel', reminder: r })}
              onRequestDelete={(r) => setPending({ kind: 'delete', reminder: r })}
            />
            {others.length > 0 && (
              <Section
                title="Past & cancelled"
                items={others}
                onRequestDelete={(r) => setPending({ kind: 'delete', reminder: r })}
              />
            )}
          </div>
        )}
      </main>

      <ConfirmDialog
        open={pending !== null}
        title={pending?.kind === 'cancel' ? 'Cancel reminder' : 'Delete reminder'}
        message={
          pending?.kind === 'cancel'
            ? `Stop the reminder "${pending?.reminder.title}"? It won't be sent, but stays in your history.`
            : `Delete the reminder "${pending?.reminder.title}"? This cannot be undone.`
        }
        confirmLabel={pending?.kind === 'cancel' ? 'Cancel reminder' : 'Delete'}
        cancelLabel="Keep"
        destructive={pending?.kind === 'delete'}
        busy={busy}
        onConfirm={confirmAction}
        onCancel={() => setPending(null)}
      />
    </div>
  );
}

function Section({
  title,
  items,
  onRequestCancel,
  onRequestDelete,
}: {
  title: string;
  items: ReminderResponse[];
  onRequestCancel?: (r: ReminderResponse) => void;
  onRequestDelete: (r: ReminderResponse) => void;
}) {
  if (items.length === 0) return null;
  return (
    <section>
      <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-400">{title}</h2>
      <ul className="mt-3 space-y-2">
        {items.map((r) => (
          <li
            key={r.id}
            className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-200 bg-white p-4"
          >
            <div className="min-w-0">
              <Link to={`/documents/${r.documentId}`} className="font-medium text-slate-900 hover:text-brand">
                {r.title}
              </Link>
              <p className="mt-0.5 text-sm text-slate-500">
                {r.reminderLocalDate} · {r.channel === 'EMAIL' ? 'Email' : 'In-app'}
              </p>
            </div>
            <div className="flex items-center gap-2">
              <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${STATUS_STYLES[r.status] ?? 'bg-slate-100 text-slate-600'}`}>
                {r.status.toLowerCase()}
              </span>
              {onRequestCancel && r.status === 'SCHEDULED' && (
                <button
                  onClick={() => onRequestCancel(r)}
                  className="rounded-md border border-slate-300 px-2.5 py-1 text-xs text-slate-700 hover:bg-slate-100"
                >
                  Cancel
                </button>
              )}
              <button
                onClick={() => onRequestDelete(r)}
                className="rounded-md border border-red-300 px-2.5 py-1 text-xs text-red-700 hover:bg-red-50"
              >
                Delete
              </button>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}
