import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AppHeader } from '../components/AppHeader';
import { StatusBadge, formatBytes } from '../components/StatusBadge';
import { documentsApi } from '../api/documents';
import { errorMessage } from '../api/client';
import { ReminderForm } from '../components/ReminderForm';

export function DocumentDetailPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { data: doc, isLoading, error } = useQuery({
    queryKey: ['documents', id],
    queryFn: () => documentsApi.get(id),
    enabled: !!id,
    // Poll while the document is still being processed so the UI updates automatically.
    refetchInterval: (query) => {
      const s = query.state.data?.status;
      return s === 'UPLOADED' || s === 'PROCESSING' ? 2000 : false;
    },
  });

  const archive = useMutation({
    mutationFn: () => documentsApi.update(id, { archive: true }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['documents'] }),
  });

  const remove = useMutation({
    mutationFn: () => documentsApi.remove(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] });
      navigate('/documents');
    },
  });

  const reprocess = useMutation({
    mutationFn: () => documentsApi.reprocess(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['documents', id] }),
  });

  const openDownload = async () => {
    const { url } = await documentsApi.downloadUrl(id);
    window.open(url, '_blank', 'noopener');
  };

  return (
    <div className="min-h-screen bg-slate-50">
      <AppHeader />
      <main className="mx-auto max-w-3xl px-4 py-8 sm:px-6 sm:py-10">
        <button onClick={() => navigate('/documents')} className="text-sm text-slate-500 hover:text-slate-700">
          ← Back to documents
        </button>

        {error && <div className="mt-4 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">{errorMessage(error)}</div>}
        {isLoading || !doc ? (
          <p className="mt-8 text-slate-500">Loading…</p>
        ) : (
          <>
            <div className="mt-4 flex flex-wrap items-start justify-between gap-3">
              <h1 className="text-2xl font-semibold text-slate-900">{doc.title}</h1>
              <StatusBadge status={doc.status} />
            </div>

            {(doc.status === 'UPLOADED' || doc.status === 'PROCESSING') && (
              <div className="mt-4 flex items-center gap-3 rounded-xl bg-amber-50 px-4 py-3 text-amber-800">
                <span className="h-4 w-4 animate-spin rounded-full border-2 border-amber-300 border-t-amber-700" />
                Understanding your document… this updates automatically.
              </div>
            )}
            {doc.status === 'REVIEW_REQUIRED' && (
              <div className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-xl bg-blue-50 px-4 py-3 text-blue-800">
                <span>We found details in this document. Please review and confirm.</span>
                <button
                  onClick={() => navigate(`/documents/${id}/review`)}
                  className="rounded-lg bg-brand px-4 py-2 text-sm font-medium text-white hover:bg-brand-dark"
                >
                  Review details
                </button>
              </div>
            )}
            {doc.status === 'FAILED' && (
              <div className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-xl bg-red-50 px-4 py-3 text-red-800">
                <span>{doc.failureReason ?? 'Processing failed.'}</span>
                <button
                  onClick={() => reprocess.mutate()}
                  disabled={reprocess.isPending}
                  className="rounded-lg border border-red-300 px-4 py-2 text-sm font-medium text-red-700 hover:bg-red-100 disabled:opacity-60"
                >
                  Try again
                </button>
              </div>
            )}

            {doc.dates.length > 0 && (
              <div className="mt-6 rounded-2xl border border-slate-200 bg-white p-5">
                <h2 className="text-sm font-semibold text-slate-700">Important dates</h2>
                <ul className="mt-3 space-y-3">
                  {doc.dates.map((d) => (
                    <li key={d.id} className="flex flex-col gap-2 border-b border-slate-100 pb-3 last:border-0 last:pb-0">
                      <div className="flex items-center justify-between text-sm">
                        <span className="text-slate-600">{d.dateType.replace(/_/g, ' ')}</span>
                        <span className="flex items-center gap-2 font-medium text-slate-900">
                          {d.dateValue}
                          {d.derived && (
                            <span className="rounded-full bg-purple-50 px-2 py-0.5 text-xs text-purple-700">derived</span>
                          )}
                        </span>
                      </div>
                      <ReminderForm documentId={doc.id} date={d} />
                    </li>
                  ))}
                </ul>
              </div>
            )}

            <div className="mt-6 rounded-2xl border border-slate-200 bg-white p-5">
              <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <Row label="File name" value={doc.fileName} />
                <Row label="Type" value={doc.documentType ? doc.documentType.replace('_', ' ') : 'Unclassified'} />
                <Row label="Format" value={doc.mimeType} />
                <Row label="Size" value={formatBytes(doc.fileSize)} />
                <Row label="Uploaded" value={new Date(doc.createdAt).toLocaleString()} />
                {doc.failureReason && <Row label="Failure" value={doc.failureReason} />}
              </dl>
            </div>

            <div className="mt-6 flex flex-wrap gap-3">
              <button
                onClick={openDownload}
                className="rounded-lg bg-brand px-4 py-2 text-sm font-medium text-white hover:bg-brand-dark"
              >
                Download
              </button>
              {doc.status !== 'ARCHIVED' && (
                <button
                  onClick={() => archive.mutate()}
                  disabled={archive.isPending}
                  className="rounded-lg border border-slate-300 px-4 py-2 text-sm text-slate-700 hover:bg-slate-100 disabled:opacity-60"
                >
                  Archive
                </button>
              )}
              <button
                onClick={() => {
                  if (window.confirm('Permanently delete this document? This cannot be undone.')) {
                    remove.mutate();
                  }
                }}
                disabled={remove.isPending}
                className="rounded-lg border border-red-300 px-4 py-2 text-sm text-red-700 hover:bg-red-50 disabled:opacity-60"
              >
                Delete
              </button>
            </div>
          </>
        )}
      </main>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-slate-400">{label}</dt>
      <dd className="mt-0.5 break-words text-slate-800">{value}</dd>
    </div>
  );
}
