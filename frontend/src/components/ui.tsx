import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { errorText } from '../format';

const STATUS_COLORS: Record<string, string> = {
  PENDING_APPROVAL: 'bg-amber-500/15 text-amber-300 ring-amber-500/30',
  APPROVED: 'bg-sky-500/15 text-sky-300 ring-sky-500/30',
  EXECUTING: 'bg-sky-500/15 text-sky-300 ring-sky-500/30',
  PARTIALLY_FILLED: 'bg-sky-500/15 text-sky-300 ring-sky-500/30',
  FILLED: 'bg-emerald-500/15 text-emerald-300 ring-emerald-500/30',
  COMPLETED: 'bg-emerald-500/15 text-emerald-300 ring-emerald-500/30',
  OPEN: 'bg-emerald-500/15 text-emerald-300 ring-emerald-500/30',
  NEW: 'bg-sky-500/15 text-sky-300 ring-sky-500/30',
  ENTRY_LONG: 'bg-emerald-500/15 text-emerald-300 ring-emerald-500/30',
  EXIT_LONG: 'bg-rose-500/15 text-rose-300 ring-rose-500/30',
  REJECTED: 'bg-rose-500/15 text-rose-300 ring-rose-500/30',
  FAILED: 'bg-rose-500/15 text-rose-300 ring-rose-500/30',
  CRITICAL: 'bg-rose-500/15 text-rose-300 ring-rose-500/30',
  UNKNOWN: 'bg-orange-500/15 text-orange-300 ring-orange-500/30',
  WARNING: 'bg-amber-500/15 text-amber-300 ring-amber-500/30',
  TESTNET: 'bg-sky-500/15 text-sky-300 ring-sky-500/30',
  BACKTEST: 'bg-violet-500/15 text-violet-300 ring-violet-500/30',
  LIVE: 'bg-rose-600/30 text-rose-200 ring-rose-500/50',
};

export function Badge({ value }: { value: string }) {
  const color = STATUS_COLORS[value] ?? 'bg-slate-500/15 text-slate-300 ring-slate-500/30';
  return <span className={`inline-flex rounded-md px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${color}`}>{value}</span>;
}

export function Card({ title, actions, children }: { title?: ReactNode; actions?: ReactNode; children: ReactNode }) {
  return (
    <section className="card">
      {(title || actions) && (
        <div className="mb-3 flex items-center justify-between gap-2">
          <h2 className="text-sm font-semibold text-slate-200">{title}</h2>
          <div className="flex gap-2">{actions}</div>
        </div>
      )}
      {children}
    </section>
  );
}

export function ErrorBox({ error }: { error: unknown }) {
  if (!error) return null;
  return <div className="rounded-lg border border-rose-800 bg-rose-950/50 p-3 text-sm text-rose-200">{errorText(error)}</div>;
}

export function Empty({ children }: { children: ReactNode }) {
  return <p className="py-6 text-center text-sm text-slate-500">{children}</p>;
}

/** Loads data, exposes reload(), and re-runs when {@code deps} change. */
export function useAsync<T>(loader: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const load = useCallback(loader, deps);
  const reload = useCallback(() => {
    setLoading(true);
    load()
      .then((d) => {
        setData(d);
        setError(null);
      })
      .catch(setError)
      .finally(() => setLoading(false));
  }, [load]);
  useEffect(reload, [reload]);
  return { data, error, loading, reload };
}

export function Table({ head, children }: { head: string[]; children: ReactNode }) {
  return (
    <div className="overflow-x-auto">
      <table className="min-w-full divide-y divide-slate-800">
        <thead>
          <tr>{head.map((h) => <th key={h} className="th">{h}</th>)}</tr>
        </thead>
        <tbody className="divide-y divide-slate-800/60">{children}</tbody>
      </table>
    </div>
  );
}

export function Stat({ label, value, tone }: { label: string; value: ReactNode; tone?: 'good' | 'bad' }) {
  const color = tone === 'good' ? 'text-emerald-300' : tone === 'bad' ? 'text-rose-300' : 'text-slate-100';
  return (
    <div>
      <div className="label">{label}</div>
      <div className={`text-lg font-semibold tabular-nums ${color}`}>{value}</div>
    </div>
  );
}
