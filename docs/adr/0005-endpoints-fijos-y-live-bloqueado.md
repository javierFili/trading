# ADR-0005: Endpoints fijados por modo y LIVE bloqueado

- Estado: Aceptada (Fase 0)

## Contexto
Riesgo crítico: que una configuración de pruebas envíe órdenes a producción.

## Decisión
- Las URLs de Binance se derivan del `TradingMode` en código (`BinanceEnvironment.forMode`), no de propiedades.
- BACKTEST no tiene entorno de exchange (lanza excepción).
- `TradingModeGuard` valida al arrancar; LIVE requiere opt-in + frase de confirmación y, en esta versión, está bloqueado incondicionalmente (`LIVE_SUPPORTED=false`).
- Modo por defecto: BACKTEST.

## Consecuencias
+ Imposible redirigir TESTNET a producción editando configuración. − Cambiar endpoints exige un cambio de código revisado (deseable).
