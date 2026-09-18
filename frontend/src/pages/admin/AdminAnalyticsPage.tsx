import { useQuery } from '@tanstack/react-query';
import { AdminLayout } from './AdminLayout';
import { adminApi } from '../../api/admin';
import type { FunnelStage, RetentionBucket } from '../../api/types';

export function AdminAnalyticsPage() {
  const { data, isLoading } = useQuery({
    queryKey: ['admin', 'analytics'],
    queryFn: () => adminApi.analytics(),
  });

  return (
    <AdminLayout title="Analytics">
      {isLoading || !data ? (
        <p className="text-slate-500">Loading…</p>
      ) : (
        <div className="space-y-8">
          {/* Funnel */}
          <section className="rounded-2xl border border-slate-200 bg-white p-6">
            <h2 className="text-sm font-semibold text-slate-800">Success funnel</h2>
            <p className="mt-0.5 text-xs text-slate-500">
              Accounts reaching each stage (% of all signups).
            </p>
            <ul className="mt-4 space-y-2">
              {data.funnel.map((s) => (
                <FunnelRow key={s.stage} stage={s} />
              ))}
            </ul>
          </section>

          {/* Rates */}
          <section className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <RateCard
              label="Processing success"
              rate={data.processingSuccessRate}
              detail={`${data.documentsProcessedOk} ok · ${data.documentsFailed} failed`}
            />
            <RateCard
              label="Reminder conversion"
              rate={data.reminderConversionRate}
              detail="reminders fired vs. created"
            />
          </section>

          {/* Retention */}
          <section className="rounded-2xl border border-slate-200 bg-white p-6">
            <h2 className="text-sm font-semibold text-slate-800">Retention</h2>
            <p className="mt-0.5 text-xs text-slate-500">
              Accounts active beyond their first N-day window (of those old enough to qualify).
            </p>
            <div className="mt-4 grid grid-cols-3 gap-4">
              {data.retention.map((r) => (
                <RetentionCard key={r.days} bucket={r} />
              ))}
            </div>
          </section>
        </div>
      )}
    </AdminLayout>
  );
}

function FunnelRow({ stage }: { stage: FunnelStage }) {
  const pct = Math.max(0, Math.min(100, stage.pctOfSignups));
  return (
    <li>
      <div className="flex items-baseline justify-between text-sm">
        <span className="font-medium text-slate-700">{stage.stage}</span>
        <span className="text-slate-500">
          {stage.count} · {stage.pctOfSignups}%
        </span>
      </div>
      <div className="mt-1 h-2 w-full overflow-hidden rounded-full bg-slate-100">
        <div className="h-full bg-brand" style={{ width: `${pct}%` }} />
      </div>
    </li>
  );
}

function RateCard({ label, rate, detail }: { label: string; rate: number; detail: string }) {
  const color = rate >= 80 ? 'text-green-600' : rate >= 50 ? 'text-amber-600' : 'text-red-600';
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5">
      <div className={`text-3xl font-bold ${color}`}>{rate}%</div>
      <div className="mt-1 text-sm font-medium text-slate-700">{label}</div>
      <div className="mt-0.5 text-xs text-slate-400">{detail}</div>
    </div>
  );
}

function RetentionCard({ bucket }: { bucket: RetentionBucket }) {
  return (
    <div className="rounded-xl bg-slate-50 p-4 text-center">
      <div className="text-2xl font-bold text-slate-900">{bucket.rate}%</div>
      <div className="mt-1 text-xs font-medium text-slate-600">{bucket.days}-day</div>
      <div className="mt-0.5 text-xs text-slate-400">
        {bucket.retained}/{bucket.eligible}
      </div>
    </div>
  );
}
