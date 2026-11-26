import { useEffect, useState } from 'react';
import { api, post, type Proposal } from '../api';
import { Badge, Card, Empty, ErrorBox, Stat, useAsync } from '../components/ui';
import { useAuth } from '../auth';
import { dt, num, qty } from '../format';
import { useServerEvents } from '../notifications';

function Countdown({ until }: { until: string }) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);
  const s = Math.max(0, Math.floor((Date.parse(until) - now) / 1000));
  return <span className={s < 60 ? 'text-rose-300' : 'text-amber-300'}>{Math.floor(s / 60)}:{String(s % 60).padStart(2, '0')}</span>;
}

function Decision({ p, onDone }: { p: Proposal; onDone: () => void }) {
  const { user } = useAuth();
  const [totp, setTotp] = useState('');
  const [reason, setReason] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  if (user?.role !== 'TRADER') return <p className="text-sm text-slate-500">Solo un usuario TRADER puede decidir.</p>;

  const act = async (kind: 'approve' | 'reject') => {
    setBusy(true);
    setError(null);
    try {
      const body = kind === 'approve'
        ? { version: p.version, contentHash: p.contentHash, totpCode: totp }
        : { version: p.version, contentHash: p.contentHash, reason };
      await post(`/api/v1/proposals/${p.id}/${kind}`, body);
      onDone();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-3 rounded-lg border border-slate-700 bg-slate-950/60 p-3">
      <p className="text-xs text-slate-400">
        Apruebas exactamente esta versión (v{p.version}, hash <code>{p.contentHash.slice(0, 12)}…</code>). Antes de ejecutar,
        el sistema revalida precio, saldo y riesgo; si algo cambia, la propuesta se cancela.
      </p>
      <div className="flex flex-wrap items-end gap-2">
        <div>
          <label className="label" htmlFor={`totp-${p.id}`}>Código TOTP</label>
          <input id={`totp-${p.id}`} className="input w-32 tracking-widest" inputMode="numeric" maxLength={6} value={totp}
                 onChange={(e) => setTotp(e.target.value.replace(/\D/g, ''))} />
        </div>
        <button className="btn-primary" disabled={busy || totp.length !== 6} onClick={() => act('approve')}>
          Aprobar {p.side === 'BUY' ? 'compra' : 'venta'}
        </button>
        <input className="input max-w-xs" placeholder="Motivo del rechazo (opcional)" value={reason} onChange={(e) => setReason(e.target.value)} />
        <button className="btn-danger" disabled={busy} onClick={() => act('reject')}>Rechazar</button>
      </div>
      <ErrorBox error={error} />
    </div>
  );
}

function ProposalCard({ p, onDone }: { p: Proposal; onDone: () => void }) {
  const quote = p.symbol.split('/')[1];
  return (
    <Card
      title={<span className="flex items-center gap-2">{p.purpose === 'ENTRY' ? 'Compra' : 'Venta'} {p.symbol} <Badge value={p.status} /></span>}
      actions={p.status === 'PENDING_APPROVAL' ? <span className="text-sm">Expira en <Countdown until={p.expiresAt} /></span> : <span className="text-xs text-slate-500">{dt(p.updatedAt)}</span>}
    >
      <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
        <Stat label="Precio de referencia" value={num(p.referencePrice)} />
        <Stat label="Cantidad" value={qty(p.quantity)} />
        <Stat label={`Nocional (${quote})`} value={num(p.notional)} />
        <Stat label={`Comisiones est. (${quote})`} value={num(p.estimatedFees, 4)} />
        {p.stopLoss !== null && <Stat label="Stop loss" value={num(p.stopLoss)} tone="bad" />}
        {p.takeProfit !== null && <Stat label="Take profit" value={num(p.takeProfit)} tone="good" />}
        {p.riskAmount !== null && <Stat label={`Riesgo máx. (${quote})`} value={num(p.riskAmount, 4)} tone="bad" />}
        {p.rewardToRisk !== null && <Stat label="Beneficio/riesgo neto" value={num(p.rewardToRisk)} />}
      </div>
      <details className="mt-3" open={p.status === 'PENDING_APPROVAL'}>
        <summary className="cursor-pointer text-sm text-slate-300">Justificación</summary>
        <ul className="mt-2 list-inside list-disc space-y-0.5 text-sm text-slate-300">
          {p.justification.map((j, i) => <li key={i}>{j}</li>)}
        </ul>
      </details>
      {p.statusReason && <p className="mt-2 text-sm text-slate-400">Motivo: {p.statusReason}</p>}
      {p.status === 'PENDING_APPROVAL' && <div className="mt-3"><Decision p={p} onDone={onDone} /></div>}
    </Card>
  );
}

export default function Proposals() {
  const { lastEvent } = useServerEvents();
  const version = lastEvent && lastEvent.type !== 'CANDLE_CLOSED' ? Date.parse(lastEvent.at) : 0;
  const { data, error, reload } = useAsync(() => api<Proposal[]>('/api/v1/proposals?limit=50'), [version]);
  const pending = data?.filter((p) => p.status === 'PENDING_APPROVAL') ?? [];
  const others = data?.filter((p) => p.status !== 'PENDING_APPROVAL') ?? [];
  return (
    <div className="space-y-4">
      <ErrorBox error={error} />
      <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-400">Pendientes de aprobación</h2>
      {pending.length === 0 && <Empty>No hay propuestas pendientes.</Empty>}
      {pending.map((p) => <ProposalCard key={p.id} p={p} onDone={reload} />)}
      <h2 className="pt-4 text-sm font-semibold uppercase tracking-wide text-slate-400">Historial</h2>
      {others.length === 0 && <Empty>Sin historial.</Empty>}
      {others.map((p) => <ProposalCard key={p.id} p={p} onDone={reload} />)}
    </div>
  );
}
