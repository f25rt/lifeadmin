import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AdminLayout } from './AdminLayout';
import { adminApi } from '../../api/admin';
import { errorMessage } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { USER_ROLES, type AdminUserRow } from '../../api/types';

/** A pending confirmation: either a role change or an enable/disable toggle. */
type Pending =
  | { kind: 'role'; user: AdminUserRow; role: string }
  | { kind: 'status'; user: AdminUserRow; disabled: boolean };

export function AdminUsersPage() {
  const queryClient = useQueryClient();
  const { user: me } = useAuth();
  const { data, isLoading } = useQuery({
    queryKey: ['admin', 'users'],
    queryFn: () => adminApi.users(),
  });

  const [pending, setPending] = useState<Pending | null>(null);
  const [error, setError] = useState<string | null>(null);

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });

  const roleMutation = useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: string }) =>
      adminApi.updateUserRole(userId, role),
    onSuccess: () => {
      invalidate();
      setPending(null);
    },
    onError: (err) => setError(errorMessage(err)),
  });

  const statusMutation = useMutation({
    mutationFn: ({ userId, disabled }: { userId: string; disabled: boolean }) =>
      adminApi.updateUserStatus(userId, disabled),
    onSuccess: () => {
      invalidate();
      setPending(null);
    },
    onError: (err) => setError(errorMessage(err)),
  });

  const busy = roleMutation.isPending || statusMutation.isPending;

  const confirm = () => {
    if (!pending) return;
    setError(null);
    if (pending.kind === 'role') {
      roleMutation.mutate({ userId: pending.user.id, role: pending.role });
    } else {
      statusMutation.mutate({ userId: pending.user.id, disabled: pending.disabled });
    }
  };

  const dialogCopy = (): { title: string; message: string; confirmLabel: string; destructive: boolean } => {
    if (!pending) return { title: '', message: '', confirmLabel: 'Confirm', destructive: false };
    if (pending.kind === 'role') {
      return {
        title: 'Change role',
        message: `Change ${pending.user.name} (${pending.user.email}) from ${pending.user.role} to ${pending.role}?`,
        confirmLabel: 'Change role',
        destructive: false,
      };
    }
    return {
      title: pending.disabled ? 'Disable user' : 'Enable user',
      message: pending.disabled
        ? `Disable ${pending.user.name} (${pending.user.email})? They will not be able to log in until re-enabled.`
        : `Enable ${pending.user.name} (${pending.user.email})? They will be able to log in again.`,
      confirmLabel: pending.disabled ? 'Disable' : 'Enable',
      destructive: pending.disabled,
    };
  };

  const copy = dialogCopy();

  return (
    <AdminLayout title="Users">
      {error && (
        <p className="mb-4 rounded-lg bg-red-50 px-4 py-2 text-sm text-red-700">{error}</p>
      )}
      {isLoading || !data ? (
        <p className="text-slate-500">Loading…</p>
      ) : (
        <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white">
          <table className="min-w-full divide-y divide-slate-100 text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-3">Name</th>
                <th className="px-4 py-3">Email</th>
                <th className="px-4 py-3">Role</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Country</th>
                <th className="px-4 py-3 text-right">Documents</th>
                <th className="px-4 py-3">Joined</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.map((u) => {
                const isSelf = me?.userId === u.id;
                return (
                  <tr key={u.id} className={`hover:bg-slate-50 ${u.disabled ? 'opacity-60' : ''}`}>
                    <td className="px-4 py-3 font-medium text-slate-900">{u.name}</td>
                    <td className="px-4 py-3 text-slate-600">{u.email}</td>
                    <td className="px-4 py-3">
                      <select
                        aria-label={`Role for ${u.name}`}
                        value={u.role}
                        disabled={isSelf || busy}
                        onChange={(e) => {
                          if (e.target.value !== u.role) {
                            setError(null);
                            setPending({ kind: 'role', user: u, role: e.target.value });
                          }
                        }}
                        className="rounded-lg border border-slate-300 px-2 py-1 text-xs font-medium text-slate-700 disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        {USER_ROLES.map((r) => (
                          <option key={r} value={r}>
                            {r}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td className="px-4 py-3">
                      {u.disabled ? (
                        <span className="rounded-full bg-red-100 px-2 py-0.5 text-xs font-medium text-red-700">
                          Disabled
                        </span>
                      ) : (
                        <span className="rounded-full bg-green-100 px-2 py-0.5 text-xs font-medium text-green-700">
                          Active
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-slate-600">{u.country}</td>
                    <td className="px-4 py-3 text-right">
                      <Link
                        to={`/admin/documents?accountId=${u.accountId}`}
                        className="font-medium text-brand hover:underline"
                      >
                        {u.documentCount}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-slate-500">
                      {new Date(u.createdAt).toLocaleDateString()}
                    </td>
                    <td className="px-4 py-3 text-right">
                      {isSelf ? (
                        <span className="text-xs text-slate-400">You</span>
                      ) : (
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() => {
                            setError(null);
                            setPending({ kind: 'status', user: u, disabled: !u.disabled });
                          }}
                          className={`rounded-lg border px-3 py-1 text-xs font-medium disabled:opacity-60 ${
                            u.disabled
                              ? 'border-green-300 text-green-700 hover:bg-green-50'
                              : 'border-red-300 text-red-700 hover:bg-red-50'
                          }`}
                        >
                          {u.disabled ? 'Enable' : 'Disable'}
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      <ConfirmDialog
        open={pending !== null}
        title={copy.title}
        message={copy.message}
        confirmLabel={copy.confirmLabel}
        destructive={copy.destructive}
        busy={busy}
        onConfirm={confirm}
        onCancel={() => {
          if (!busy) {
            setPending(null);
          }
        }}
      />
    </AdminLayout>
  );
}
