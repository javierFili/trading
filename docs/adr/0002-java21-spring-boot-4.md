# ADR-0002: Java 21 LTS + Spring Boot 4.1

- Estado: Aceptada (Fase 0)

## Contexto
La especificación pide Java 21 y "una versión estable compatible" de Spring Boot. A 2026-11-18, Maven Central
ofrece 4.1.2 (última estable) y 4.0.x; la línea 3.5 está fuera del soporte OSS.

## Decisión
Spring Boot **4.1.2** (Spring Framework 7, Security 7, Hibernate 7.4, Flyway 12, JUnit 6, Testcontainers 2).
Starters modulares de Boot 4 (`spring-boot-starter-webmvc`, `spring-boot-starter-flyway`, `…-webmvc-test`).
Hilos virtuales habilitados. Sin Lombok (se usan `record`).

## Consecuencias
+ Línea con soporte más largo. − Algunas guías en internet aún describen Boot 3; los nombres de paquetes de test
cambiaron (p. ej. `org.springframework.boot.webmvc.test.autoconfigure`, `org.testcontainers.postgresql`).
