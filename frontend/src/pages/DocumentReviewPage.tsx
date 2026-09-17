import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AppHeader } from '../components/AppHeader';
import { documentsApi } from '../api/documents';
import { errorMessage } from '../api/client';
import { DOCUMENT_TYPES, type DocumentType } from '../api/types';

/**
 * "We found these details — please verify" (spec §8). Editable fields + dates (with explicit vs.
 * derived badges) and suggested actions. Submitting marks everything USER-verified → ACTIVE.
 */
export function DocumentReviewPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { data: doc, isLoading, error } = useQuery({
    queryKey: ['documents', id],
    queryFn: () => documentsApi.get(id),
    enabled: !!id,
  });

  const [docType, setDocType] = useState<DocumentType | ''>('');
  const [fieldValues, setFieldValues] = useState<Record<string, string>>({});

  // Seed local editable state once the document loads.
  useEffect(() => {
    if (doc) {
      setDocType(doc.documentType ?? '');
      const seed: Record<string, string> = {};
      doc.fields.forEach((f) => (seed[f.fieldName] = f.fieldValue ?? ''));
      setFieldValues(seed);
    }
  }, [doc]);

  const verify = useMutation({
    mutationFn: () =>
      documentsApi.verify(id, {
        documentType: docType || undefined,
        fields: Object.entries(fieldValues).map(([fieldName, fieldValue]) => ({ fieldName, fieldValue })),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] });
      navigate(`/documents/${id}`);
    },
  });

  return (
    <div className="min-h-screen bg-slate-50">
      <AppHeader />
      <main className="mx-auto max-w-2xl px-4 py-8 sm:px-6 sm:py-10">
        {error && <div className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">{errorMessage(error)}</div>}
        {isLoading || !doc ? (
          <p className="text-slate-500">Loading…</p>
        ) : (
          <>
            <h1 className="text-2xl font-semibold text-slate-900">We found these details</h1>
            <p className="mt-1 text-slate-600">
              Please review and correct anything that&apos;s wrong, then confirm. Nothing is saved as
              final until you verify it.
            </p>

            <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-5">
              <label className="block">
                <span className="mb-1 block text-sm font-medium text-slate-700">Document type</span>
                <select
                  value={docType}
                  onChange={(e) => setDocType(e.target.value as DocumentType)}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2"
                >
                  <option value="">Unclassified</option>
                  {DOCUMENT_TYPES.map((t) => (
                    <option key={t} value={t}>{t.replace(/_/g, ' ')}</option>
                  ))}
                </select>
                {doc.classificationConfidence != null && (
                  <span className="mt-1 block text-xs text-slate-400">
                    AI confidence: {(doc.classificationConfidence * 100).toFixed(0)}%
                  </span>
                )}
              </label>

              {doc.fields.length > 0 && (
                <div className="mt-5 space-y-4">
                  <h2 className="text-sm font-semibold text-slate-700">Details</h2>
                  {doc.fields.map((f) => (
                    <label key={f.id} className="block">
                      <span className="mb-1 flex items-center gap-2 text-sm text-slate-600">
                        {f.fieldName}
                        <SourceBadge source={f.source} confidence={f.confidence} />
                      </span>
                      <input
                        value={fieldValues[f.fieldName] ?? ''}
                        onChange={(e) => setFieldValues((s) => ({ ...s, [f.fieldName]: e.target.value }))}
                        className="w-full rounded-lg border border-slate-300 px-3 py-2"
                      />
                    </label>
                  ))}
                </div>
              )}
            </section>

            {doc.dates.length > 0 && (
              <section className="mt-4 rounded-2xl border border-slate-200 bg-white p-5">
                <h2 className="text-sm font-semibold text-slate-700">Important dates</h2>
                <ul className="mt-3 space-y-2">
                  {doc.dates.map((d) => (
                    <li key={d.id} className="flex items-center justify-between gap-2 text-sm">
                      <span className="text-slate-700">{d.dateType.replace(/_/g, ' ')}</span>
                      <span className="flex items-center gap-2">
                        <span className="font-medium text-slate-900">{d.dateValue}</span>
                        {d.derived ? (
                          <span className="rounded-full bg-purple-50 px-2 py-0.5 text-xs text-purple-700" title="Calculated, not printed on the document">
                            derived
                          </span>
                        ) : (
                          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-500">
                            found
                          </span>
                        )}
                      </span>
                    </li>
                  ))}
                </ul>
                <p className="mt-3 text-xs text-slate-400">
                  &ldquo;Derived&rdquo; dates are calculated (e.g. warranty term), not printed on the document.
                </p>
              </section>
            )}

            {doc.suggestedActions.length > 0 && (
              <section className="mt-4 rounded-2xl border border-slate-200 bg-white p-5">
                <h2 className="text-sm font-semibold text-slate-700">Suggested actions</h2>
                <ul className="mt-3 space-y-2">
                  {doc.suggestedActions.map((a) => (
                    <li key={a.id} className="flex items-center gap-2 text-sm text-slate-700">
                      <span className="text-slate-400">□</span> {a.label}
                    </li>
                  ))}
                </ul>
              </section>
            )}

            <div className="mt-6 flex gap-3">
              <button
                onClick={() => verify.mutate()}
                disabled={verify.isPending}
                className="rounded-lg bg-brand px-5 py-2.5 font-medium text-white hover:bg-brand-dark disabled:opacity-60"
              >
                {verify.isPending ? 'Confirming…' : 'Confirm details'}
              </button>
              <button
                onClick={() => navigate(`/documents/${id}`)}
                className="rounded-lg border border-slate-300 px-5 py-2.5 text-slate-700 hover:bg-slate-100"
              >
                Cancel
              </button>
            </div>
          </>
        )}
      </main>
    </div>
  );
}

function SourceBadge({ source, confidence }: { source: string; confidence: number | null }) {
  const label = source === 'DERIVED' ? 'derived' : source === 'AI' ? 'AI' : source === 'OCR' ? 'found' : source.toLowerCase();
  return (
    <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-500">
      {label}
      {confidence != null && source !== 'USER' ? ` · ${(confidence * 100).toFixed(0)}%` : ''}
    </span>
  );
}
