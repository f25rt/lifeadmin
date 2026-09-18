import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { AppHeader } from '../components/AppHeader';
import { useAuth } from '../auth/AuthContext';
import { dashboardApi } from '../api/dashboard';
import { accountApi } from '../api/account';
import type { AttentionItem, UpcomingDate } from '../api/types';

export function DashboardPage() {
  const { user } = useAuth();
  const { data, isLoading } = useQuery({
    queryKey: ['dashboard'],
    queryFn: () => dashboardApi.get(),
  });
  const { data: usage } = useQuery({
    queryKey: ['account', 'usage'],
    queryFn: () => accountApi.usage(),
  });

  const counts = data?.counts;

  return (
    <div className="min-h-screen bg-slate-50">
      <AppHeader />
      <main className="mx-auto max-w-5xl px-4 py-8 sm:px-6 sm:py-10">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h1 className="text-2xl font-semibold text-slate-900">
            Good day, {user?.name ?? 'there'}
          </h1>
          <Link
            to="/documents/upload"
            className="rounded-lg bg-brand px-4 py-2 text-sm font-medium text-white hover:bg-brand-dark"
          >
            + Add document
          </Link>
        </div>
        <p className="mt-1 text-slate-600">Your workspace overview.</p>

        <div className="mt-8 grid grid-cols-2 gap-4 lg:grid-cols-4">
          <MetricCard label="Documents" value={counts?.totalDocuments ?? 0} />
          <MetricCard label="Expiring soon" value={counts?.expiringSoon ?? 0} accent="amber" />
          <MetricCard label="Needs review" value={counts?.needsReview ?? 0} accent="blue" />
          <MetricCard label="Active" value={counts?.activeDocuments ?? 0} accent="green" />
        </div>

        {usage && (
          <Link
            to="/account"
            className="mt-4 flex items-center justify-between rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm hover:border-brand"
          >
            <span className="text-slate-600">
              {usage.plan} plan · {usage.documentsUsed} of {usage.documentLimit} documents used
            </span>
            <span className="font-medium text-brand">Manage →</span>
          </Link>
        )}

        {(data?.needsAttention.length ?? 0) > 0 && (
          <section className="mt-8">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-400">Needs attention</h2>
            <ul className="mt-3 space-y-2">
              {data!.needsAttention.map((a) => (
                <AttentionRow key={`${a.documentId}-${a.reason}`} item={a} />
              ))}
            </ul>
          </section>
        )}

        <section className="mt-8">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-400">Upcoming dates</h2>
            <Link to="/reminders" className="text-sm font-medium text-brand hover:underline">
              View reminders →
            </Link>
          </div>

          {isLoading ? (
            <p className="mt-4 text-slate-500">Loading…</p>
          ) : (data?.upcoming.length ?? 0) === 0 ? (
            <div className="mt-4 rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center">
              <p className="text-slate-500">No upcoming dates. Upload a document to get started.</p>
              <Link
                to="/documents"
                className="mt-4 inline-block rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
              >
                View documents
              </Link>
            </div>
          ) : (
            <ul className="mt-4 space-y-2">
              {data!.upcoming.slice(0, 10).map((u) => (
                <UpcomingRow key={u.importantDateId} item={u} />
              ))}
            </ul>
          )}
        </section>
      </main>
    </div>
  );
}

function MetricCard({
  label,
  value,
  accent,
}: {
  label: string;
  value: number;
  accent?: 'amber' | 'blue' | 'green';
}) {
  const color =
    accent === 'amber'
      ? 'text-amber-600'
      : accent === 'blue'
        ? 'text-blue-600'
        : accent === 'green'
          ? 'text-green-600'
          : 'text-slate-900';
  return (
    <div className="rounded-2xl bg-white p-5 shadow-sm">
      <div className={`text-3xl font-bold ${color}`}>{value}</div>
      <div className="mt-1 text-sm text-slate-500">{label}</div>
    </div>
  );
}

function UpcomingRow({ item }: { item: UpcomingDate }) {
  const soon = item.daysUntil <= 30;
  return (
    <li className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-200 bg-white p-4">
      <div className="min-w-0">
        <Link to={`/documents/${item.documentId}`} className="font-medium text-slate-900 hover:text-brand">
          {item.documentTitle}
        </Link>
        <p className="mt-0.5 text-sm text-slate-500">
          {item.dateType.replace(/_/g, ' ').toLowerCase()} · {item.dateValue}
          {!item.hasReminder && <span className="ml-2 text-amber-600">no reminder</span>}
        </p>
      </div>
      <span
        className={`rounded-full px-2.5 py-1 text-xs font-medium ${
          soon ? 'bg-amber-50 text-amber-700' : 'bg-slate-100 text-slate-600'
        }`}
      >
        {daysLabel(item.daysUntil)}
      </span>
    </li>
  );
}

function AttentionRow({ item }: { item: AttentionItem }) {
  return (
    <li className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4">
      <div className="min-w-0">
        <Link to={`/documents/${item.documentId}`} className="font-medium text-amber-900 hover:underline">
          {item.documentTitle}
        </Link>
        <p className="mt-0.5 text-sm text-amber-800">{item.detail}</p>
      </div>
      <Link
        to={`/documents/${item.documentId}`}
        className="rounded-md border border-amber-300 bg-white px-3 py-1.5 text-xs font-medium text-amber-800 hover:bg-amber-100"
      >
        Set reminder
      </Link>
    </li>
  );
}

function daysLabel(days: number): string {
  if (days <= 0) return 'today';
  if (days === 1) return 'in 1 day';
  return `in ${days} days`;
}
