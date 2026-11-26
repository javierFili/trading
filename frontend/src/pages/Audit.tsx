import { api } from '../api';
import { useAuth } from '../auth';
import { Card, ErrorBox, Table, useAsync } from '../components/ui';
import { dt } from '../format';

export default function Audit() {
  const { user } = useAuth();
  const { data, error } = useAsync(() => user?.role === 'TRADER'
    ? api<Record<string, unknown>[]>('/api/v1/audit?limit=200') : Promise.resolve([]), [user?.role]);
  if (user?.role !== 'TRADER') return <p className="text-sm text-slate-400">La auditoría solo es visible para usuarios TRADER.</p>;
  return (
    <Card title="Registro de auditoría (solo inserción)">
      <ErrorBox error={error} />
      <Table head={['Fecha', 'Actor', 'Acción', 'Entidad', 'Modo', 'Detalles']}>
        {data?.map((a) => (
          <tr key={String(a.id)}>
            <td className="td">{dt(String(a.occurred_at))}</td><td className="td">{String(a.actor)}</td>
            <td className="td font-medium">{String(a.action)}</td>
            <td className="td text-slate-400">{a.entity_type ? `${a.entity_type}:${String(a.entity_id ?? '').slice(0, 8)}` : ''}</td>
            <td className="td">{String(a.trading_mode)}</td>
            <td className="td max-w-md truncate font-mono text-xs text-slate-400" title={String(a.details)}>{String(a.details)}</td>
          </tr>
        ))}
      </Table>
    </Card>
  );
}
