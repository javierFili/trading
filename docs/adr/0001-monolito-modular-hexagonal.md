# ADR-0001: Monolito modular con arquitectura hexagonal por módulo

- Estado: Aceptada (Fase 0)

## Contexto
Un único operador, un exchange, capital pequeño. Se exige auditabilidad, testabilidad y evolución.

## Decisión
Un único artefacto Spring Boot con 14 módulos (paquetes de primer nivel) y capas `domain` / `application` /
`infrastructure` / `presentation`. Los puertos (interfaces) viven en `domain`; los adaptadores en `infrastructure`.

## Consecuencias
+ Despliegue y transacciones simples; fronteras verificadas por ArchUnit (ADR-0007).
+ Extraer un módulo a servicio en el futuro es posible porque las dependencias ya son explícitas.
− Requiere disciplina para no saltarse capas (mitigado con tests de arquitectura).
