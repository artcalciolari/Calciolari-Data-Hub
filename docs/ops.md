# Operação — Calciolari Data Hub (Fase 5)

## Ambientes e variáveis

Ver `.env.example`. Principais:

| Variável | Default | Uso |
|---|---|---|
| `SPRING_DATASOURCE_*` | local Postgres | JDBC |
| `DATAHUB_RAW_STORAGE_ROOT` | `./data/raw-storage` | bytes imutáveis `.QRP` |
| `DATAHUB_IMPORTS_MAX_FILES` | `20` | arquivos por upload |
| `DATAHUB_IMPORTS_MAX_FILE_BYTES` | `33554432` (32MB) | tamanho por arquivo |
| `DATAHUB_SECURITY_ENABLED` | `false` | autenticação HTTP Basic |
| `DATAHUB_SECURITY_USERS` | vazio | `user:pass:ROLE1\|ROLE2,…` |
| `DATAHUB_CORS_ALLOWED_ORIGINS` | vazio | origens CORS (CSV) |
| `SPRING_PROFILES_ACTIVE` | — | `local` ou `production` |

## Segurança

- **Local / rede controlada:** `datahub.security.enabled=false` (default). A API fica aberta; mantenha atrás de VPN/firewall. Não publique na internet.
- **Produção:** perfil `production` força `datahub.security.enabled=true` e falha o startup se não houver usuários. Roles:
  - `VIEWER` — `GET /api/**`
  - `IMPORTER` — VIEWER + `POST /api/imports/qrp` (upload)
  - `ADMIN` — tudo, inclusive `POST /api/imports/files/{id}/reprocess` + `/actuator/metrics`
- `/actuator/health` e `/actuator/info` permanecem públicos.
- Headers: `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`.
- CORS vazio = same-origin only. Prefira reverse-proxy same-origin em produção.
- Stack traces nunca são incluídos (`server.error.include-stacktrace=never`).

Exemplo (produção):

```bash
export SPRING_PROFILES_ACTIVE=production
export DATAHUB_SECURITY_USERS='viewer:segredo:VIEWER,importer:segredo:IMPORTER|VIEWER,admin:segredo:ADMIN|IMPORTER|VIEWER'
```

## Limites de upload

| Limite | Valor |
|---|---|
| Extensão | somente `.qrp` |
| Arquivos / request | 20 |
| Bytes / arquivo | 32 MB |
| Request multipart | 64 MB |

MIME e filename do cliente não são confiáveis; só a extensão e o tamanho são checados no transporte. Conteúdo inválido é armazenado e auditado, sem publicação canônica.

## Backup e restore (unidade lógica)

Trate **PostgreSQL + diretório raw** como uma unidade. Restaurar só o banco sem os bytes (ou o inverso) deixa o sistema inconsistente.

### Backup

```bash
./scripts/backup.sh /var/backups/datahub
# gera:
#   datahub-YYYYMMDD-HHMMSS/pg.dump
#   datahub-YYYYMMDD-HHMMSS/raw-storage.tgz
#   datahub-YYYYMMDD-HHMMSS/MANIFEST.txt
```

Requer `pg_dump`, `tar`, e variáveis `SPRING_DATASOURCE_*` / `DATAHUB_RAW_STORAGE_ROOT` (ou defaults locais).

### Restore

```bash
# Pare o backend antes.
./scripts/restore.sh /var/backups/datahub/datahub-YYYYMMDD-HHMMSS
```

O script:

1. restaura o dump no banco alvo;
2. substitui o diretório raw pelo tarball;
3. imprime um checklist de verificação (`/actuator/health`, contagem de `raw_artifact`).

### Verificação pós-restore

```sql
SELECT count(*) FROM raw_artifact;
SELECT count(*) FROM artifact_publication;
```

Confirme que cada `storage_key` em `raw_artifact` existe sob `DATAHUB_RAW_STORAGE_ROOT` e que o SHA-256 bate.

## PWA

- Manifest + service worker via `vite-plugin-pwa`.
- Cache: app shell e assets estáticos versionados.
- `/api/**` e `/actuator/**`: **NetworkOnly** (nunca cacheados).
- Sem parsing offline e sem persistência de dados de negócio no client.
- Banner “Nova versão disponível” força reload do shell quando há SW waiting.

Build:

```bash
cd frontend && npm run build && npm run preview
```

Instalável em HTTPS ou `localhost`.

## Reprocessamento

```bash
curl -u admin:… -X POST http://localhost:8080/api/imports/files/<fileId>/reprocess
```

- Bytes brutos imutáveis; corrupção (hash/tamanho) → `409`, sem nova tentativa.
- Sucesso troca `artifact_publication.active_parse_attempt_id` na mesma transação da publicação.
- Falha de parse preserva o pointer anterior; tentativa fica auditável.
- Não há botão na UI principal do MVP.

## Riscos residuais

Ver `docs/residuals.md`.
