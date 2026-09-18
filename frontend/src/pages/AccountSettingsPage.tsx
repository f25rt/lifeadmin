import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { AppHeader } from '../components/AppHeader';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { accountApi } from '../api/account';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';

export function AccountSettingsPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const { data: usage } = useQuery({
    queryKey: ['account', 'usage'],
    queryFn: () => accountApi.usage(),
  });

  const [confirmOpen, setConfirmOpen] = useState(false);
  const [confirmEmail, setConfirmEmail] = useState('');
  const [error, setError] = useState<string | null>(null);

  const exportMutation = useMutation({
    mutationFn: () => accountApi.exportData(),
    onSuccess: (blob) => {
      // Trigger a browser download of the returned JSON.
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'lifeadmin-export.json';
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    },
    onError: (err) => setError(errorMessage(err)),
  });

  const deleteMutation = useMutation({
    mutationFn: () => accountApi.deleteAccount(confirmEmail.trim()),
    onSuccess: async () => {
      // Data is gone; clear the session and return to the landing page.
      await logout();
      navigate('/', { replace: true });
    },
    onError: (err) => {
      setError(errorMessage(err));
      setConfirmOpen(false);
    },
  });

  const emailMatches = confirmEmail.trim().toLowerCase() === (user?.email ?? '').toLowerCase();
  const usedPct =
    usage && usage.documentLimit > 0
      ? Math.min(100, Math.round((usage.documentsUsed / usage.documentLimit) * 100))
      : 0;

  return (
    <div className="min-h-screen bg-slate-50">
      <AppHeader />
      <main className="mx-auto max-w-3xl px-4 py-8 sm:px-6 sm:py-10">
        <h1 className="text-2xl font-semibold text-slate-900">Account &amp; data</h1>
        <p className="mt-1 text-slate-600">Manage your plan usage and your personal data.</p>

        {error && (
          <div className="mt-4 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>
        )}

        {/* Usage */}
        <section className="mt-8 rounded-2xl border border-slate-200 bg-white p-6">
          <h2 className="text-sm font-semibold text-slate-800">Plan usage</h2>
          {usage ? (
            <div className="mt-3">
              <div className="flex items-baseline justify-between text-sm">
                <span className="font-medium text-slate-700">{usage.plan} plan</span>
                <span className="text-slate-500">
                  {usage.documentsUsed} of {usage.documentLimit} documents
                </span>
              </div>
              <div className="mt-2 h-2 w-full overflow-hidden rounded-full bg-slate-100">
                <div
                  className={`h-full ${usedPct >= 100 ? 'bg-red-500' : usedPct >= 80 ? 'bg-amber-500' : 'bg-brand'}`}
                  style={{ width: `${usedPct}%` }}
                />
              </div>
              {usedPct >= 100 && (
                <p className="mt-2 text-xs text-red-600">
                  You've reached your document limit. Archive or delete documents to free up space.
                </p>
              )}
            </div>
          ) : (
            <p className="mt-3 text-sm text-slate-500">Loading…</p>
          )}
        </section>

        {/* Export */}
        <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-6">
          <h2 className="text-sm font-semibold text-slate-800">Export your data</h2>
          <p className="mt-1 text-sm text-slate-500">
            Download a JSON copy of your account, documents, extracted details, and reminders.
          </p>
          <button
            type="button"
            onClick={() => {
              setError(null);
              exportMutation.mutate();
            }}
            disabled={exportMutation.isPending}
            className="mt-3 rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60"
          >
            {exportMutation.isPending ? 'Preparing…' : 'Download export'}
          </button>
        </section>

        {/* Danger zone */}
        <section className="mt-6 rounded-2xl border border-red-200 bg-white p-6">
          <h2 className="text-sm font-semibold text-red-700">Delete account</h2>
          <p className="mt-1 text-sm text-slate-500">
            Permanently delete your account and all associated documents, extracted data, and
            reminders. This cannot be undone.
          </p>
          <label htmlFor="confirm-email" className="mt-4 block text-sm text-slate-700">
            Type your email <span className="font-medium">{user?.email}</span> to confirm:
          </label>
          <input
            id="confirm-email"
            type="email"
            value={confirmEmail}
            onChange={(e) => setConfirmEmail(e.target.value)}
            placeholder={user?.email}
            className="mt-1 w-full max-w-sm rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-red-400 focus:outline-none focus:ring-1 focus:ring-red-400"
          />
          <div className="mt-3">
            <button
              type="button"
              disabled={!emailMatches || deleteMutation.isPending}
              onClick={() => {
                setError(null);
                setConfirmOpen(true);
              }}
              className="rounded-lg bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              Delete my account
            </button>
          </div>
        </section>
      </main>

      <ConfirmDialog
        open={confirmOpen}
        title="Delete account permanently?"
        message="This will erase your account and every document, extracted detail, and reminder. This action cannot be undone."
        confirmLabel="Delete everything"
        destructive
        busy={deleteMutation.isPending}
        onConfirm={() => deleteMutation.mutate()}
        onCancel={() => {
          if (!deleteMutation.isPending) setConfirmOpen(false);
        }}
      />
    </div>
  );
}
