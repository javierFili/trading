import { useState } from 'react';
import { api, post, type BacktestRun, type BacktestTrade, type SystemStatus } from '../api';
import { useAuth } from '../auth';
import { Badge, Card, Empty, ErrorBox, Stat, Table, useAsync } from '../components/ui';
import { dt, num, pct, qty, symbolPath } from '../format';

function Trades({ runId }: { runId: string }) {
  const { data, error } = useAsync(() => api<BacktestTrade[]>(`/api/v1/backtests/${runId}/trades`), [runId]);
  if (error) return <ErrorBox error={error} />;
  if (!data?.length) return <Empty>Sin operaciones.</Empty>;
  return (
    <Table head={['Entrada', 'Precio', 'Salida', 'Precio', 'Cantidad', 'Comisiones', 'PnL', 'Motivo']}>
      {data.map((t, i) => (
        <tr key={i}>
          <td className="td">{dt(t.entryTime)}</td><td className="td tabular-nums">{num(t.entryPrice)}</td>
          <td className="td">{dt(t.exitTime)}</td><td className="td tabular-nums">{num(t.exitPrice)}</td>
          <td className="td tabular-nums">{qty(t.quantity)}</td><td className="td tabular-nums">{num(t.fees, 4)}</td>
          <td className={`td tabular-nums ${t.pnl >= 0 ? 'text-emerald-300' : 'text-rose-300'}`}>{num(t.pnl, 4)}</td>
          <td className="td">{t.exitReason}</td>
        </tr>
      ))}
    </Table>
  );
}

function RunCard({ run }: { run: BacktestRun }) {
  const [open, setOpen] = useState(false);
  const m = run.metrics;
  return (
    <Card title={<span className="flex items-center gap-2">{run.symbol} · {run.split} <Badge value={run.status} /></span>}
          actions={<span className="text-xs text-slate-500">{dt(run.periodStart)} → {dt(run.periodEnd)}</span>}>
      {run.error && <ErrorBox error={run.error} />}
      {m && (
        <>
          <div className="grid grid-cols-2 gap-4 md:grid-cols-6">
            <Stat label="Beneficio neto" value={num(m.netProfit, 4)} tone={m.netProfit >= 0 ? 'good' : 'bad'} />
            <Stat label="Rentabilidad" value={pct(m.returnPct)} tone={m.returnPct >= 0 ? 'good' : 'bad'} />
            <Stat label="Operaciones" value={m.trades} />
            <Stat label="Win rate" value={pct(m.winRate)} />
            <Stat label="Profit factor" value={num(m.profitFactor)} />
            <Stat label="Max drawdown" value={`${num(m.maxDrawdown, 4)} (${pct(m.maxDrawdownPct)})`} tone="bad" />
            <Stat label="Sharpe (por operación)" value={num(m.sharpePerTrade)} />
            <Stat label="Operación media" value={num(m.averageTrade, 4)} />
            <Stat label="Ganancia/pérdida media" value={num(m.realizedRewardToRisk)} />
            <Stat label="Comisiones" value={num(m.totalFees, 4)} />
            <Stat label="Capital final" value={num(m.finalEquity, 4)} />
            <Stat label="Huecos en datos" value={run.dataGaps ?? '—'} tone={run.dataGaps ? 'bad' : undefined} />
          </div>
          {!m.statisticallySignificant && (
            <p className="mt-3 text-xs text-amber-300">Menos de 30 operaciones: los resultados no son estadísticamente significativos.</p>
          )}
        </>
      )}
      <p className="mt-3 text-xs text-slate-500">
        {run.strategyCode} v{run.strategyVersion} · {run.candleCount ?? '—'} velas · checksum {run.dataChecksum?.slice(0, 12) ?? '—'}
      </p>
      {run.status === 'COMPLETED' && (
        <button className="btn-ghost mt-2" onClick={() => setOpen(!open)}>{open ? 'Ocultar' : 'Ver'} operaciones</button>
      )}
      {open && <div className="mt-3"><Trades runId={run.id} /></div>}
    </Card>
  );
}

export default function Backtests({ status }: { status: SystemStatus | null }) {
  const { user } = useAuth();
  const symbol = status?.symbols[0] ?? 'BTC/USDT';
  const runs = useAsync(() => api<BacktestRun[]>('/api/v1/backtests?limit=30'), []);
  const [from, setFrom] = useState('2024-01-01');
  const [to, setTo] = useState('2025-01-01');
  const [split, setSplit] = useState(true);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const range = { from: `${from}T00:00:00Z`, to: `${to}T00:00:00Z` };

  const run = async (fn: () => Promise<string>) => {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      setMessage(await fn());
      runs.reload();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-4">
      <Card title="Nuevo backtest (EMA + RSI + MACD)">
        <div className="flex flex-wrap items-end gap-3">
          <div><label className="label">Desde</label><input type="date" className="input" value={from} onChange={(e) => setFrom(e.target.value)} /></div>
          <div><label className="label">Hasta</label><input type="date" className="input" value={to} onChange={(e) => setTo(e.target.value)} /></div>
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={split} onChange={(e) => setSplit(e.target.checked)} />
            Dividir en entrenamiento / validación / prueba (60/20/20)</label>
          {user?.role === 'TRADER' && (
            <>
              <button className="btn-ghost" disabled={busy} onClick={() => run(async () => {
                const r = await post<{ inserted: number }>(`/api/v1/market/${symbolPath(symbol)}/download`, { timeframe: status?.timeframe ?? 'H1', ...range });
                return `Descargadas ${r.inserted} velas nuevas de ${symbol} (endpoint público de solo datos de mercado).`;
              })}>1. Descargar histórico</button>
              <button className="btn-primary" disabled={busy} onClick={() => run(async () => {
                await post('/api/v1/backtests', { symbol, timeframe: status?.timeframe ?? 'H1', strategyCode: 'EMA_RSI_MACD', split, ...range });
                setTimeout(runs.reload, 4000);
                return 'Backtest lanzado; los resultados aparecerán abajo en unos segundos.';
              })}>2. Ejecutar backtest</button>
            </>
          )}
          <button className="btn-ghost" onClick={runs.reload}>Actualizar</button>
        </div>
        {message && <p className="mt-3 text-sm text-emerald-300">{message}</p>}
        <div className="mt-3"><ErrorBox error={error} /></div>
        <p className="mt-3 text-xs text-slate-500">
          Los resultados históricos no garantizan resultados futuros. No optimices parámetros mirando el tramo de prueba.
        </p>
      </Card>
      <ErrorBox error={runs.error} />
      {runs.data?.length === 0 && <Empty>Todavía no hay backtests.</Empty>}
      {runs.data?.map((r) => <RunCard key={r.id} run={r} />)}
    </div>
  );
}
