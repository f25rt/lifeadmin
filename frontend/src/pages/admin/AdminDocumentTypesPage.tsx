import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AdminLayout } from './AdminLayout';
import { adminApi } from '../../api/admin';
import { errorMessage } from '../../api/client';
import type { DocumentTypeView } from '../../api/types';

export function AdminDocumentTypesPage() {
  const { data, isLoading } = useQuery({
    queryKey: ['admin', 'document-types'],
    queryFn: () => adminApi.documentTypes(),
  });

  return (
    <AdminLayout title="Document types & AI templates">
      <p className="mb-4 max-w-3xl text-sm text-slate-600">
        Enable or disable types, rename their labels, and tune how the AI classifies and extracts. The
        classifier matches <span className="font-medium">keywords</span> (comma-separated) in the
        document text; <span className="font-medium">relevant date types</span> tell it which date to
        treat as primary; <span className="font-medium">default offsets</span> seed reminder
        suggestions (days before the date).
      </p>

      {isLoading || !data ? (
        <p className="text-slate-500">Loading…</p>
      ) : (
        <div className="space-y-3">
          {data.map((t) => (
            <TypeRow key={t.typeCode} type={t} />
          ))}
        </div>
      )}
    </AdminLayout>
  );
}

function TypeRow({ type }: { type: DocumentTypeView }) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [label, setLabel] = useState(type.label);
  const [enabled, setEnabled] = useState(type.enabled);
  const [keywords, setKeywords] = useState(type.keywords);
  const [relevantDateTypes, setRelevantDateTypes] = useState(type.relevantDateTypes);
  const [defaultOffsetsDays, setDefaultOffsetsDays] = useState(type.defaultOffsetsDays);

  const save = useMutation({
    mutationFn: () =>
      adminApi.updateDocumentType(type.typeCode, {
        label,
        enabled,
        keywords,
        relevantDateTypes,
        defaultOffsetsDays,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'document-types'] }),
  });

  return (
    <div className="rounded-2xl border border-slate-200 bg-white">
      <div className="flex flex-wrap items-center justify-between gap-3 p-4">
        <div className="flex items-center gap-3">
          <span className="font-mono text-xs text-slate-400">{type.typeCode}</span>
          <span className="font-medium text-slate-900">{type.label}</span>
          <span
            className={`rounded-full px-2 py-0.5 text-xs font-medium ${
              type.enabled ? 'bg-green-50 text-green-700' : 'bg-slate-100 text-slate-500'
            }`}
          >
            {type.enabled ? 'enabled' : 'disabled'}
          </span>
        </div>
        <button
          onClick={() => setOpen((v) => !v)}
          className="rounded-md border border-slate-300 px-3 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100"
        >
          {open ? 'Close' : 'Edit'}
        </button>
      </div>

      {open && (
        <div className="space-y-4 border-t border-slate-100 p-4">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <label className="block text-sm">
              <span className="text-slate-700">Label</span>
              <input
                value={label}
                onChange={(e) => setLabel(e.target.value)}
                className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              />
            </label>
            <label className="flex items-center gap-2 pt-6 text-sm text-slate-700">
              <input
                type="checkbox"
                checked={enabled}
                onChange={(e) => setEnabled(e.target.checked)}
                className="h-4 w-4 rounded border-slate-300"
              />
              Enabled (available for classification &amp; upload)
            </label>
          </div>

          <label className="block text-sm">
            <span className="text-slate-700">Keywords (comma-separated)</span>
            <input
              value={keywords}
              onChange={(e) => setKeywords(e.target.value)}
              placeholder="e.g. insurance,policy,coverage"
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            />
          </label>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <label className="block text-sm">
              <span className="text-slate-700">Relevant date types</span>
              <input
                value={relevantDateTypes}
                onChange={(e) => setRelevantDateTypes(e.target.value)}
                placeholder="e.g. EXPIRATION,RENEWAL"
                className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              />
            </label>
            <label className="block text-sm">
              <span className="text-slate-700">Default reminder offsets (days)</span>
              <input
                value={defaultOffsetsDays}
                onChange={(e) => setDefaultOffsetsDays(e.target.value)}
                placeholder="e.g. 30,60"
                className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              />
            </label>
          </div>

          {save.isError && <p className="text-sm text-red-600">{errorMessage(save.error)}</p>}
          {save.isSuccess && <p className="text-sm text-green-600">Saved.</p>}

          <button
            onClick={() => save.mutate()}
            disabled={save.isPending}
            className="rounded-lg bg-brand px-4 py-2 text-sm font-medium text-white hover:bg-brand-dark disabled:opacity-60"
          >
            {save.isPending ? 'Saving…' : 'Save'}
          </button>
        </div>
      )}
    </div>
  );
}
