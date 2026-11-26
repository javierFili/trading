import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { getToken } from './api';

export interface ServerEvent { type: string; at: string; payload: Record<string, unknown> }
interface Toast { id: number; text: string; tone: 'info' | 'warn' | 'critical' }

const EventContext = createContext<{ lastEvent: ServerEvent | null; connected: boolean }>({ lastEvent: null, connected: false });

function describe(e: ServerEvent): Toast['text'] {
  const p = e.payload;
  switch (e.type) {
    case 'PROPOSAL_CREATED': return `Nueva propuesta ${p.purpose} ${p.symbol} pendiente de aprobación`;
    case 'PROPOSAL_STATUS': return `Propuesta → ${p.status}${p.reason ? `: ${p.reason}` : ''}`;
    case 'RISK_ALERT': return `Riesgo [${p.severity}] ${p.rule}: ${p.detail}`;
    case 'POSITION': return `Posición ${p.type} ${p.symbol}${p.realizedPnl != null ? ` (PnL ${p.realizedPnl})` : ''}`;
    case 'ORDER': return `Orden ${p.role} ${p.symbol} → ${p.status}`;
    case 'SIGNAL': return `Señal ${p.type} ${p.symbol} (puntuación ${p.score})`;
    default: return e.type;
  }
}

/** Server → client notifications over an authenticated WebSocket, with automatic reconnection. */
export function NotificationProvider({ children }: { children: ReactNode }) {
  const [lastEvent, setLastEvent] = useState<ServerEvent | null>(null);
  const [connected, setConnected] = useState(false);
  const [toasts, setToasts] = useState<Toast[]>([]);
  const counter = useRef(0);

  useEffect(() => {
    let socket: WebSocket | null = null;
    let retry = 1000;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let stopped = false;

    const connect = () => {
      const token = getToken();
      if (!token || stopped) return;
      const proto = location.protocol === 'https:' ? 'wss' : 'ws';
      socket = new WebSocket(`${proto}://${location.host}/ws?access_token=${encodeURIComponent(token)}`);
      socket.onopen = () => {
        setConnected(true);
        retry = 1000;
      };
      socket.onmessage = (msg) => {
        const event = JSON.parse(msg.data) as ServerEvent;
        setLastEvent(event);
        if (event.type === 'CANDLE_CLOSED') return;
        const severity = String(event.payload.severity ?? '');
        const tone: Toast['tone'] = severity === 'CRITICAL' ? 'critical' : event.type === 'RISK_ALERT' ? 'warn' : 'info';
        const id = ++counter.current;
        setToasts((t) => [...t.slice(-4), { id, text: describe(event), tone }]);
        if (tone !== 'critical') setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 8000);
      };
      socket.onclose = () => {
        setConnected(false);
        if (!stopped) timer = setTimeout(connect, (retry = Math.min(retry * 2, 30000)));
      };
    };
    connect();
    return () => {
      stopped = true;
      clearTimeout(timer);
      socket?.close();
    };
  }, []);

  const tones = { info: 'border-slate-700 bg-slate-900', warn: 'border-amber-700 bg-amber-950', critical: 'border-rose-600 bg-rose-950' };
  return (
    <EventContext.Provider value={{ lastEvent, connected }}>
      {children}
      <div className="fixed right-4 bottom-4 z-50 flex w-96 max-w-[calc(100vw-2rem)] flex-col gap-2">
        {toasts.map((t) => (
          <div key={t.id} className={`rounded-lg border p-3 text-sm shadow-lg ${tones[t.tone]}`}>
            <div className="flex items-start justify-between gap-2">
              <span>{t.text}</span>
              <button className="text-slate-400 hover:text-white" onClick={() => setToasts((x) => x.filter((y) => y.id !== t.id))}>✕</button>
            </div>
          </div>
        ))}
      </div>
    </EventContext.Provider>
  );
}

export const useServerEvents = () => useContext(EventContext);
