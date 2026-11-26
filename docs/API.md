# API REST (v1)

Base `/api/v1`. JSON. Autenticación `Authorization: Bearer <jwt>` salvo login. Errores: `{"error": CODE, "message": …}`
con 400 (petición inválida), 401, 403 (rol o 2FA), 404, 409 (conflicto de estado/versión/hash), 410 (expirada), 422.
Especificación OpenAPI generada en `/v3/api-docs` y `/swagger-ui.html`. Importes y precios son decimales exactos.

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| POST | `/auth/login` | — | `{username, password}` → `{accessToken, expiresAt, user}` |
| GET | `/auth/me` | autenticado | Usuario actual |
| POST | `/auth/totp/setup` | autenticado | Inicia el alta TOTP; devuelve `{secret, otpauthUri}` una vez |
| POST | `/auth/totp/confirm` | autenticado | `{code}` activa el TOTP |
| GET | `/system/status` | autenticado | Modo, símbolos, temporalidad, TTL, desviación máxima, LIVE disponible |
| GET | `/market/{BTC-USDT}/candles?timeframe=&from=&to=` | autenticado | Velas almacenadas (máx. 5000) |
| GET | `/market/{BTC-USDT}/indicators?timeframe=&from=&to=` | autenticado | Indicadores registrados por el motor de señales |
| POST | `/market/{BTC-USDT}/import?timeframe=` (multipart `file`) | TRADER | Importa CSV de data.binance.vision |
| POST | `/market/{BTC-USDT}/download` | TRADER | `{timeframe, from, to}` descarga histórico público (máx. 3 años) |
| GET | `/signals?limit=` | autenticado | Señales con indicadores, razones e invalidación |
| GET | `/proposals?status=&limit=` | autenticado | Propuestas |
| GET | `/proposals/{id}` | autenticado | Detalle (incluye `version` y `contentHash`) |
| POST | `/proposals/{id}/approve` | TRADER | `{version, contentHash, totpCode}`. 409 si cambió, 410 si expiró, 403 sin TOTP válido |
| POST | `/proposals/{id}/reject` | TRADER | `{version, contentHash, reason?}` |
| GET | `/orders?limit=` | autenticado | Órdenes y su estado de reconciliación |
| GET | `/portfolio` | autenticado | Saldos, posiciones, pérdida realizada hoy |
| GET | `/risk/status` | autenticado | Límites, circuit breaker, eventos de riesgo |
| POST | `/risk/circuit-breaker/reset` | TRADER | `{totpCode}` cierra el circuit breaker |
| POST | `/backtests` | TRADER | `{symbol: "BTC/USDT", timeframe, from, to, strategyCode: "EMA_RSI_MACD", parameters?, split?, initialCapital?}` → 202 `{groupId}` |
| GET | `/backtests?limit=` · `/backtests/groups/{groupId}` · `/backtests/{runId}` · `/backtests/{runId}/trades` | autenticado | Resultados |
| GET | `/audit?limit=` | TRADER | Registro de auditoría |

## WebSocket `/ws?access_token=<jwt>`

Solo servidor → cliente; cada mensaje: `{"type", "at", "payload"}` con `type` ∈ `PROPOSAL_CREATED`, `PROPOSAL_STATUS`,
`RISK_ALERT`, `POSITION`, `ORDER`, `SIGNAL`, `CANDLE_CLOSED`. Se envía solo tras el commit de la transacción.
Las acciones siempre se hacen por REST.
