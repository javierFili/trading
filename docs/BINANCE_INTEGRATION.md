# Integración con Binance Spot

> Fuente: https://github.com/binance/binance-spot-api-docs. Implementación en `exchange.infrastructure.binance`.
> **Probado contra un servidor HTTP simulado** (incluido el ejemplo de firma HMAC de la documentación oficial) y,
> para datos públicos, contra `data-api.binance.vision` real. **No probado aún contra el Testnet real** (requiere claves).

## Puertos y endpoints

| Puerto | Endpoint | Peso |
|---|---|---|
| `MarketDataClient.serverTime` | `GET /api/v3/time` | 1 |
| `latestPrice` | `GET /api/v3/ticker/price` | 2 |
| `candles` | `GET /api/v3/klines` (máx. 1000, `endTime` inclusivo = fin − 1 ms) | 2 |
| `symbolRules` | `GET /api/v3/exchangeInfo?symbol=` | 2 |
| `AccountClient.balances` | `GET /api/v3/account?omitZeroBalances=true` | 20 |
| `findOrder` | `GET /api/v3/order?origClientOrderId=` (`-2013` → no existe) | 4 |
| `fills` | `GET /api/v3/myTrades?orderId=` (comisiones reales) | 5 |
| `OrderClient.submit` | `POST /api/v3/order` (`newOrderRespType=FULL`) | 1 |
| `submitOco` | `POST /api/v3/orderList/oco` (above `LIMIT_MAKER`, below `STOP_LOSS_LIMIT` GTC) | 1 |
| `cancelOrderList` | `DELETE /api/v3/orderList` | 1 |
| `MarketDataStream` | `wss://…/ws/<symbol>@kline_<interval>` (solo velas con `x=true`) | — |
| LIVE: permisos de la clave | `GET /sapi/v1/account/apiRestrictions` | 1 |

## Entornos (fijos en código)

| Modo | REST | WebSocket |
|---|---|---|
| TESTNET | `https://testnet.binance.vision` | `wss://stream.testnet.binance.vision` |
| LIVE (bloqueado) | `https://api.binance.com` | `wss://stream.binance.com:9443` |
| Histórico (todos los modos) | `https://data-api.binance.vision` (solo datos de mercado, no admite órdenes) | — |

## Firma, tiempo y límites

- HMAC-SHA256 sobre la query exacta enviada; `X-MBX-APIKEY`; `recvWindow=5000`; `timestamp` corregido con el desfase
  medido contra `/api/v3/time`. Error `-1021` → resincroniza y reintenta una vez.
- Presupuesto local de peso por minuto (3000, por debajo del límite publicado) sincronizado con `X-MBX-USED-WEIGHT-1M`.
  HTTP 429/418 → bloqueo local durante `Retry-After` (o 1/5 min).
- Lecturas: hasta 3 intentos con backoff exponencial + jitter ante 5xx/red. **Órdenes: un solo intento.**
- Timeouts: conexión 5 s, lectura 10 s.

## Clasificación de resultados de órdenes (ADR-0006)

| Respuesta | Resultado | Acción |
|---|---|---|
| 2xx | Accepted | aplicar estado; si es final, registrar fills |
| 4xx con código (p. ej. `-2010`) / 429 / 418 / presupuesto local | Rejected | propuesta FAILED, fallo contado para el circuit breaker |
| 5xx, `-1007`, error de red/timeout, 2xx ilegible | **Unknown** | orden UNKNOWN; la reconciliación la busca por `clientOrderId` |

`clientOrderId` = `cts` + rol + UUID (36 caracteres), persistido **antes** de enviar y único en BD.
La reconciliación (cada 15 s) declara inexistente una orden solo si el exchange responde `-2013` y han pasado 2 minutos.

## Limitaciones conocidas

- **User data stream** privado no implementado: el estado de las órdenes se sincroniza por REST cada 15 s (suficiente para velas de 1 h).
- Solo claves HMAC (no Ed25519/RSA).
- Comisiones pagadas en BNB se estiman con `risk.fee-rate` (marcado como `ORDER_FILLED_FEES_ESTIMATED`).
- Si el precio ya supera el take profit al colocar la OCO, Binance rechazará el `LIMIT_MAKER`: la posición queda sin protección, se abre el circuit breaker y se alerta (requiere acción manual).
