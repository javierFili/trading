# ADR-0003: Dominio puro, entidades JPA separadas

- Estado: Aceptada (Fase 0)

## Decisión
Los modelos de `domain` son Java puro (sin anotaciones JPA ni Spring). Las entidades JPA viven en
`infrastructure.persistence` y se mapean explícitamente. El esquema lo gobierna exclusivamente Flyway
(`ddl-auto=validate`).

## Consecuencias
+ Reglas financieras testeables sin base de datos; el modelo no se deforma por necesidades del ORM.
− Código de mapeo adicional (aceptable dado el número moderado de entidades).
