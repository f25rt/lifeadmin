import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { AdminLayout } from './AdminLayout';
import { adminApi } from '../../api/admin';
import { formatBytes } from '../../components/StatusBadge';

export function AdminDocumentsPage() {
  const [searchParams] = useSearchParams();
  const accountId = searchParams.get('accountId') ?? undefined;
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ['admin', 'documents', accountId, page],
    queryFn: () => adminApi.documents(page, 25, accountId),
  });

  return (
    <AdminLayout title="Documents">
      {accountId && (
        <div className="mb-4 flex items-center gap-3 rounded-lg bg-blue-50 px-4 py-2 text-sm text-blue-800">
          <span>Filtered to account {accountId}</span>
          <Link to="/admin/documents" className="font-medium underline">
            Clear filter
          </Link>
        </div>
      )}

      {isLoading || !data ? (
        <p className="text-slate-500">Loading…</p>
      ) : data.content.length === 0 ? (
        <p className="text-slate-500">No documents.</p>
      ) : (
        <>
          <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white">
            <table className="min-w-full divide-y divide-slate-100 text-sm">
              <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-3">Title</th>
                  <th className="px-4 py-3">Type</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3 text-right">Size</th>
                  <th className="px-4 py-3">Account</th>
                  <th className="px-4 py-3">Uploaded</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.content.map((d) => (
                  <tr key={d.id} className="hover:bg-slate-50">
                    <td className="px-4 py-3 font-medium text-slate-900">{d.title}</td>
                    <td className="px-4 py-3 text-slate-600">{d.documentType ?? '—'}</td>
                    <td className="px-4 py-3 text-slate-600">{d.status}</td>
                    <td className="px-4 py-3 text-right text-slate-600">{formatBytes(d.fileSize)}</td>
                    <td className="px-4 py-3 font-mono text-xs text-slate-500">{d.accountId.slice(0, 8)}…</td>
                    <td className="px-4 py-3 text-slate-500">{new Date(d.createdAt).toLocaleDateString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="mt-4 flex items-center justify-between text-sm">
            <span className="text-slate-500">
              Page {data.number + 1} of {Math.max(1, data.totalPages)} · {data.totalElements} total
            </span>
            <div className="flex gap-2">
              <button
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={data.number === 0}
                className="rounded-lg border border-slate-300 px-3 py-1.5 text-slate-700 hover:bg-slate-100 disabled:opacity-50"
              >
                Previous
              </button>
              <button
                onClick={() => setPage((p) => p + 1)}
                disabled={data.number + 1 >= data.totalPages}
                className="rounded-lg border border-slate-300 px-3 py-1.5 text-slate-700 hover:bg-slate-100 disabled:opacity-50"
              >
                Next
              </button>
            </div>
          </div>
        </>
      )}
    </AdminLayout>
  );
}
