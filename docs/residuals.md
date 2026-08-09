# Riscos residuais e limitações (MVP)

Documentados de propósito — não escondidos em defaults. Ver também `IMPLEMENTATION_PLAN.md` §17–18.

## Produto / domínio

| Residual | Impacto | Mitigação atual |
|---|---|---|
| Sobreposição de `external_sale_id` entre artefatos | Publicação bloqueada (`OVERLAPPING_REPORT`) | Resolver identidade antes de republicar; ver ADR `0002-identity-reconciliation.md` |
| Escalas numéricas provisórias | Precisão/arredondamento pode mudar com mais fixtures | Migrations `V1` + nota no plano §6.2 |
| Sem rollback completo de dataset / escolha arbitrária de parser | Só “avançar” via reprocess | Tentativas antigas ficam auditáveis; pointer ativo só troca no sucesso |
| Reprocessamento fora da navegação principal | Operação só via API admin | `POST /api/imports/files/{id}/reprocess` (`ADMIN` com security on) |
| Worker assíncrono / reclaim de lease após crash | Parse síncrono no request; crash mid-parse pode deixar `PROCESSING` até lease expirar | Lease 5 min; reprocess subsequente rejeita lease ativo (`409`) ou segue após expiração |

## Operação / segurança

| Residual | Impacto | Mitigação atual |
|---|---|---|
| Security default `enabled=false` | API aberta em local | Somente rede controlada; perfil `production` fail-fast sem usuários |
| HTTP Basic in-memory | Sem IdP / rotação | Suficiente para LAN; trocar antes de exposição externa |
| Multi-instância no reprocess | Segundo nó pode receber `409` se lease ativo | Serialização in-JVM + lock de linha; cliente deve retentar |
| Backup/restore manual | Inconsistência se só PG ou só raw for restaurado | Scripts `scripts/backup.sh` / `restore.sh`; unidade lógica documentada |

## Frontend / PWA

| Residual | Impacto | Mitigação atual |
|---|---|---|
| PWA só app-shell | Sem dados offline / parsing no client | `/api` e `/actuator` = NetworkOnly |
| Sem UI de reprocess | Admin usa API/curl | Evita superfície acidental até haver fluxo de autorização na UI |

## Fora de escopo (não é bug)

Stone, Excel/CSV, ERP completo, Kafka, K8s, microsserviços, IA, app nativo, automação do InterPDV — ver plano §18.
