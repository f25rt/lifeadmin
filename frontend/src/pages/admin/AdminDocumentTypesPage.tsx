import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AdminLayout } from './AdminLayout';
import { adminApi } from '../../api/admin';
import { errorMessage } from '../../api/client';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { DOCUMENT_TYPES, type DocumentTypeView } from '../../api/types';

/** Built-in type codes (the original enum) — these can't be deleted. */
const BUILT_IN = new Set<string>(DOCUMENT_TYPES);

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
        suggestions (days before the date). You can also add your own types below.
      </p>

      <AddTypeForm />

      {isLoading || !data ? (
        <p className="text-slate-500">Loading…</p>
      ) : (
        <div className="space-y-3">
          {data.map((t) => (
            <TypeRow key={t.typeCode} type={t} deletable={!BUILT_IN.has(t.typeCode)} />
          ))}
        </div>
      )}
    </AdminLayout>
  );
}

function AddTypeForm() {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [typeCode, setTypeCode] = useState('');
  const [label, setLabel] = useState('');
  const [keywords, setKeywords] = useState('');
  const [relevantDateTypes, setRelevantDateTypes] = useState('');
  const [defaultOffsetsDays, setDefaultOffsetsDays] = useState('30');

  const create = useMutation({
    mutationFn: () =>
      adminApi.createDocumentType({
        typeCode,
        label,
        keywords: keywords || undefined,
        relevantDateTypes: relevantDateTypes || undefined,
        defaultOffsetsDays: defaultOffsetsDays || undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'document-types'] });
      setTypeCode('');
      setLabel('');
      setKeywords('');
      setRelevantDateTypes('');
      setDefaultOffsetsDays('30');
      setOpen(false);
    },
  });

  if (!open) {
    return (
      <button
        onClick={() => setOpen(true)}
        className="mb-4 rounded-lg bg-brand px-4 py-2 text-sm font-medium text-white hover:bg-brand-dark"
      >
        + Add document type
      </button>
    );
  }

  return (
    <div className="mb-6 space-y-4 rounded-2xl border border-brand/30 bg-white p-4">
      <h2 className="text-sm font-semibold text-slate-800">New document type</h2>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <label className="block text-sm">
          <span className="text-slate-700">Code</span>
          <input
            value={typeCode}
            onChange={(e) => setTypeCode(e.target.value.toUpperCase())}
            placeholder="e.g. MEDICAL_RECORD"
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 font-mono text-sm"
          />
          <span className="mt-1 block text-xs text-slate-400">
            Letters, numbers, underscores; starts with a letter.
          </span>
        </label>
        <label className="block text-sm">
          <span className="text-slate-700">Label</span>
          <input
            value={label}
            onChange={(e) => setLabel(e.target.value)}
            placeholder="e.g. Medical Record"
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
          />
        </label>
      </div>
      <label className="block text-sm">
        <span className="text-slate-700">Keywords (comma-separated)</span>
        <input
          value={keywords}
          onChange={(e) => setKeywords(e.target.value)}
          placeholder="e.g. medical,clinic,prescription"
          className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
        />
      </label>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <label className="block text-sm">
          <span className="text-slate-700">Relevant date types</span>
          <input
            value={relevantDateTypes}
            onChange={(e) => setRelevantDateTypes(e.target.value)}
            placeholder="e.g. EXPIRATION,APPOINTMENT"
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

      {create.isError && <p className="text-sm text-red-600">{errorMessage(create.error)}</p>}

      <div className="flex gap-2">
        <button
          onClick={() => create.mutate()}
          disabled={create.isPending || !typeCode.trim() || !label.trim()}
          className="rounded-lg bg-brand px-4 py-2 text-sm font-medium text-white hover:bg-brand-dark disabled:opacity-60"
        >
          {create.isPending ? 'Creating…' : 'Create type'}
        </button>
        <button
          onClick={() => setOpen(false)}
          className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100"
        >
          Cancel
        </button>
      </div>
    </div>
  );
}

function TypeRow({ type, deletable }: { type: DocumentTypeView; deletable: boolean }) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
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

  const remove = useMutation({
    mutationFn: () => adminApi.deleteDocumentType(type.typeCode),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'document-types'] });
      setConfirmDelete(false);
    },
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
        <div className="flex items-center gap-2">
          <button
            onClick={() => setOpen((v) => !v)}
            className="rounded-md border border-slate-300 px-3 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100"
          >
            {open ? 'Close' : 'Edit'}
          </button>
          {deletable && (
            <button
              onClick={() => setConfirmDelete(true)}
              className="rounded-md border border-red-300 px-3 py-1 text-xs font-medium text-red-700 hover:bg-red-50"
            >
              Delete
            </button>
          )}
        </div>
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

      <ConfirmDialog
        open={confirmDelete}
        title="Delete document type?"
        message={`Delete "${type.label}" (${type.typeCode})? Any documents using it will be reassigned to OTHER. This cannot be undone.`}
        confirmLabel="Delete type"
        destructive
        busy={remove.isPending}
        onConfirm={() => remove.mutate()}
        onCancel={() => {
          if (!remove.isPending) setConfirmDelete(false);
        }}
      />
      {remove.isError && (
        <p className="px-4 pb-3 text-sm text-red-600">{errorMessage(remove.error)}</p>
      )}
    </div>
  );
}
