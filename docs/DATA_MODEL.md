# Modelo de datos

Migraciones: `V1__audit_log.sql`, `V2__core_schema.sql`. PostgreSQL 16, `TIMESTAMPTZ` UTC, importes `NUMERIC(38,18)`,
enumerados como `VARCHAR` + `CHECK`. El esquema lo gobierna solo Flyway (`ddl-auto=validate`).

| Tabla | Propósito | Invariantes en BD |
|---|---|---|
| `app_user` | Usuarios, hash Argon2id, TOTP cifrado, bloqueo | `username` único; rol ∈ {VIEWER, TRADER} |
| `trading_account` | Cuenta por modo (sin credenciales) | sembradas TESTNET y LIVE |
| `trading_pair` | Par y filtros del exchange cacheados | `symbol_code` único |
| `market_candle` | Velas | único (par, tf, apertura); OHLC coherente; precios > 0 |
| `trading_strategy` | Estrategia + versión + parámetros (JSONB) | único (código, versión) |
| `market_indicator` | Indicadores calculados por vela/estrategia | único por (par, tf, vela, estrategia, nombre) |
| `strategy_signal` | Señales con justificación | **único (estrategia, par, tf, vela)** → sin duplicados |
| `trade_proposal` | Propuestas, hash, expiración | `@Version`; estado ∈ máquina de estados |
| `trade_approval` | Decisión única por propuesta, ligada a versión + hash | `proposal_id` único |
| `trading_order` | Órdenes (entrada, salida, TP, SL) | **único (cuenta, client_order_id)** |
| `trade_execution` | Fills reales con comisión | único (orden, trade del exchange) |
| `trading_position` | Posiciones y PnL realizado | **índice único parcial: una posición OPEN por cuenta y par** |
| `portfolio_snapshot` | Valoración horaria | — |
| `risk_event` | Rechazos, fallos, cambios del circuit breaker | — |
| `circuit_breaker` | Estado persistente por cuenta | — |
| `backtest_run` / `backtest_trade` | Backtests reproducibles | checksum, parámetros, límites |
| `audit_log` | Auditoría | **append-only (trigger)** |

Persistencia: JPA para agregados con concurrencia (usuarios, propuestas, órdenes, posiciones, pares; bloqueo optimista)
y JDBC para escrituras masivas o append-only (velas, señales, auditoría, backtests, fills) — ver ADR-0008.
