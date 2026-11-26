import { useState } from 'react';
import { api, post, type RiskStatus } from '../api';
import { useAuth } from '../auth';
import { Badge, Card, ErrorBox, Table, useAsync } from '../components/ui';
import { dt } from '../format';
import { useServerEvents } from '../notifications';

const LABELS: Record<string, string> = {
  maxRiskPerTrade: 'Riesgo máximo por operación', maxDailyLoss: 'Pérdida máxima diaria', maxOpenPositions: 'Posiciones simultáneas',
  minQuoteReserve: 'Reserva mínima', maxPositionNotional: 'Exposición máxima por posición', minRewardToRisk: 'Beneficio/riesgo mínimo',
  feeRate: 'Comisión estimada por lado', slippageRate: 'Deslizamiento estimado', stopLimitBuffer: 'Margen del stop-limit',
  maxSymbolRulesAge: 'Antigüedad máx. de filtros', maxConsecutiveFailures: 'Fallos consecutivos hasta el circuit breaker',
  initialCapital: 'Capital previsto',
};

export default function Risk() {
  const { user } = useAuth();
  const { lastEvent } = useServerEvents();
  const version = lastEvent?.type === 'RISK_ALERT' ? Date.parse(lastEvent.at) : 0;
  const { data, error, reload } = useAsync(() => api<RiskStatus>('/api/v1/risk/status'), [version]);
  const [totp, setTotp] = useState('');
  const [resetError, setResetError] = useState<unknown>(null);
  const breaker = data?.circuitBreaker;

  const reset = async () => {
    setResetError(null);
    try {
      await post('/api/v1/risk/circuit-breaker/reset', { totpCode: totp });
      setTotp('');
      reload();
    } catch (e) {
      setResetError(e);
    }
  };

  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <ErrorBox error={error} />
      <Card title="Límites (solo configurables reiniciando el sistema)">
        <Table head={['Límite', 'Valor']}>
          {Object.entries(data?.limits ?? {}).map(([k, v]) => (
            <tr key={k}><td className="td">{LABELS[k] ?? k}</td><td className="td tabular-nums">{String(v)}</td></tr>
          ))}
        </Table>
      </Card>
      <Card title="Circuit breaker">
        {!breaker ? <p className="text-sm text-slate-400">No aplica en modo BACKTEST.</p> : (
          <div className="space-y-3">
            <div className="flex items-center gap-2"><Badge value={breaker.open ? 'CRITICAL' : 'OPEN'} />
              <span className="text-sm">{breaker.open ? 'ABIERTO: no se crean ni ejecutan operaciones' : 'Cerrado: operativa normal'}</span></div>
            {breaker.reason && <p className="text-sm text-rose-300">{breaker.reason}</p>}
            <p className="text-xs text-slate-500">Fallos consecutivos: {breaker.consecutiveFailures} · último cambio {dt(breaker.changedAt)} por {breaker.changedBy ?? '—'}</p>
            {breaker.open && user?.role === 'TRADER' && (
              <div className="flex items-end gap-2">
                <div><label className="label">Código TOTP</label>
                  <input className="input w-32" maxLength={6} value={totp} onChange={(e) => setTotp(e.target.value.replace(/\D/g, ''))} /></div>
                <button className="btn-danger" disabled={totp.length !== 6} onClick={reset}>Cerrar circuit breaker</button>
              </div>
            )}
            <ErrorBox error={resetError} />
          </div>
        )}
      </Card>
      <div className="lg:col-span-2">
        <Card title="Eventos de riesgo recientes">
          <Table head={['Fecha', 'Severidad', 'Regla', 'Detalle']}>
            {data?.recentEvents.map((e) => (
              <tr key={String(e.id)}>
                <td className="td">{dt(String(e.occurred_at))}</td><td className="td"><Badge value={String(e.severity)} /></td>
                <td className="td">{String(e.rule)}</td><td className="td whitespace-normal text-slate-300">{String(e.detail)}</td>
              </tr>
            ))}
          </Table>
        </Card>
      </div>
    </div>
  );
}
