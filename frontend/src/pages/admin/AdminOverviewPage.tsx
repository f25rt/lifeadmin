import { useQuery } from '@tanstack/react-query';
import { AdminLayout } from './AdminLayout';
import { adminApi } from '../../api/admin';

export function AdminOverviewPage() {
  const { data, isLoading } = useQuery({
    queryKey: ['admin', 'overview'],
    queryFn: () => adminApi.overview(),
  });

  return (
    <AdminLayout title="Platform overview">
      {isLoading || !data ? (
        <p className="text-slate-500">Loading…</p>
      ) : (
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-3">
          <Stat label="Users" value={data.totalUsers} />
          <Stat label="Accounts" value={data.totalAccounts} />
          <Stat label="Documents" value={data.totalDocuments} />
          <Stat label="Active" value={data.documentsActive} accent="green" />
          <Stat label="Processing" value={data.documentsProcessing} accent="amber" />
          <Stat label="Failed" value={data.documentsFailed} accent="red" />
        </div>
      )}
    </AdminLayout>
  );
}

function Stat({
  label,
  value,
  accent,
}: {
  label: string;
  value: number;
  accent?: 'green' | 'amber' | 'red';
}) {
  const color =
    accent === 'green'
      ? 'text-green-600'
      : accent === 'amber'
        ? 'text-amber-600'
        : accent === 'red'
          ? 'text-red-600'
          : 'text-slate-900';
  return (
    <div className="rounded-2xl bg-white p-5 shadow-sm">
      <div className={`text-3xl font-bold ${color}`}>{value}</div>
      <div className="mt-1 text-sm text-slate-500">{label}</div>
    </div>
  );
}
