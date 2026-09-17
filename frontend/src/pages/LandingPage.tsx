import { Link } from 'react-router-dom';

export function LandingPage() {
  return (
    <div className="min-h-screen bg-slate-50">
      <header className="mx-auto flex max-w-5xl items-center justify-between gap-2 px-4 py-4 sm:px-6 sm:py-5">
        <span className="text-lg font-bold text-brand sm:text-xl">Life Admin</span>
        <nav className="flex gap-2 sm:gap-3">
          <Link to="/login" className="rounded-lg px-3 py-2 text-sm text-slate-700 hover:bg-slate-100 sm:text-base">
            Sign in
          </Link>
          <Link
            to="/register"
            className="rounded-lg bg-brand px-3 py-2 text-sm font-medium text-white hover:bg-brand-dark sm:text-base"
          >
            Get started
          </Link>
        </nav>
      </header>

      <main className="mx-auto max-w-3xl px-4 py-16 text-center sm:px-6 sm:py-24">
        <h1 className="text-3xl font-bold leading-tight text-slate-900 sm:text-5xl">
          Never forget an important document, deadline, or renewal again.
        </h1>
        <p className="mx-auto mt-5 max-w-2xl text-base text-slate-600 sm:mt-6 sm:text-lg">
          Upload important documents. We&apos;ll find the dates and remind you before they matter.
        </p>
        <div className="mt-8 flex flex-col justify-center gap-3 sm:mt-10 sm:flex-row sm:gap-4">
          <Link
            to="/register"
            className="rounded-xl bg-brand px-6 py-3 text-lg font-semibold text-white shadow hover:bg-brand-dark"
          >
            Get started
          </Link>
          <Link
            to="/login"
            className="rounded-xl border border-slate-300 px-6 py-3 text-lg font-semibold text-slate-700 hover:bg-white"
          >
            Sign in
          </Link>
        </div>
      </main>
    </div>
  );
}
