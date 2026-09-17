import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { remindersApi } from '../api/reminders';
import { errorMessage } from '../api/client';
import type { DateView, ReminderChannel, ReminderDirection } from '../api/types';

const OFFSET_PRESETS = [7, 30, 60, 90];

/**
 * Inline "set a reminder" control for one important date. Users pick one or more "days before"
 * offsets (or a custom value) and a channel; the backend creates one reminder per offset.
 */
export function ReminderForm({ documentId, date }: { documentId: string; date: DateView }) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState<number[]>([30]);
  const [custom, setCustom] = useState('');
  const [channel, setChannel] = useState<ReminderChannel>('IN_APP');
  const [direction, setDirection] = useState<ReminderDirection>('BEFORE');

  const create = useMutation({
    mutationFn: () => {
      const offsets = [...selected];
      const c = Number(custom);
      if (custom && Number.isFinite(c) && c >= 0 && !offsets.includes(c)) {
        offsets.push(c);
      }
      return remindersApi.create({
        documentId,
        importantDateId: date.id,
        offsetsDaysBefore: offsets.length > 0 ? offsets : [30],
        channel,
        direction,
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reminders'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      setOpen(false);
      setCustom('');
    },
  });

  const toggle = (n: number) =>
    setSelected((prev) => (prev.includes(n) ? prev.filter((x) => x !== n) : [...prev, n]));

  if (!open) {
    return (
      <button
        onClick={() => setOpen(true)}
        className="rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
      >
        Set reminder
      </button>
    );
  }

  return (
    <div className="w-full rounded-lg border border-slate-200 bg-slate-50 p-3">
      <p className="text-xs font-medium text-slate-600">
        Remind me {direction === 'AFTER' ? 'after' : 'before'} this date:
      </p>

      {/* Before / After direction */}
      <div className="mt-2 inline-flex rounded-lg border border-slate-300 bg-white p-0.5" role="group" aria-label="Reminder direction">
        {(['BEFORE', 'AFTER'] as const).map((d) => (
          <button
            key={d}
            type="button"
            onClick={() => setDirection(d)}
            aria-pressed={direction === d}
            className={`rounded-md px-3 py-1 text-xs font-medium ${
              direction === d ? 'bg-brand text-white' : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            {d === 'BEFORE' ? 'Before' : 'After'}
          </button>
        ))}
      </div>

      <div className="mt-2 flex flex-wrap gap-1.5">
        {OFFSET_PRESETS.map((n) => (
          <button
            key={n}
            onClick={() => toggle(n)}
            className={`rounded-full px-3 py-1 text-xs font-medium ${
              selected.includes(n)
                ? 'bg-brand text-white'
                : 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-100'
            }`}
          >
            {n} days
          </button>
        ))}
        <input
          type="number"
          min={0}
          value={custom}
          onChange={(e) => setCustom(e.target.value)}
          placeholder="custom"
          className="w-20 rounded-full border border-slate-300 px-3 py-1 text-xs"
          aria-label={`Custom days ${direction === 'AFTER' ? 'after' : 'before'}`}
        />
      </div>
      <p className="mt-1.5 text-xs text-slate-400">
        {selectedSummary(selected, custom, direction)}
      </p>

      <div className="mt-3 flex items-center gap-2">
        <label className="text-xs text-slate-600" htmlFor={`channel-${date.id}`}>
          Channel
        </label>
        <select
          id={`channel-${date.id}`}
          value={channel}
          onChange={(e) => setChannel(e.target.value as ReminderChannel)}
          className="rounded-md border border-slate-300 px-2 py-1 text-xs"
        >
          <option value="IN_APP">In-app</option>
          <option value="EMAIL">Email</option>
        </select>
      </div>

      {create.isError && (
        <p className="mt-2 text-xs text-red-600">{errorMessage(create.error)}</p>
      )}

      <div className="mt-3 flex gap-2">
        <button
          onClick={() => create.mutate()}
          disabled={create.isPending}
          className="rounded-md bg-brand px-3 py-1.5 text-xs font-medium text-white hover:bg-brand-dark disabled:opacity-60"
        >
          {create.isPending ? 'Saving…' : 'Save reminder'}
        </button>
        <button
          onClick={() => setOpen(false)}
          className="rounded-md border border-slate-300 px-3 py-1.5 text-xs text-slate-700 hover:bg-slate-100"
        >
          Cancel
        </button>
      </div>
    </div>
  );
}

/** Human-readable preview of the chosen offsets, e.g. "7, 30 days before the date". */
function selectedSummary(selected: number[], custom: string, direction: ReminderDirection): string {
  const offsets = [...selected];
  const c = Number(custom);
  if (custom && Number.isFinite(c) && c >= 0 && !offsets.includes(c)) {
    offsets.push(c);
  }
  if (offsets.length === 0) {
    return 'Pick at least one offset.';
  }
  offsets.sort((a, b) => a - b);
  const word = direction === 'AFTER' ? 'after' : 'before';
  return `${offsets.join(', ')} day${offsets.length === 1 && offsets[0] === 1 ? '' : 's'} ${word} the date`;
}
