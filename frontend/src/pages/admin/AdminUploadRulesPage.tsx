import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AdminLayout } from './AdminLayout';
import { adminApi } from '../../api/admin';
import { errorMessage } from '../../api/client';

const MIME_LABELS: Record<string, string> = {
  'application/pdf': 'PDF',
  'image/jpeg': 'JPEG image',
  'image/png': 'PNG image',
};

export function AdminUploadRulesPage() {
  const queryClient = useQueryClient();
  const { data, isLoading } = useQuery({
    queryKey: ['admin', 'upload-rules'],
    queryFn: () => adminApi.uploadRules(),
  });

  const [allowed, setAllowed] = useState<string[]>([]);
  const [maxMb, setMaxMb] = useState('10');
  const [maxPages, setMaxPages] = useState('15');

  useEffect(() => {
    if (data) {
      setAllowed(data.allowedMimeTypes);
      setMaxMb((data.maxFileSizeBytes / (1024 * 1024)).toString());
      setMaxPages(data.maxPdfPages.toString());
    }
  }, [data]);

  const save = useMutation({
    mutationFn: () =>
      adminApi.updateUploadRules({
        allowedMimeTypes: allowed,
        maxFileSizeBytes: Math.round(Number(maxMb) * 1024 * 1024),
        maxPdfPages: Number(maxPages),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'upload-rules'] }),
  });

  const toggle = (mime: string) =>
    setAllowed((prev) => (prev.includes(mime) ? prev.filter((m) => m !== mime) : [...prev, mime]));

  return (
    <AdminLayout title="Upload rules">
      {isLoading || !data ? (
        <p className="text-slate-500">Loading…</p>
      ) : (
        <div className="max-w-xl space-y-6 rounded-2xl border border-slate-200 bg-white p-6">
          <div>
            <h2 className="text-sm font-semibold text-slate-800">Accepted file types</h2>
            <p className="mt-0.5 text-xs text-slate-500">
              Only types the pipeline can process are selectable.
            </p>
            <div className="mt-3 space-y-2">
              {data.supportedMimeTypes.map((mime) => (
                <label key={mime} className="flex items-center gap-2 text-sm text-slate-700">
                  <input
                    type="checkbox"
                    checked={allowed.includes(mime)}
                    onChange={() => toggle(mime)}
                    className="h-4 w-4 rounded border-slate-300"
                  />
                  {MIME_LABELS[mime] ?? mime} <span className="text-xs text-slate-400">({mime})</span>
                </label>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <label className="block text-sm">
              <span className="text-slate-700">Max file size (MB)</span>
              <input
                type="number"
                min={1}
                value={maxMb}
                onChange={(e) => setMaxMb(e.target.value)}
                className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              />
            </label>
            <label className="block text-sm">
              <span className="text-slate-700">Max PDF pages</span>
              <input
                type="number"
                min={1}
                value={maxPages}
                onChange={(e) => setMaxPages(e.target.value)}
                className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              />
            </label>
          </div>

          {save.isError && <p className="text-sm text-red-600">{errorMessage(save.error)}</p>}
          {save.isSuccess && <p className="text-sm text-green-600">Saved.</p>}

          <button
            onClick={() => save.mutate()}
            disabled={save.isPending || allowed.length === 0}
            className="rounded-lg bg-brand px-4 py-2 text-sm font-medium text-white hover:bg-brand-dark disabled:opacity-60"
          >
            {save.isPending ? 'Saving…' : 'Save changes'}
          </button>
        </div>
      )}
    </AdminLayout>
  );
}
