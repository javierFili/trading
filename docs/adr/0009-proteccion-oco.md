# ADR-0009: Protección de posiciones con OCO en el exchange

- Estado: Aceptada (confirmada por el usuario el 2026-11-19)

## Decisión
Tras ejecutarse la entrada (MARKET), se coloca una OCO de venta: take profit `LIMIT_MAKER` y stop `STOP_LOSS_LIMIT`
con un margen (`risk.stop-limit-buffer`) entre disparo y límite. El dimensionamiento usa el peor precio del stop-limit.
Es parte de la operación aprobada (no requiere segunda aprobación). Si la OCO no puede colocarse → circuit breaker + alerta CRITICAL.
Una salida por señal (EXIT) cancela primero la OCO y solo entonces vende.

## Consecuencias
+ La posición está protegida aunque el sistema caiga. − Un hueco de precio puede saltar el límite del stop-limit
(riesgo residual documentado); la OCO bloquea el saldo base mientras está activa.
