import { StrictMode, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import { AuthProvider, useAuth } from './auth';
import { NotificationProvider, useServerEvents } from './notifications';
import { api, type SystemStatus } from './api';
import { Badge } from './components/ui';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Proposals from './pages/Proposals';
import Trades from './pages/Trades';
import Backtests from './pages/Backtests';
import Risk from './pages/Risk';
import Audit from './pages/Audit';
import Account from './pages/Account';

const PAGES = {
  dashboard: { label: 'Mercado', component: Dashboard },
  proposals: { label: 'Propuestas', component: Proposals },
  trades: { label: 'Órdenes y posiciones', component: Trades },
  backtests: { label: 'Backtesting', component: Backtests },
  risk: { label: 'Riesgo', component: Risk },
  audit: { label: 'Auditoría', component: Audit },
  account: { label: 'Cuenta', component: Account },
} as const;
type PageKey = keyof typeof PAGES;

function currentPage(): PageKey {
  const key = location.hash.replace('#/', '') as PageKey;
  return key in PAGES ? key : 'dashboard';
}

function Shell() {
  const { user, logout } = useAuth();
  const { connected } = useServerEvents();
  const [page, setPage] = useState<PageKey>(currentPage());
  const [status, setStatus] = useState<SystemStatus | null>(null);

  useEffect(() => {
    const onHash = () => setPage(currentPage());
    window.addEventListener('hashchange', onHash);
    api<SystemStatus>('/api/v1/system/status').then(setStatus).catch(() => setStatus(null));
    return () => window.removeEventListener('hashchange', onHash);
  }, []);

  const Page = PAGES[page].component;
  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-40 border-b border-slate-800 bg-slate-950/90 backdrop-blur">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3">
          <span className="font-semibold tracking-tight">Crypto Trading System</span>
          {status && <Badge value={status.mode} />}
          <nav className="flex flex-wrap gap-1">
            {(Object.keys(PAGES) as PageKey[]).map((k) => (
              <a key={k} href={`#/${k}`}
                 className={`rounded-md px-2.5 py-1 text-sm ${k === page ? 'bg-slate-800 text-white' : 'text-slate-400 hover:text-white'}`}>
                {PAGES[k].label}
              </a>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-3 text-sm text-slate-400">
            <span title="Notificaciones en tiempo real" className={connected ? 'text-emerald-400' : 'text-slate-600'}>●</span>
            <span>{user?.username} ({user?.role})</span>
            <button className="btn-ghost" onClick={logout}>Salir</button>
          </div>
        </div>
      </header>
      {user && !user.totpEnabled && user.role === 'TRADER' && (
        <div className="border-b border-amber-800 bg-amber-950/60 px-4 py-2 text-center text-sm text-amber-200">
          Activa el segundo factor (TOTP) en <a className="underline" href="#/account">Cuenta</a>: es obligatorio para aprobar propuestas.
        </div>
      )}
      <main className="mx-auto max-w-7xl px-4 py-6">
        <Page status={status} />
      </main>
    </div>
  );
}

function App() {
  const { user } = useAuth();
  if (!user) return <Login />;
  return (
    <NotificationProvider>
      <Shell />
    </NotificationProvider>
  );
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider>
      <App />
    </AuthProvider>
  </StrictMode>,
);
