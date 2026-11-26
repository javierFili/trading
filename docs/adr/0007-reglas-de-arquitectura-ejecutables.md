# ADR-0007: Reglas de arquitectura ejecutables (ArchUnit)

- Estado: Aceptada (Fase 0)

## Decisión
Las restricciones críticas (sin ciclos, dominio sin frameworks, solo `exchange` conoce Binance, solo `execution`
usa `OrderClient`, `strategies` sin acceso a cuentas/órdenes/riesgo, `risk` independiente de quien propone,
sin `float/double`) se codifican en `ArchitectureTest` y se ejecutan en cada build.

Cada regla se valida con una violación plantada para evitar reglas que pasan en vacío (ArchUnit no registra
dependencias a tipos primitivos; por eso la regla de coma flotante usa una condición propia).

## Consecuencias
+ La arquitectura no se degrada en silencio. − Las reglas deben mantenerse cuando cambien los módulos.
