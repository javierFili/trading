# Reglas de trading

> Valores confirmados por el usuario el 2026-11-19 (aceptación de las propuestas de la Fase 0).
> Todos son configurables en `application.yml` (`risk.*`, `trading.*`), solo cambian reiniciando y se auditan
> en cada arranque (`RISK_LIMITS_LOADED`).

## Reglas invariables (en código, no configurables)

1. Solo **Spot**, solo **largos**. No existen tipos de orden ni puertos para Futures/Margin; `TradeProposal` rechaza ENTRY≠BUY y EXIT≠SELL.
2. **Ninguna orden sin aprobación explícita**: rol TRADER + JWT válido + **código TOTP de un solo uso** + versión y hash SHA-256 exactos de la propuesta mostrada. No hay ejecución automática.
3. La aprobación **expira** con la propuesta (`trading.approval-ttl`). Una propuesta aprobada que no empieza a ejecutarse antes de expirar pasa a EXPIRED.
4. **Revalidación antes de ejecutar**: circuit breaker cerrado, precio dentro de ±`max-price-deviation` respecto al de referencia, riesgo recalculado con saldo/posiciones/filtros frescos. Si falla → CANCELLED y hace falta una **nueva** propuesta. La cantidad solo puede reducirse, nunca aumentar.
5. Los límites de riesgo no pueden ser modificados por estrategias, señales ni IA (`risk` no depende de ellas, verificado por ArchUnit).
6. `BigDecimal` en todos los cálculos; cantidades redondeadas **hacia abajo** al `stepSize`.
7. La puntuación de confianza es heurística, **no una probabilidad**.
8. Toda posición abierta se protege **en el exchange** con una OCO (take profit LIMIT_MAKER + STOP_LOSS_LIMIT). Si no se puede proteger, se abre el circuit breaker y se alerta como CRITICAL.

## Límites

| Parámetro | Valor | Propiedad |
|---|---|---|
| Capital previsto | 100 USDT | `risk.initial-capital` |
| Riesgo máximo por operación | 0,50 USDT | `risk.max-risk-per-trade` |
| Pérdida máxima diaria (día UTC, PnL realizado) | 2,00 USDT | `risk.max-daily-loss` |
| Posiciones simultáneas | 1 | `risk.max-open-positions` |
| Reserva mínima | 20 USDT | `risk.min-quote-reserve` |
| Exposición máxima por posición | 80 USDT | `risk.max-position-notional` |
| Beneficio/riesgo mínimo **neto de costes** | 1,5 | `risk.min-reward-to-risk` |
| Comisión estimada por lado | 0,1 % | `risk.fee-rate` |
| Deslizamiento estimado (órdenes MARKET) | 0,05 % | `risk.slippage-rate` |
| Margen del stop-limit bajo el disparo | 0,2 % | `risk.stop-limit-buffer` |
| Antigüedad máxima de los filtros del exchange | 24 h | `risk.max-symbol-rules-age` |
| Fallos de ejecución consecutivos hasta abrir el circuit breaker | 3 | `risk.max-consecutive-failures` |
| Validez de la propuesta/aprobación | 10 min | `trading.approval-ttl` |
| Desviación de precio que invalida la propuesta | 0,3 % | `trading.max-price-deviation` |
| Apalancamiento | ninguno | — |

## Dimensionamiento (`DefaultRiskManager`)

```
e            = entrada × (1 + deslizamiento)               precio esperado de la compra MARKET
x            = stop × (1 − margenStopLimit)                peor precio de venta del stop-limit
pérdida/u    = (e − x) + e·comisión + x·comisión
presupuesto  = min(riesgoMáx, pérdidaDiariaMáx − pérdidaHoy)
cantidad     = floor_step( min(presupuesto / pérdida/u,  exposiciónMáx / e,  (libre − reserva) / (e·(1+comisión))) )
benef/riesgo = (tp − e − e·comisión − tp·comisión) / pérdida/u
```

Se rechaza (con **todas** las violaciones, registradas como `risk_event`) si: circuit breaker abierto, posiciones ≥ máximo,
pérdida diaria alcanzada, saldo ≤ reserva, beneficio/riesgo < mínimo, filtros caducados, cantidad 0 o filtros del exchange
incumplidos (`LOT_SIZE`, `PRICE_FILTER`, `NOTIONAL`). Si el tamaño queda por debajo del mínimo del exchange **se rechaza, nunca se agranda**.

## Estrategia EMA + RSI + MACD (experimental)

Evaluada al **cierre** de cada vela de 1 h sobre las últimas 300 velas cerradas.

- **Entrada (ENTRY_LONG)**: EMA20 > EMA50 **y** el histograma MACD(12,26,9) cruza por encima de 0 en la última vela **y** 50 ≤ RSI14 ≤ 70.
  El volumen ≥ media de 20 velas solo sube la puntuación.
- **Stop sugerido** = cierre − 1,5·ATR14; **take profit** = cierre + 3·ATR14.
- **Salida (EXIT_LONG)**: EMA20 ≤ EMA50, o el histograma MACD cruza por debajo de 0. Solo genera propuesta si hay posición abierta.
- **Puntuación** (heurística): tendencia 40 + cruce MACD 30 + RSI en rango 20 + volumen 10.
- La versión de la estrategia incluye un hash de sus parámetros: cambiarlos genera una versión nueva y trazable.

## Circuit breaker

Se abre automáticamente por: pérdida diaria ≥ límite; N fallos o resultados desconocidos consecutivos; posición sin protección
(OCO rechazada, salida fallida, protección terminada sin ejecutarse). Bloquea la creación **y** la ejecución de propuestas.
Solo se cierra manualmente (TRADER + TOTP), con auditoría.
