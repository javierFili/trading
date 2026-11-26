# Riesgos técnicos identificados

| # | Riesgo | Impacto | Mitigación |
|---|---|---|---|
| R1 | Respuesta ambigua al enviar una orden (timeout/5xx) → orden duplicada o perdida | Alto | `clientOrderId` persistido antes de enviar; resultado `Unknown` explícito; reconciliación obligatoria; nunca reintentar con un id nuevo (ADR-0006) |
| R2 | Configuración TESTNET que acaba enviando a producción | Crítico | Endpoints fijados en código por modo, test que lo verifica; LIVE bloqueado en esta versión; claves separadas por modo |
| R3 | Errores de precisión (float, redondeos) | Alto | `BigDecimal` + `DecimalPolicy`; regla ArchUnit contra `float/double` probada con violación real; redondeo hacia abajo de cantidades |
| R4 | Look-ahead bias en backtest y en vivo (usar la vela abierta) | Alto | `CandleSeries` rechaza velas no cerradas a `asOf`; ejecución simulada en la vela siguiente |
| R5 | Sobreajuste de la estrategia | Alto | Splits cronológicos, prueba fuera de muestra una sola vez, métricas más allá del beneficio; la estrategia no se asume rentable |
| R6 | Rate limits / baneo de IP | Medio | Limitador local por peso, respeto de `Retry-After`, parada total ante 418 |
| R7 | Desfase de reloj → rechazo de peticiones firmadas | Medio | Offset con `/api/v3/time`, circuit breaker si excede margen |
| R8 | Desconexiones de WebSocket → velas o eventos perdidos | Medio | Reconexión con backoff, relleno de huecos vía REST, reconciliación periódica independiente del stream |
| R9 | Posición sin protección si el sistema cae | Alto | OCO en el exchange tras cada entrada (ADR-0009); si falla → circuit breaker + alerta CRITICAL |
| R10 | Filtros del exchange cambian (minNotional, tickSize) | Medio | `SymbolRules.fetchedAt`, refresco periódico y antes de cada orden, validación local + `order/test` |
| R11 | Fuga de secretos (logs, endpoints, repo) | Crítico | Variables de entorno, `.gitignore`, `toString` redactado, sin secretos en `audit_log`, key sin permiso de retiro |
| R12 | Aprobación sobre datos obsoletos | Alto | Aprobación ligada a versión+hash, expiración, revalidación previa a ejecutar |
| R13 | Capital pequeño (100 USDT): comisiones y `minNotional` dominan el resultado | Medio | Comisiones y deslizamiento incluidos en el sizing y el backtest; rechazo si el tamaño cae bajo el mínimo |
| R14 | Testnet no representativa (liquidez, reinicios periódicos) | Medio | Resultados de testnet solo validan integración, no rentabilidad |
| R15 | Entorno: Maven 3.6.3 del sistema; disco al 90 %; puerto 8080 ocupado por otro proyecto local | Bajo | Maven Wrapper 3.9.16 añadido; `SERVER_PORT` configurable; vigilar espacio |
| R16 | El directorio `trading/` está dentro de un repositorio git padre que contiene otros proyectos | Medio | Recomendado: repositorio git propio para este proyecto (pendiente de decisión del usuario) |
| R17 | Cambios de la API de Binance (p. ej. mecanismo del user data stream) | Medio | Reverificación contra la documentación oficial al inicio de la Fase 2; adaptador aislado tras puertos |
| R18 | Integración no validada contra el Testnet real (firma, OCO, formatos) | Alto | Tests con servidor simulado y ejemplo oficial de firma; **validar en TESTNET antes de cualquier uso** |
| R19 | La estrategia apenas opera tras costes (backtest real: 2 operaciones en 23 meses) | Medio | Documentado en BACKTESTING.md; revisar con criterio sin optimizar sobre TEST |
| R20 | JWT en `sessionStorage` (exposición ante XSS) y sin HTTPS propio | Medio | CSP restrictiva; desplegar tras proxy HTTPS; valorar cookie HttpOnly |
| R21 | Comisiones en BNB estimadas | Bajo | Marcadas como estimadas en auditoría |
