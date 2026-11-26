# ADR-0008: JPA para agregados, JDBC para escrituras masivas

- Estado: Aceptada (Fase 1)

## Decisión
JPA (Spring Data) con `@Version` para agregados con concurrencia y ciclo de vida: usuarios, propuestas, órdenes,
posiciones y pares. `JdbcTemplate` para velas (`INSERT … ON CONFLICT DO NOTHING` por lotes), señales/indicadores, auditoría
(append-only), backtests, fills y circuit breaker. Las columnas JSONB se mapean como texto con `?::jsonb`.

## Consecuencias
+ Importar 17 544 velas tarda segundos; idempotencia garantizada por la BD. − Dos estilos de persistencia que mantener.
