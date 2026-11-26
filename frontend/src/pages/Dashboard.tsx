import { useEffect, useRef } from 'react';
import { CandlestickSeries, ColorType, createChart, createSeriesMarkers, LineSeries, type UTCTimestamp } from 'lightweight-charts';
import { api, type CandleDto, type Portfolio, type Signal, type SystemStatus } from '../api';
import { Badge, Card, Empty, ErrorBox, Stat, Table, useAsync } from '../components/ui';
import { dt, num, symbolPath } from '../format';
import { useServerEvents } from '../notifications';

const ts = (iso: string) => (Date.parse(iso) / 1000) as UTCTimestamp;

function PriceChart({ symbol, timeframe, signals, version }: { symbol: string; timeframe: string; signals: Signal[]; version: number }) {
  const el = useRef<HTMLDivElement>(null);
  const candles = useAsync(() => api<CandleDto[]>(`/api/v1/market/${symbolPath(symbol)}/candles?timeframe=${timeframe}`), [symbol, timeframe, version]);
  const indicators = useAsync(() => api<Record<string, Record<string, number>>>(
    `/api/v1/market/${symbolPath(symbol)}/indicators?timeframe=${timeframe}`), [symbol, timeframe, version]);

  useEffect(() => {
    if (!el.current || !candles.data) return;
    const chart = createChart(el.current, {
      autoSize: true,
      layout: { background: { type: ColorType.Solid, color: 'transparent' }, textColor: '#94a3b8' },
      grid: { vertLines: { color: '#1e293b' }, horzLines: { color: '#1e293b' } },
      timeScale: { timeVisible: true, borderColor: '#334155' },
      rightPriceScale: { borderColor: '#334155' },
    });
    const series = chart.addSeries(CandlestickSeries, {
      upColor: '#10b981', downColor: '#f43f5e', borderVisible: false, wickUpColor: '#10b981', wickDownColor: '#f43f5e',
    });
    series.setData(candles.data.map((c) => ({ time: ts(c.openTime), open: c.open, high: c.high, low: c.low, close: c.close })));
    const colors: Record<string, string> = { 'EMA(20)': '#38bdf8', 'EMA(50)': '#f59e0b' };
    Object.entries(indicators.data ?? {}).filter(([name]) => name in colors).forEach(([name, values]) => {
      const line = chart.addSeries(LineSeries, { color: colors[name], lineWidth: 1, priceLineVisible: false, lastValueVisible: false });
      line.setData(Object.entries(values).map(([t, v]) => ({ time: ts(t), value: v })).sort((a, b) => a.time - b.time));
    });
    const sym = symbol.replace('/', '');
    createSeriesMarkers(series, signals
      .filter((s) => s.symbol.baseAsset + s.symbol.quoteAsset === sym)
      .map((s) => ({
        time: ts(s.evaluation.candleOpenTime),
        position: s.evaluation.type === 'ENTRY_LONG' ? 'belowBar' as const : 'aboveBar' as const,
        color: s.evaluation.type === 'ENTRY_LONG' ? '#10b981' : '#f43f5e',
        shape: s.evaluation.type === 'ENTRY_LONG' ? 'arrowUp' as const : 'arrowDown' as const,
        text: s.evaluation.type === 'ENTRY_LONG' ? 'Entrada' : 'Salida',
      }))
      .sort((a, b) => a.time - b.time));
    chart.timeScale().fitContent();
    return () => chart.remove();
  }, [candles.data, indicators.data, signals, symbol]);

  return (
    <Card title={`${symbol} · ${timeframe}`} actions={<span className="text-xs text-slate-500">EMA20 azul · EMA50 ámbar</span>}>
      <ErrorBox error={candles.error} />
      {candles.data && candles.data.length === 0 && (
        <Empty>Sin velas almacenadas. En TESTNET se sincronizan solas; para backtesting, descarga histórico en «Backtesting».</Empty>
      )}
      <div ref={el} className="h-[420px] w-full" />
    </Card>
  );
}

export default function Dashboard({ status }: { status: SystemStatus | null }) {
  const { lastEvent } = useServerEvents();
  const version = lastEvent?.type === 'CANDLE_CLOSED' || lastEvent?.type === 'SIGNAL' ? Date.parse(lastEvent.at) : 0;
  const signals = useAsync(() => api<Signal[]>('/api/v1/signals?limit=100'), [version]);
  const portfolio = useAsync(() => api<Portfolio>('/api/v1/portfolio'), [version]);
  const symbol = status?.symbols[0] ?? 'BTC/USDT';
  const quote = symbol.split('/')[1];
  const open = portfolio.data?.positions.find((p) => p.status === 'OPEN');

  return (
    <div className="grid gap-4 lg:grid-cols-3">
      <div className="space-y-4 lg:col-span-2">
        <PriceChart symbol={symbol} timeframe={status?.timeframe ?? 'H1'} signals={signals.data ?? []} version={version} />
        <Card title="Señales recientes">
          <ErrorBox error={signals.error} />
          {signals.data?.length === 0 && <Empty>Todavía no hay señales.</Empty>}
          <div className="space-y-2">
            {signals.data?.slice(0, 8).map((s) => (
              <details key={s.id} className="rounded-lg border border-slate-800 p-2">
                <summary className="flex cursor-pointer items-center gap-3 text-sm">
                  <Badge value={s.evaluation.type} />
                  <span>{s.symbol.baseAsset}/{s.symbol.quoteAsset}</span>
                  <span className="text-slate-400">{dt(s.evaluation.candleOpenTime)}</span>
                  <span className="ml-auto tabular-nums">{num(s.evaluation.referencePrice)}</span>
                  <span className="text-xs text-slate-500" title="Puntuación heurística, no es una probabilidad">
                    puntuación {num(s.evaluation.score.value, 0)}/100
                  </span>
                </summary>
                <ul className="mt-2 list-inside list-disc text-sm text-slate-300">
                  {s.evaluation.reasons.map((r) => <li key={r}>{r}</li>)}
                </ul>
                <p className="mt-1 text-xs text-slate-500">{s.strategyId} v{s.strategyVersion}</p>
              </details>
            ))}
          </div>
        </Card>
      </div>
      <div className="space-y-4">
        <Card title="Cuenta">
          <ErrorBox error={portfolio.error ?? portfolio.data?.balancesError} />
          {portfolio.data?.mode === 'BACKTEST' ? (
            <p className="text-sm text-slate-400">Modo BACKTEST: no hay cuenta de exchange ni órdenes. Usa «Backtesting».</p>
          ) : (
            <div className="space-y-3">
              <Table head={['Activo', 'Libre', 'Bloqueado']}>
                {portfolio.data?.balances.map((b) => (
                  <tr key={b.asset}><td className="td">{b.asset}</td><td className="td tabular-nums">{num(b.free, 6)}</td><td className="td tabular-nums">{num(b.locked, 6)}</td></tr>
                ))}
              </Table>
              <Stat label={`Pérdida realizada hoy (${quote})`} value={num(portfolio.data?.lossToday)} tone={portfolio.data?.lossToday ? 'bad' : undefined} />
            </div>
          )}
        </Card>
        <Card title="Posición abierta">
          {!open ? <Empty>Sin posición abierta.</Empty> : (
            <div className="grid grid-cols-2 gap-3">
              <Stat label="Cantidad" value={num(open.entryQuantity, 6)} />
              <Stat label="Precio medio" value={num(open.averageEntryPrice)} />
              <Stat label="Stop loss" value={num(open.stopLoss)} tone="bad" />
              <Stat label="Take profit" value={num(open.takeProfit)} tone="good" />
              <div className="col-span-2">
                {open.protectedByExchange
                  ? <Badge value="Protegida por OCO en el exchange" />
                  : <span className="text-sm text-rose-300">⚠ Sin protección en el exchange</span>}
              </div>
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}
