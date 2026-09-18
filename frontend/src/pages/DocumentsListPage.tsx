import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { AppHeader } from '../components/AppHeader';
import { StatusBadge, formatBytes } from '../components/StatusBadge';
import { documentsApi } from '../api/documents';
import { errorMessage } from '../api/client';

export function DocumentsListPage() {
  const [term, setTerm] = useState('');
  const [debounced, setDebounced] = useState('');

  // Debounce the query so we don't fire a request on every keystroke.
  useEffect(() => {
    const t = setTimeout(() => setDebounced(term.trim()), 300);
    return () => clearTimeout(t);
  }, [term]);

  const { data, isLoading, error } = useQuery({
    queryKey: ['documents', 'list', debounced],
    queryFn: () => documentsApi.list(0, 50, undefined, debounced || undefined),
  });

  return (
    <div className="min-h-screen bg-slate-50">
      <AppHeader />
      <main className="mx-auto max-w-5xl px-4 py-8 sm:px-6 sm:py-10">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h1 className="text-2xl font-semibold text-slate-900">Documents</h1>
          <Link
            to="/documents/upload"
            className="rounded-lg bg-brand px-4 py-2 text-sm font-medium text-white hover:bg-brand-dark"
          >
            + Add document
          </Link>
        </div>

        <div className="mt-6">
          <label htmlFor="doc-search" className="sr-only">
            Search documents
          </label>
          <input
            id="doc-search"
            type="search"
            value={term}
            onChange={(e) => setTerm(e.target.value)}
            placeholder="Search by title, type, or extracted details…"
            className="w-full rounded-lg border border-slate-300 px-4 py-2 text-sm focus:border-brand focus:outline-none focus:ring-1 focus:ring-brand"
          />
        </div>

        {error && <div className="mt-4 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">{errorMessage(error)}</div>}

        {isLoading ? (
          <p className="mt-8 text-slate-500">Loading…</p>
        ) : !data || data.content.length === 0 ? (
          debounced ? (
            <div className="mt-8 rounded-2xl border border-dashed border-slate-300 bg-white p-10 text-center">
              <p className="text-slate-500">
                No documents match “{debounced}”.
              </p>
            </div>
          ) : (
            <div className="mt-8 rounded-2xl border border-dashed border-slate-300 bg-white p-10 text-center">
              <p className="text-slate-500">No documents yet.</p>
              <Link
                to="/documents/upload"
                className="mt-4 inline-block rounded-lg bg-brand px-4 py-2 text-sm font-medium text-white hover:bg-brand-dark"
              >
                Upload your first document
              </Link>
            </div>
          )
        ) : (
          <ul className="mt-6 grid grid-cols-1 gap-3 sm:grid-cols-2">
            {data.content.map((doc) => (
              <li key={doc.id}>
                <Link
                  to={`/documents/${doc.id}`}
                  className="block rounded-2xl border border-slate-200 bg-white p-4 hover:border-brand hover:shadow-sm"
                >
                  <div className="flex items-start justify-between gap-2">
                    <span className="truncate font-medium text-slate-900">{doc.title}</span>
                    <StatusBadge status={doc.status} />
                  </div>
                  <div className="mt-2 truncate text-sm text-slate-500">{doc.fileName}</div>
                  <div className="mt-1 text-xs text-slate-400">
                    {doc.documentType ? doc.documentType.replace('_', ' ') : 'Unclassified'} ·{' '}
                    {formatBytes(doc.fileSize)}
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </main>
    </div>
  );
}
