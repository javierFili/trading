import { api, type Order, type Portfolio } from '../api';
import { Badge, Card, Empty, ErrorBox, Table, useAsync } from '../components/ui';
import { dt, num, qty } from '../format';
import { useServerEvents } from '../notifications';

export default function Trades() {
  const { lastEvent } = useServerEvents();
  const version = lastEvent?.type === 'ORDER' || lastEvent?.type === 'POSITION' ? Date.parse(lastEvent.at) : 0;
  const orders = useAsync(() => api<Order[]>('/api/v1/orders?limit=100'), [version]);
  const portfolio = useAsync(() => api<Portfolio>('/api/v1/portfolio'), [version]);
  return (
    <div className="space-y-4">
      <Card title="Posiciones">
        <ErrorBox error={portfolio.error} />
        {portfolio.data?.positions.length === 0 && <Empty>Sin posiciones.</Empty>}
        {!!portfolio.data?.positions.length && (
          <Table head={['Abierta', 'Par', 'Estado', 'Cantidad', 'Entrada', 'Salida', 'Stop', 'TP', 'Comisiones', 'PnL neto', 'Motivo', 'Protegida']}>
            {portfolio.data.positions.map((p) => (
              <tr key={p.id}>
                <td className="td">{dt(p.openedAt)}</td><td className="td">{p.symbol}</td><td className="td"><Badge value={p.status} /></td>
                <td className="td tabular-nums">{qty(p.entryQuantity)}</td><td className="td tabular-nums">{num(p.averageEntryPrice)}</td>
                <td className="td tabular-nums">{num(p.averageExitPrice)}</td><td className="td tabular-nums">{num(p.stopLoss)}</td>
                <td className="td tabular-nums">{num(p.takeProfit)}</td><td className="td tabular-nums">{num(p.fees, 4)}</td>
                <td className={`td tabular-nums ${(p.realizedPnl ?? 0) >= 0 ? 'text-emerald-300' : 'text-rose-300'}`}>{num(p.realizedPnl, 4)}</td>
                <td className="td">{p.exitReason ?? '—'}</td><td className="td">{p.status === 'OPEN' ? (p.protectedByExchange ? 'Sí (OCO)' : '⚠ No') : '—'}</td>
              </tr>
            ))}
          </Table>
        )}
      </Card>
      <Card title="Órdenes">
        <ErrorBox error={orders.error} />
        {orders.data?.length === 0 && <Empty>Sin órdenes.</Empty>}
        {!!orders.data?.length && (
          <Table head={['Creada', 'Rol', 'Lado', 'Tipo', 'Cantidad', 'Precio', 'Stop', 'Estado', 'Ejecutado', 'Total', 'clientOrderId', 'Motivo']}>
            {orders.data.map((o) => (
              <tr key={o.id}>
                <td className="td">{dt(o.createdAt)}</td><td className="td">{o.role}</td><td className="td">{o.side}</td><td className="td">{o.type}</td>
                <td className="td tabular-nums">{qty(o.quantity)}</td><td className="td tabular-nums">{num(o.price)}</td>
                <td className="td tabular-nums">{num(o.stopPrice)}</td><td className="td"><Badge value={o.status} /></td>
                <td className="td tabular-nums">{qty(o.executedQuantity)}</td><td className="td tabular-nums">{num(o.cumulativeQuoteQuantity)}</td>
                <td className="td font-mono text-xs text-slate-400">{o.clientOrderId}</td><td className="td text-slate-400">{o.statusReason ?? ''}</td>
              </tr>
            ))}
          </Table>
        )}
      </Card>
    </div>
  );
}
