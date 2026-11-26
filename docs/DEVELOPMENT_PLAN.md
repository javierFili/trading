# Plan de desarrollo

| Fase | Contenido | Estado |
|---|---|---|
| 0 | Entorno, arquitectura, contratos, reglas ArchUnit, seguridad base, Flyway V1 | ✅ |
| 1 | Persistencia (Flyway V2: 16 tablas), JPA/JDBC, `SymbolRules` (redondeo y filtros), auditoría por eventos | ✅ |
| 2 | Adaptador Binance: firma HMAC, timestamp/recvWindow, rate limits, reintentos de lectura, errores, klines, exchangeInfo, cuenta, órdenes, OCO, WebSocket de velas con reconexión, ingesta | ✅ (probado contra servidor simulado; **sin probar contra el Testnet real**: requiere tus claves) |
| 3 | Indicadores (EMA, SMA, RSI, ATR, MACD, Bollinger), estrategia EMA+RSI+MACD, `SignalEngine` con deduplicación | ✅ |
| 4 | `RiskManager`, dimensionamiento con costes, circuit breaker persistente, eventos de riesgo | ✅ |
| 5 | Motor de backtesting, métricas, splits train/validation/test, checksum y reproducibilidad | ✅ (ejecutado con datos reales) |
| 6 | Propuestas, aprobación (versión + hash + TOTP), expiración, API REST, notificaciones WebSocket, usuarios/JWT | ✅ |
| 7 | Ejecución, revalidación previa, OCO de protección, reconciliación, cartera, PnL | ✅ (probado con exchange simulado) |
| 8 | Habilitar LIVE | ⛔ **No iniciado a propósito.** Requiere autorización expresa (ver abajo) |
| 9 | Frontend React + TypeScript + Tailwind + Lightweight Charts | ✅ |

## Siguientes pasos recomendados (en orden)

1. **Validación en TESTNET real** con tus claves de testnet: verificar firma, OCO (`/api/v3/orderList/oco`),
   formatos de respuesta y reconciliación contra el servidor real. Campaña mínima: 2–4 semanas sin discrepancias.
2. **Revisar la estrategia**: con los costes y límites actuales, el backtest real (dic-2023 a oct-2025) produce
   muy pocas operaciones (ver BACKTESTING.md). Decidir con criterio (no optimizando sobre el tramo de prueba)
   si se ajustan multiplicadores ATR, el mínimo beneficio/riesgo o se prueban otras estrategias.
3. User data stream privado (WebSocket) para reducir la latencia de reconciliación (hoy: REST cada 15 s).
4. Fase 8 (LIVE) solo tras 1–2 y con la lista de [SECURITY.md](SECURITY.md#desbloqueo-de-live-contrato).

## Fuera de alcance actual

Futures/Margin (prohibidos), ejecución automática sin aprobación (no implementada), ML/IA en el camino de ejecución,
multiusuario con cuentas separadas, Redis.
