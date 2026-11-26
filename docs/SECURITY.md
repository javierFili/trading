# Seguridad

## Implementado

| Control | Implementación |
|---|---|
| Autenticación | Usuarios en BD, contraseña **Argon2id** (mín. 12 caracteres), login → **JWT HS256** (30 min) con emisor validado |
| Bloqueo | 5 intentos fallidos → bloqueo 15 min; respuesta siempre genérica (no revela si el usuario existe); tiempo constante con hash señuelo |
| Autorización | `VIEWER` (lectura) / `TRADER` (aprobar, rechazar, backtests, importar datos, cerrar circuit breaker, auditoría) con `@PreAuthorize` |
| Segundo factor | **TOTP RFC 6238** (verificado con los vectores oficiales) obligatorio para aprobar y para cerrar el circuit breaker. Cada código se consume una sola vez (paso guardado de forma atómica, en transacción propia: un código usado en un intento fallido no puede reutilizarse) |
| Secretos en reposo | Secreto TOTP cifrado con **AES-256-GCM** (`CTS_MASTER_KEY`); manipulación detectada por la etiqueta GCM |
| Secretos de configuración | Solo variables de entorno; el arranque falla si faltan `CTS_JWT_SECRET`/`CTS_MASTER_KEY`. `toString()` redactado en todos los objetos con secretos. Verificado: ningún secreto aparece en los logs de arranque |
| Claves de Binance | Solo variables de entorno, separadas por modo; endpoints fijados en código por modo; LIVE exige claves distintas de TESTNET y comprueba que la clave **no** permite retiros |
| API | Deny-by-default; públicos solo login, health, la UI estática y OpenAPI. Errores JSON sin stacktraces. CSRF desactivado (tokens bearer, sin cookies). CSP restrictiva |
| WebSocket | `/ws` autenticado (token en `access_token`, único endpoint que lo acepta por query); solo servidor→cliente; orígenes restringidos al mismo origen salvo configuración |
| Auditoría | `audit_log` append-only (trigger). Se registran logins, bloqueos, TOTP, propuestas, aprobaciones/rechazos (también **intentos fallidos**), órdenes, posiciones, circuit breaker, límites cargados, importaciones y backtests |

## Pendiente / recomendaciones

- Servir **siempre por HTTPS** (proxy inverso). El JWT viaja en cabeceras; sin TLS sería interceptable.
- El JWT se guarda en `sessionStorage`: un XSS podría leerlo. Mitigado con CSP y React (sin `dangerouslySetInnerHTML`). Alternativa futura: cookie `HttpOnly` + CSRF.
- No hay revocación de JWT antes de su expiración (30 min).
- No hay gestión de usuarios por API (solo el usuario inicial por variables de entorno).
- OpenAPI (`/v3/api-docs`, `/swagger-ui.html`) es público: no expone secretos, pero puede desactivarse con `springdoc.api-docs.enabled=false`.

## Desbloqueo de LIVE (contrato)

LIVE está bloqueado en código (`TradingModeGuard.LIVE_SUPPORTED = false`). Ya implementado para cuando se active:
opt-in `trading.live.enabled=true`, frase `trading.live.confirmation`, claves LIVE distintas de TESTNET y verificación de
que la clave no permite retiros (`/sapi/v1/account/apiRestrictions`). **Antes de cambiar `LIVE_SUPPORTED`**:

1. Campaña en TESTNET real sin discrepancias de reconciliación (≥ 2 semanas).
2. HTTPS y restricción por IP de la clave en Binance.
3. Revisión de código del cambio y autorización explícita del usuario, registrada.
