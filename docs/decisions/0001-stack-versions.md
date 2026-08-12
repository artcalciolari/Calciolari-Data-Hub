# ADR 0001 — Stack inicial do MVP

## Status

Aceito (Fase 0, parcial).

## Contexto

O repositório estava apenas com o plano. É preciso fixar versões e um scaffold
mínimo sem inventar o parser QRP.

## Decisão

- Java 21 + Spring Boot **4.1.0** (versão estável aceita pelo Spring Initializr em 09/08/2026; intervalo declarado `>=4.0.0`).
- Build com Maven Wrapper (`./mvnw`), sem exigir Maven instalado no PATH.
- PostgreSQL **18.4** via `compose.yaml` a partir da Fase 3A.
- Neste incremento o `pom.xml` inclui apenas `starter`, `validation` e `json`.
  Web/JPA/Flyway/Actuator entram nas Fases 3A/3B após o gate do parser.
- Binários `.QRP` ficam fora do Git por padrão (`.gitignore`); o manifesto versiona
  expectativas e, quando disponíveis, SHA-256.

## Consequências

- Testes unitários do scaffold rodavam sem Docker.
- `InterPdvQrpParser` permanece sem implementação de formato até o PoC/fixtures.
- **Superseded in part:** o backend de runtime é C# / ASP.NET Core 10 (ADR 0004). Este ADR permanece como registro da stack inicial Java.
