# Arquitectura — Crypto Trading System

> Estado: fases 0–7 y 9 implementadas. LIVE bloqueado en código.

## 1. Visión general

Monolito modular (un único artefacto Spring Boot) con arquitectura hexagonal dentro de cada módulo.
Motivo: un solo operador, un solo exchange y capital pequeño no justifican microservicios; sí justifican
fronteras estrictas para poder auditar y probar cada pieza por separado (ver [ADR-0001](adr/0001-monolito-modular-hexagonal.md)).

```
                ┌────────────── presentation (REST / WebSocket) ──────────────┐
                │                                                              │
 signals ──► trading (propuestas + aprobación) ──► execution ──► exchange.OrderClient
    ▲              │                                   │
 strategies        ▼                                   ▼
    ▲            risk (guardián independiente)     portfolio ◄── exchange.AccountClient
 indicators
    ▲
  market ◄── exchange.MarketDataClient / MarketDataStream
    ▲
  shared (Symbol, Timeframe, Candle, TradingMode, DecimalPolicy)

 backtesting ──► strategies + risk + market   (nunca exchange)
 audit / notifications ◄── eventos de aplicación (Spring Events)
 configuration / security: transversales
```

## 2. Capas dentro de cada módulo

| Capa | Contenido | Puede depender de |
|---|---|---|
| `domain` | Modelos, reglas, puertos (interfaces). Java puro. | `shared.domain`, `domain` de módulos inferiores |
| `application` | Casos de uso, orquestación, transacciones | `domain` |
| `infrastructure` | Adaptadores: JPA, Binance, schedulers | `application`, `domain`, frameworks |
| `presentation` | Controladores REST/WebSocket, DTOs | `application` |

Reglas verificadas automáticamente por `ArchitectureTest` (ArchUnit) en cada `mvn test`:

1. Sin ciclos entre módulos.
2. `domain` no depende de Spring, JPA, Hibernate ni de otras capas.
3. `application` no depende de `infrastructure` ni `presentation`.
4. Solo el módulo `exchange` conoce el adaptador Binance.
5. Solo `execution` puede usar `OrderClient` (enviar/cancelar órdenes).
6. `strategies` no accede a riesgo, trading, ejecución, cartera, cuentas ni órdenes.
7. `risk` no depende de `strategies`, `signals`, `trading` ni `execution`.
8. Prohibido `float`/`double` en módulos financieros (campos, firmas y llamadas, p. ej. `BigDecimal.doubleValue()`).

Cada regla se ha validado introduciendo una violación deliberada y comprobando que el test falla.

## 3. Paquetes

```
com.cryptotrading
├── CryptoTradingApplication
├── shared.domain          Symbol, Timeframe, Candle, TradingMode, DecimalPolicy
├── configuration          TradingProperties, TradingModeGuard, PlatformConfiguration (Clock UTC)
├── security.infrastructure SecurityConfiguration (deny-by-default)
├── exchange
│   ├── domain             ExchangeClient, MarketDataClient, AccountClient, OrderClient,
│   │                      MarketDataStream, UserDataStream, OrderRequest, OrderCommandResult,
│   │                      ExchangeOrder, SymbolRules, Balance, ClientOrderId, ExchangeException…
│   └── infrastructure.binance  BinanceEnvironment (endpoints fijados por modo)
├── market.domain          CandleSeries (protección look-ahead)
├── indicators.domain      Indicator<V>, IndicatorSeries, IndicatorPoint
├── strategies.domain      Strategy, StrategyDescriptor, StrategyEvaluation, SignalType, ConfidenceScore
├── signals                domain.Signal, application.SignalEngine
├── risk.domain            RiskManager, RiskLimits, RiskContext, TradeIntent, PositionSize, RiskDecision
├── trading.domain         ProposalStatus (máquina de estados)
├── execution / portfolio / backtesting / notifications / audit   (package-info: responsabilidades)
```

## 4. Flujo semiautomático

```
vela cerrada (WebSocket → sync REST, o cron de respaldo)  ── CandleClosedEvent
  → SignalEngine: estrategia sobre CandleSeries cerrada; indicadores + señal deduplicada ── SignalCreatedEvent
  → (tras commit) ProposalService: RiskContext fresco → RiskManager → TradeProposal PENDING_APPROVAL (hash SHA-256)
  → usuario TRADER: versión + hash + TOTP → APPROVED ── ProposalEvents.Approved
  → (tras commit, asíncrono) ExecutionService: expiración, circuit breaker, precio ±0,3 %, riesgo re-evaluado
       → OrderRecord PENDING_SUBMIT (clientOrderId) → submit una vez → Accepted / Rejected / Unknown
  → OrderCompletionHandler: fills y comisiones reales → posición OPEN → OCO (TP LIMIT_MAKER + SL STOP_LOSS_LIMIT)
  → ReconciliationService (cada 15 s): resuelve UNKNOWN, sigue órdenes vivas, cierra la posición al ejecutarse TP/SL
```

Las llamadas de red nunca se hacen dentro de una transacción de BD; las notificaciones y la ejecución se disparan
solo **después** del commit (`@TransactionalEventListener`).

## 5. Interfaces principales (contratos)

| Interfaz | Módulo | Responsabilidad | Implementación prevista |
|---|---|---|---|
| `ExchangeClient` | exchange | Agrega los puertos del exchange separados por sensibilidad | `BinanceSpotClient` |
| `MarketDataClient` | exchange | Datos públicos: hora del servidor, precio, velas, reglas de símbolo | implementado |
| `AccountClient` | exchange | Lectura privada: saldos, consulta de órdenes (reconciliación) | implementado |
| `OrderClient` | exchange | Envío/cancelación. Devuelve `OrderCommandResult` (Accepted/Rejected/**Unknown**) | implementado (+ OCO) |
| `MarketDataStream` | exchange | WebSocket de velas con reconexión | `BinanceKlineStream` |
| `HistoricalCandleSource` | exchange | Histórico desde el host de solo datos de mercado | `BinanceMarketDataClient` (`data-api.binance.vision`) |
| `Indicator<V>` | indicators | Cálculo puro sobre `CandleSeries` | implementado |
| `Strategy` | strategies | Evaluación explicable; nunca genera órdenes | implementado |
| `SignalEngine` | signals | Ejecuta estrategias, deduplica, persiste | implementado |
| `RiskManager` | risk | Función pura: intención + límites + contexto → decisión | `DefaultRiskManager` |

Decisiones de diseño clave:

- **Resultados ambiguos como tipo** (`OrderCommandResult.Unknown`): un timeout no se puede "tragar" en un `catch`; obliga a reconciliar ([ADR-0006](adr/0006-ordenes-ambiguas-y-reconciliacion.md)).
- **`CandleSeries` exige velas cerradas a `asOf`**: la misma regla protege el modo en vivo y el backtest contra el look-ahead bias.
- **`RiskManager` sin I/O ni reloj**: determinista y probable de forma exhaustiva; el contexto se construye fuera.
- **`ConfidenceScore` documentado como heurístico**, no como probabilidad.
- **`Clock` inyectado** (UTC) para expiraciones, ventana de pérdida diaria y simulación.

## 6. Modos de operación

`trading.mode` = `BACKTEST` (por defecto) | `TESTNET` | `LIVE`. `TradingModeGuard` valida al arrancar y en esta
versión **rechaza LIVE siempre**. Las URLs de Binance están fijadas en código por modo (`BinanceEnvironment`),
no son configurables ([ADR-0005](adr/0005-endpoints-fijos-y-live-bloqueado.md)).

## 7. Stack (verificado en Maven Central el 2026-11-18)

| Componente | Versión |
|---|---|
| Java | 21 LTS (OpenJDK 21.0.13 local) |
| Spring Boot | 4.1.2 (Spring Framework 7, Spring Security 7) |
| Hibernate | 7.4.13 |
| Flyway | 12.4.0 (+ `flyway-database-postgresql`) |
| PostgreSQL | 16 (contenedor dev/test) |
| JUnit / Mockito / AssertJ | 6.0.3 / 5.23.0 / gestionado por Boot |
| Testcontainers | 2.0.5 |
| ArchUnit | 1.5.1 |

Dependencias Maven: `webmvc`, `websocket`, `validation`, `security`, `data-jpa`, `flyway`, `actuator`,
`postgresql`, `configuration-processor`; test: `starter-test`, `webmvc-test`, `security-test`,
`spring-boot-testcontainers`, `testcontainers-postgresql`, `testcontainers-junit-jupiter`, `archunit-junit5`.
Además: `spring-boot-starter-oauth2-resource-server` (JWT), `bcprov-jdk18on` 1.87 (Argon2), `springdoc-openapi` 3.2.0.
Frontend: React 19.3.1, Vite 8.3.5, TypeScript 5.9.3, Tailwind 4.3.4, Lightweight Charts 5.2.1 (compilado al jar).
Sin Lombok: se usan `record`. Redis no se usa todavía.

## 8. ADRs

- [0001 Monolito modular con hexagonal por módulo](adr/0001-monolito-modular-hexagonal.md)
- [0002 Java 21 + Spring Boot 4.1](adr/0002-java21-spring-boot-4.md)
- [0003 Dominio puro separado de entidades JPA](adr/0003-dominio-puro-y-jpa.md)
- [0004 BigDecimal y política de redondeo](adr/0004-bigdecimal-y-redondeo.md)
- [0005 Endpoints fijos por modo y LIVE bloqueado](adr/0005-endpoints-fijos-y-live-bloqueado.md)
- [0006 Órdenes ambiguas, idempotencia y reconciliación](adr/0006-ordenes-ambiguas-y-reconciliacion.md)
- [0007 Reglas de arquitectura ejecutables](adr/0007-reglas-de-arquitectura-ejecutables.md)
- [0008 JPA para agregados, JDBC para escrituras masivas](adr/0008-jpa-y-jdbc.md)
- [0009 Protección con OCO en el exchange](adr/0009-proteccion-oco.md)
- [0010 Autenticación JWT + TOTP de un solo uso](adr/0010-autenticacion-jwt-totp.md)
- [0011 Reconciliación por REST en lugar de user data stream](adr/0011-reconciliacion-rest.md)
