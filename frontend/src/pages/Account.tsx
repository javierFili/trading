import { useState } from 'react';
import { post } from '../api';
import { useAuth } from '../auth';
import { Card, ErrorBox } from '../components/ui';

export default function Account() {
  const { user, refresh } = useAuth();
  const [setup, setSetup] = useState<{ secret: string; otpauthUri: string } | null>(null);
  const [code, setCode] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [done, setDone] = useState(false);

  const begin = async () => {
    setError(null);
    try {
      setSetup(await post('/api/v1/auth/totp/setup'));
    } catch (e) {
      setError(e);
    }
  };
  const confirm = async () => {
    setError(null);
    try {
      await post('/api/v1/auth/totp/confirm', { code });
      setDone(true);
      setSetup(null);
      await refresh();
    } catch (e) {
      setError(e);
    }
  };

  return (
    <div className="max-w-2xl space-y-4">
      <Card title="Usuario">
        <p className="text-sm">{user?.username} · rol {user?.role}</p>
      </Card>
      <Card title="Segundo factor (TOTP)">
        {user?.totpEnabled || done ? (
          <p className="text-sm text-emerald-300">TOTP activado. Cada código solo puede usarse una vez.</p>
        ) : !setup ? (
          <div className="space-y-3">
            <p className="text-sm text-slate-300">Necesario para aprobar propuestas y cerrar el circuit breaker. Usa una app como Aegis, 2FAS o Google Authenticator.</p>
            <button className="btn-primary" onClick={begin}>Configurar TOTP</button>
          </div>
        ) : (
          <div className="space-y-3">
            <p className="text-sm text-slate-300">Añade esta clave en tu app de autenticación (introducción manual, tipo «basada en tiempo»):</p>
            <code className="block rounded bg-slate-950 p-2 text-center text-lg tracking-widest">{setup.secret.match(/.{1,4}/g)?.join(' ')}</code>
            <p className="break-all text-xs text-slate-500">{setup.otpauthUri}</p>
            <p className="text-xs text-amber-300">Esta clave solo se muestra ahora. No la compartas ni la guardes en texto plano.</p>
            <div className="flex items-end gap-2">
              <div><label className="label">Código de la app</label>
                <input className="input w-32 tracking-widest" maxLength={6} value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))} /></div>
              <button className="btn-primary" disabled={code.length !== 6} onClick={confirm}>Confirmar</button>
            </div>
          </div>
        )}
        <div className="mt-3"><ErrorBox error={error} /></div>
      </Card>
    </div>
  );
}
