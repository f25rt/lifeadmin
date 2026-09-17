import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { AppHeader } from '../components/AppHeader';
import { documentsApi } from '../api/documents';
import { errorMessage } from '../api/client';

export function UploadPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [file, setFile] = useState<File | null>(null);
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const cameraInput = useRef<HTMLInputElement>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  const upload = useMutation({
    mutationFn: (f: File) => documentsApi.upload(f, setProgress),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['documents'] });
      navigate(`/documents/${res.id}`);
    },
    onError: (e) => {
      setError(errorMessage(e));
      setProgress(0);
    },
  });

  const onPick = (f: File | null | undefined) => {
    setError(null);
    if (f) setFile(f);
  };

  return (
    <div className="min-h-screen bg-slate-50">
      <AppHeader />
      <main className="mx-auto max-w-xl px-4 py-8 sm:px-6 sm:py-10">
        <h1 className="text-2xl font-semibold text-slate-900">Add a document</h1>
        <p className="mt-1 text-slate-600">Upload a photo or PDF (JPG, PNG, PDF · max 10 MB).</p>

        {error && <div className="mt-4 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

        <div className="mt-6 rounded-2xl border border-slate-200 bg-white p-6">
          {/* Hidden inputs: one with camera capture (mobile), one general file picker. */}
          <input
            ref={cameraInput}
            type="file"
            accept="image/*"
            capture="environment"
            className="hidden"
            onChange={(e) => onPick(e.target.files?.[0])}
          />
          <input
            ref={fileInput}
            type="file"
            accept="image/jpeg,image/png,application/pdf"
            className="hidden"
            onChange={(e) => onPick(e.target.files?.[0])}
          />

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <button
              onClick={() => cameraInput.current?.click()}
              className="rounded-xl border border-slate-300 py-6 text-slate-700 hover:bg-slate-50"
            >
              📷 Take a photo
            </button>
            <button
              onClick={() => fileInput.current?.click()}
              className="rounded-xl border border-slate-300 py-6 text-slate-700 hover:bg-slate-50"
            >
              📁 Choose a file
            </button>
          </div>

          {file && (
            <div className="mt-5">
              <div className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 text-sm">
                <span className="truncate text-slate-700">{file.name}</span>
                <span className="ml-2 shrink-0 text-slate-400">{(file.size / 1024).toFixed(0)} KB</span>
              </div>

              {upload.isPending && (
                <div className="mt-3 h-2 w-full overflow-hidden rounded-full bg-slate-100">
                  <div className="h-full bg-brand transition-all" style={{ width: `${progress}%` }} />
                </div>
              )}

              <button
                onClick={() => upload.mutate(file)}
                disabled={upload.isPending}
                className="mt-4 w-full rounded-lg bg-brand py-2.5 font-medium text-white hover:bg-brand-dark disabled:opacity-60"
              >
                {upload.isPending ? `Uploading… ${progress}%` : 'Upload'}
              </button>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
