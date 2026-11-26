// Thin typed client for the backend REST API. The JWT lives in sessionStorage (cleared when the tab closes).
const TOKEN_KEY = 'cts.token';

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string) {
    super(message);
  }
}

export function getToken(): string | null {
  try {
    return sessionStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setToken(token: string | null) {
  try {
    if (token) sessionStorage.setItem(TOKEN_KEY, token);
    else sessionStorage.removeItem(TOKEN_KEY);
  } catch {
    /* storage unavailable: token kept only in memory by the caller */
  }
}

let onUnauthorized: () => void = () => {};
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  const token = getToken();
  if (token) headers.set('Authorization', `Bearer ${token}`);
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  const res = await fetch(path, { ...init, headers });
  if (res.status === 401 && !path.endsWith('/auth/login')) onUnauthorized();
  if (!res.ok) {
    let code = String(res.status);
    let message = res.statusText;
    try {
      const body = await res.json();
      code = body.error ?? code;
      message = body.message ?? body.error ?? message;
    } catch {
      /* no JSON body */
    }
    throw new ApiError(res.status, code, message);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export const post = <T>(path: string, body?: unknown) =>
  api<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) });

// ------------------------------------------------------------------ types
export type Role = 'VIEWER' | 'TRADER';
export interface User { id: string; username: string; role: Role; totpEnabled: boolean }
export interface LoginResponse { accessToken: string; expiresAt: string; user: User }
export interface SystemStatus {
  mode: 'BACKTEST' | 'TESTNET' | 'LIVE'; liveAvailable: boolean; symbols: string[]; timeframe: string;
  approvalTtl: string; maxPriceDeviation: number; serverTime: string;
}
export interface CandleDto { openTime: string; open: number; high: number; low: number; close: number; volume: number }
export interface Evaluation {
  type: 'ENTRY_LONG' | 'EXIT_LONG' | 'NO_ACTION'; candleOpenTime: string; referencePrice: number;
  score: { value: number }; indicatorValues: Record<string, number>; reasons: string[];
  invalidationConditions: string[]; suggestedStopLoss: number | null; suggestedTakeProfit: number | null;
}
export interface Signal {
  id: string; symbol: { baseAsset: string; quoteAsset: string }; timeframe: string; strategyId: string;
  strategyVersion: string; evaluation: Evaluation; createdAt: string;
}
export type ProposalStatus = 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'EXPIRED' | 'EXECUTING'
  | 'PARTIALLY_FILLED' | 'FILLED' | 'FAILED' | 'CANCELLED';
export interface Proposal {
  id: string; signalId: string | null; symbol: string; positionId: string | null; purpose: 'ENTRY' | 'EXIT';
  side: 'BUY' | 'SELL'; referencePrice: number; stopLoss: number | null; takeProfit: number | null;
  quantity: number; notional: number; riskAmount: number | null; estimatedFees: number; rewardToRisk: number | null;
  justification: string[]; status: ProposalStatus; statusReason: string | null; contentHash: string;
  version: number; expiresAt: string; mode: string; createdAt: string; updatedAt: string;
}
export interface Order {
  id: string; proposalId: string | null; positionId: string | null; symbol: string; role: string;
  clientOrderId: string; exchangeOrderId: string | null; side: string; type: string; quantity: number;
  price: number | null; stopPrice: number | null; status: string; executedQuantity: number;
  cumulativeQuoteQuantity: number; statusReason: string | null; createdAt: string; lastReconciledAt: string | null;
}
export interface Position {
  id: string; symbol: string; status: 'OPEN' | 'CLOSED'; entryQuantity: number; averageEntryPrice: number;
  entryQuote: number; averageExitPrice: number | null; exitQuote: number | null; stopLoss: number;
  takeProfit: number; fees: number; realizedPnl: number | null; exitReason: string | null;
  protectedByExchange: boolean; openedAt: string; closedAt: string | null;
}
export interface Balance { asset: string; free: number; locked: number }
export interface Portfolio { mode: string; balances: Balance[]; balancesError: string | null; positions: Position[]; lossToday: number }
export interface BreakerState { open: boolean; reason: string | null; consecutiveFailures: number; changedAt: string; changedBy: string | null }
export interface RiskStatus { limits: Record<string, number | string>; circuitBreaker: BreakerState | null; recentEvents: Record<string, unknown>[] }
export interface Metrics {
  trades: number; wins: number; winRate: number | null; netProfit: number; returnPct: number; profitFactor: number | null;
  maxDrawdown: number; maxDrawdownPct: number; sharpePerTrade: number | null; averageTrade: number | null;
  realizedRewardToRisk: number | null; totalFees: number; finalEquity: number; statisticallySignificant: boolean;
}
export interface BacktestRun {
  id: string; groupId: string; strategyCode: string; strategyVersion: string; parameters: Record<string, string>;
  symbol: string; timeframe: string; periodStart: string; periodEnd: string; split: string; status: string;
  metrics: Metrics | null; dataChecksum: string | null; candleCount: number | null; dataGaps: number | null;
  error: string | null; createdBy: string; createdAt: string;
}
export interface BacktestTrade {
  entryTime: string; entryPrice: number; exitTime: string; exitPrice: number; quantity: number; fees: number;
  pnl: number; exitReason: string;
}
