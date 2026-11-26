import { useState, type FormEvent } from 'react';
import { useAuth } from '../auth';
import { ErrorBox } from '../components/ui';

export default function Login() {
  const { login } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(username, password);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <form onSubmit={submit} className="card w-full max-w-sm space-y-4">
        <h1 className="text-lg font-semibold">Crypto Trading System</h1>
        <p className="text-sm text-slate-400">Plataforma semiautomática: ninguna orden se ejecuta sin tu aprobación.</p>
        <div>
          <label className="label" htmlFor="u">Usuario</label>
          <input id="u" className="input" autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} />
        </div>
        <div>
          <label className="label" htmlFor="p">Contraseña</label>
          <input id="p" type="password" className="input" autoComplete="current-password" value={password}
                 onChange={(e) => setPassword(e.target.value)} />
        </div>
        <ErrorBox error={error} />
        <button className="btn-primary w-full" disabled={busy || !username || !password}>Entrar</button>
      </form>
    </div>
  );
}
