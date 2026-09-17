import type { ReactNode } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';

const adminNav = [
  { to: '/admin', label: 'Overview', end: true },
  { to: '/admin/users', label: 'Users' },
  { to: '/admin/documents', label: 'Documents' },
  { to: '/admin/upload-rules', label: 'Upload rules' },
  { to: '/admin/document-types', label: 'Document types & AI' },
];

/** Shell for the platform-admin section: header + horizontal sub-nav + page content. */
export function AdminLayout({ title, children }: { title: string; children: ReactNode }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-slate-900 text-white">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-x-4 gap-y-2 px-4 py-3 sm:px-6">
          <div className="flex items-center gap-3">
            <Link to="/admin" className="text-lg font-bold">
              Life Admin <span className="text-brand-light font-normal">· Admin</span>
            </Link>
          </div>
          <div className="flex items-center gap-2 sm:gap-3">
            <Link to="/dashboard" className="text-sm text-slate-300 hover:text-white">
              ← Back to app
            </Link>
            <span className="rounded-full bg-white/10 px-2.5 py-1 text-xs font-medium">{user?.email}</span>
            <button
              onClick={handleLogout}
              className="rounded-lg border border-white/20 px-3 py-1.5 text-sm hover:bg-white/10"
            >
              Log out
            </button>
          </div>
        </div>
        <nav className="mx-auto flex max-w-6xl gap-1 overflow-x-auto px-4 pb-2 sm:px-6">
          {adminNav.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `whitespace-nowrap rounded-lg px-3 py-1.5 text-sm ${
                  isActive ? 'bg-white/15 font-medium text-white' : 'text-slate-300 hover:bg-white/10'
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-8 sm:px-6 sm:py-10">
        <h1 className="text-2xl font-semibold text-slate-900">{title}</h1>
        <div className="mt-6">{children}</div>
      </main>
    </div>
  );
}
