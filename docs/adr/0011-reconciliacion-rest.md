# ADR-0011: Reconciliación por REST en lugar de user data stream

- Estado: Aceptada (Fase 7)

## Contexto
El mecanismo del user data stream privado de Binance Spot ha cambiado recientemente (listenKey frente a suscripción vía
WebSocket API) y su implementación no se puede validar sin el Testnet real.

## Decisión
El estado de las órdenes converge por REST cada 15 s (`GET /api/v3/order` por `clientOrderId` + `myTrades`). Es la
fuente de verdad incluso si en el futuro se añade el stream (que solo reduciría latencia).

## Consecuencias
+ Simple, idempotente y robusto ante desconexiones. − Hasta ~15 s de latencia en detectar ejecuciones (aceptable con velas de 1 h).
