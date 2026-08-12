# ADR 0004 — Backend C# / ASP.NET Core

## Status

Aceito (substitui a stack Java/Spring Boot do ADR 0001 para o código do MVP).

## Contexto

O MVP estava em Java 21 + Spring Boot. A migração para C# mantém o contrato HTTP (`/api`, `/actuator`), o schema PostgreSQL e o parser InterPDV QRP (port do PoC, sem inventar formato).

## Decisão

- **.NET 10 LTS** (C# 14) + ASP.NET Core, solução `backend/Calciolari.DataHub.sln`. .NET 8 está em maintenance (EOL 10/11/2026); .NET 11 ainda é preview.
- EF Core 10 + Npgsql 10; migração SQL embutida `V1__import_and_canonical.sql` (mesmo DDL da era Flyway).
- Coverlet 100% line+branch, excluindo o namespace QRP/EMF (equivalente ao exclude JaCoCo).
- PostgreSQL real apenas (sem InMemory/H2).
- Variáveis `DATAHUB_*`; `SPRING_DATASOURCE_*` ainda são lidas como fallback para scripts de backup existentes.
- Porta **8080** para o proxy Vite.

## Consequências

- CI usa `actions/setup-dotnet` e `dotnet test`.
- Perfil de produção: `ASPNETCORE_ENVIRONMENT=Production` (auth obrigatória).
- O plano histórico (`IMPLEMENTATION_PLAN.md`) descreve Java; este ADR é a fonte de verdade da stack do backend.
