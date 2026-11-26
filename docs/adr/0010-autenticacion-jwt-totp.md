# ADR-0010: Autenticación JWT + TOTP de un solo uso

- Estado: Aceptada (confirmada por el usuario el 2026-11-19)

## Decisión
Argon2id para contraseñas, JWT HS256 de 30 min (stateless), TOTP RFC 6238 obligatorio para aprobar y cerrar el circuit
breaker. El último paso TOTP usado se guarda de forma atómica en su propia transacción: un código no puede reutilizarse
ni siquiera si la acción que protegía falla. Consecuencia práctica: como máximo una acción con 2FA cada 30 s por usuario.

## Consecuencias
+ Robo de contraseña o de JWT no basta para ejecutar operaciones. − Sin revocación de JWT antes de su expiración.
