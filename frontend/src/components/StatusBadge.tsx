import type { DocumentStatus } from '../api/types';

const STYLES: Record<DocumentStatus, string> = {
  UPLOADED: 'bg-blue-50 text-blue-700',
  PROCESSING: 'bg-amber-50 text-amber-700',
  REVIEW_REQUIRED: 'bg-orange-50 text-orange-700',
  ACTIVE: 'bg-green-50 text-green-700',
  ARCHIVED: 'bg-slate-100 text-slate-600',
  FAILED: 'bg-red-50 text-red-700',
};

export function StatusBadge({ status }: { status: DocumentStatus }) {
  return (
    <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${STYLES[status]}`}>
      {status.replace('_', ' ')}
    </span>
  );
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
