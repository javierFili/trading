# Backtesting

Implementación: `backtesting.domain.BacktestEngine` (puro, sin exchange) + `BacktestService` (asíncrono, persistente).

## Principios aplicados

- **Mismo código** de indicadores, estrategia y `RiskManager` (con los mismos límites y costes) que en vivo.
- **Sin look-ahead**: en cada vela *t* la estrategia recibe un `CandleSeries` con `asOf` = cierre de *t*; la construcción
  falla si entra una vela no cerrada. Las decisiones se ejecutan en la **apertura de *t+1*** (verificado por test).
- Stop y take profit intravela; si una vela toca ambos se asume **el stop primero** (conservador); un hueco por debajo
  del stop se ejecuta a la apertura. Comisiones en entrada y salida; deslizamiento en órdenes de mercado y stops.
- **Splits cronológicos** TRAIN 60 % / VALIDATION 20 % / TEST 20 %; los datos anteriores a cada tramo solo se usan para
  calentar indicadores. El tramo TEST debe usarse una sola vez por versión de estrategia.
- **Reproducible**: cada ejecución guarda parámetros, versión de estrategia, límites, costes, checksum SHA-256 de las
  velas, número de velas y **huecos detectados**.
- Supervivencia: con un único par (BTC/USDT, listado durante todo el periodo) no aplica; al añadir pares, el universo
  debe definirse con los pares listados en cada fecha.

## Métricas

Beneficio neto, rentabilidad, win rate, profit factor, máximo drawdown (absoluto y %), Sharpe **por operación**
(desviación muestral, sin anualizar), operación media, ganancia media / pérdida media, comisiones, capital final.
Con menos de 30 operaciones se marca como **no estadísticamente significativo**.

## Validación de indicadores

EMA, RSI, ATR, MACD y Bollinger se comparan (tolerancia 1e-25) con una implementación independiente en Python
(`Decimal`, 50 dígitos) sobre una serie determinista, además de casos calculados a mano.

## Resultado real (2026-11-19)

Datos: BTC/USDT 1 h descargados de `data-api.binance.vision`, 2023-11-01 → 2025-11-01 (17 544 velas, 0 huecos).
Estrategia EMA_RSI_MACD con parámetros por defecto y los límites de riesgo confirmados.

| Tramo | Periodo | Operaciones | Neto (USDT) | Observación |
|---|---|---|---|---|
| TRAIN | 2023-12-01 → 2025-01-24 | 2 | −0,40 | ambas perdedoras |
| VALIDATION | 2025-01-24 → 2025-06-13 | 0 | 0 | — |
| TEST | 2025-06-13 → 2025-11-01 | 0 | 0 | — |

**Interpretación**: con stop a 1,5·ATR y objetivo a 3·ATR, el beneficio/riesgo bruto es 2; tras restar comisiones
(0,1 % por lado), deslizamiento y el margen del stop-limit (0,2 %), el neto suele quedar **por debajo de 1,5** en
velas de 1 h de BTC (ATR ≈ 0,5–1 % del precio), y el `RiskManager` rechaza casi todas las señales. El sistema se comporta
como debe (no opera cuando los costes destruyen la ventaja), pero **la estrategia, tal como está, no es operable**:
no hay base estadística ni para afirmar ni para negar rentabilidad. Cualquier ajuste debe decidirse con criterio y
evaluarse en TRAIN/VALIDATION, nunca mirando TEST.
