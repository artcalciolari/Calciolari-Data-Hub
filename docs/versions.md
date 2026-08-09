# Versões fixadas (Fase 0)

Registradas em 09/08/2026 a partir da documentação oficial disponível na data.

| Componente | Versão | Fonte |
|---|---|---|
| Java | 21 (LTS) | plano + runtime do ambiente |
| Spring Boot | 4.1.0 | [start.spring.io](https://start.spring.io) (intervalo compatível `>=4.0.0`) / [releases](https://github.com/spring-projects/spring-boot/releases) |
| Apache Maven (wrapper) | 3.9.16 | `.mvn/wrapper/maven-wrapper.properties` |
| PostgreSQL | 18.4 | [Docker Hub `postgres`](https://hub.docker.com/_/postgres) (`postgres:18.4-alpine`) |
| Node.js (frontend, Fase 4) | 22.x | toolchain do ambiente Cloud |
| npm | 10.x | bundled com Node 22 |

Dependências de aplicação (JPA, Flyway, Web, Testcontainers, React/Vite) serão pinadas nos manifests correspondentes quando as fases 3–4 forem liberadas pelo gate do parser.
