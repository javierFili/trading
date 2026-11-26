export const num = (v: number | null | undefined, digits = 2) =>
  v === null || v === undefined ? '—' : v.toLocaleString('es-ES', { minimumFractionDigits: digits, maximumFractionDigits: digits });

export const qty = (v: number | null | undefined) => num(v, 6);

export const pct = (v: number | null | undefined) => (v === null || v === undefined ? '—' : `${num(v * 100, 2)} %`);

export const dt = (iso: string | null | undefined) =>
  iso ? new Date(iso).toLocaleString('es-ES', { dateStyle: 'short', timeStyle: 'short' }) : '—';

export const symbolPath = (s: string) => s.replace('/', '-');

export function errorText(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}
