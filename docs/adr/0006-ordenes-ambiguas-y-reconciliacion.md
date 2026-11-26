# ADR-0006: Órdenes ambiguas, idempotencia y reconciliación

- Estado: Aceptada (Fase 0)

## Contexto
Binance documenta que ante timeouts o errores 5xx el estado de la orden es desconocido y puede haberse ejecutado.

## Decisión
- `OrderClient` no lanza excepciones para comandos: devuelve `OrderCommandResult` = `Accepted | Rejected | Unknown`.
- `ClientOrderId` se genera y persiste antes de enviar; es la clave de idempotencia (UNIQUE en BD).
- `Unknown` obliga a reconciliar consultando la orden por `clientOrderId`; jamás se reenvía con un id nuevo.
- Un 2xx no implica llenado: el estado final llega por reconciliación/eventos.
- `PARTIALLY_FILLED` nunca transita a `FAILED` (ya existe una posición).

## Consecuencias
+ Imposible duplicar órdenes por reintentos; estado siempre convergente con el exchange.
− Más estados y un proceso de reconciliación que mantener.
