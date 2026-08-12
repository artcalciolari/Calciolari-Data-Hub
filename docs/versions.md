# Versões fixadas

Registradas a partir da documentação oficial disponível na data da migração C# (12/08/2026) e da Fase 0 original.

| Componente | Versão | Fonte |
|---|---|---|
| .NET / C# | 8.0 LTS | runtime do backend ASP.NET Core |
| ASP.NET Core | 8.0 | SDK `net8.0` |
| EF Core + Npgsql | 8.0.x | `Calciolari.DataHub.csproj` |
| PostgreSQL | 18.4 (compose) / 16 (CI) | [Docker Hub `postgres`](https://hub.docker.com/_/postgres) |
| Node.js (frontend) | 22.x | toolchain do ambiente Cloud |
| npm | 10.x | bundled com Node 22 |

O backend Java 21 + Spring Boot 4.1.0 foi substituído; ver ADR `docs/decisions/0004-csharp-backend.md`.
