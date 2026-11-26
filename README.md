# Crypto Trading System

Plataforma modular de trading algorítmico **semiautomático** para **Binance Spot** (sin apalancamiento).
El sistema analiza el mercado, genera señales explicadas, dimensiona y valida el riesgo y **propone** operaciones;
**ninguna orden se envía sin la aprobación explícita de un usuario autenticado con segundo factor (TOTP)**.

> **Estado:** fases 0–7 y 9 implementadas y probadas (ver [plan](docs/DEVELOPMENT_PLAN.md)).
> Modos operativos: **BACKTEST** (por defecto) y **TESTNET**. **LIVE está bloqueado en código**: la fase 8
> (habilitar dinero real) requiere una autorización expresa y separada.
> La estrategia incluida es experimental: **no se asume rentable** (ver resultados reales en [BACKTESTING](docs/BACKTESTING.md)).

## Requisitos

| Herramienta | Versión | Notas |
|---|---|---|
| JDK | 21 | |
| Maven | incluido (`./mvnw`, 3.9.16) | |
| Docker | reciente | PostgreSQL de desarrollo y tests de integración (Testcontainers) |
| Node.js | 22 | **solo** para desarrollar el frontend; nunca en el servidor |

## Compilar y probar

```bash
./mvnw test      # 111 tests unitarios + reglas de arquitectura (sin Docker ni red)
./mvnw verify    # + 16 tests de integración con PostgreSQL real (Testcontainers), incluido el flujo completo
```

Los tests nunca contactan con Binance: usan un servidor HTTP simulado y un exchange en memoria.

## Ejecutar

```bash
cp .env.example .env            # rellenar: contraseña BD, CTS_JWT_SECRET, CTS_MASTER_KEY, usuario admin
#   openssl rand -base64 32     → para CTS_JWT_SECRET y CTS_MASTER_KEY
set -a; source .env; set +a
docker compose up -d postgres   # PostgreSQL 16 en 127.0.0.1:55432
./mvnw spring-boot:run          # puerto 8080 (cámbialo con SERVER_PORT=8090 si está ocupado)
```

Abre `http://localhost:8080`, entra con `CTS_ADMIN_USERNAME` / `CTS_ADMIN_PASSWORD` y activa el TOTP en «Cuenta».
Borra `CTS_ADMIN_PASSWORD` de tu `.env` tras el primer arranque.

### Backtesting (modo BACKTEST, sin cuenta de Binance)

En la página «Backtesting»: **1. Descargar histórico** (endpoint público de solo datos de mercado
`data-api.binance.vision`, sin claves) y **2. Ejecutar backtest**. También se pueden importar los CSV de
`data.binance.vision` vía `POST /api/v1/market/{BTC-USDT}/import`.

### TESTNET (fondos virtuales)

1. Crea claves en https://testnet.binance.vision (**nunca** claves de producción).
2. En `.env`: `CTS_TRADING_MODE=TESTNET`, `CTS_BINANCE_TESTNET_API_KEY`, `CTS_BINANCE_TESTNET_API_SECRET`.
3. Arranca: el sistema sincroniza velas, evalúa la estrategia en cada cierre de vela, crea propuestas
   y espera tu aprobación en «Propuestas».

### Frontend en desarrollo

```bash
cd frontend && npm install && npm run dev       # http://localhost:5173 (proxy a :8080)
CTS_WS_ALLOWED_ORIGINS=http://localhost:5173     # en el backend, para el WebSocket
npm run build                                   # genera src/main/resources/static (incluido en el jar)
```

### Docker

```bash
docker build -t crypto-trading-system .
docker run --env-file .env -p 8080:8080 crypto-trading-system
```

## Documentación

[Arquitectura](docs/ARCHITECTURE.md) · [Modelo de datos](docs/DATA_MODEL.md) · [Plan](docs/DEVELOPMENT_PLAN.md) ·
[Reglas de trading](docs/TRADING_RULES.md) · [Seguridad](docs/SECURITY.md) · [Binance](docs/BINANCE_INTEGRATION.md) ·
[Backtesting](docs/BACKTESTING.md) · [API](docs/API.md) · [Riesgos](docs/TECHNICAL_RISKS.md) · [ADRs](docs/adr/) ·
OpenAPI en `/swagger-ui.html`

## Aviso

Software experimental. Las puntuaciones de confianza son heurísticas, no probabilidades. Operar con criptoactivos
implica riesgo de pérdida total del capital.
