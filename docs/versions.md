# Versões fixadas

Registradas a partir da documentação oficial disponível em 12/08/2026.

| Componente | Versão | Fonte |
|---|---|---|
| .NET / C# | 10.0 LTS (C# 14) | [Download .NET 10](https://dotnet.microsoft.com/en-us/download/dotnet/10.0) — Active até 14/11/2028 |
| ASP.NET Core | 10.0 | SDK `net10.0` / runtime 10.0.11 |
| EF Core + Npgsql | 10.0.x | `Calciolari.DataHub.csproj` |
| PostgreSQL | 16 (compose + CI) | [Docker Hub `postgres`](https://hub.docker.com/_/postgres) — same major in `compose.yaml` and `.github/workflows/ci.yml` |
| Node.js (frontend) | 22.x | toolchain do ambiente Cloud |
| npm | 10.x | bundled com Node 22 |

O backend Java 21 + Spring Boot 4.1.0 foi substituído; ver ADR `docs/decisions/0004-csharp-backend.md`.
