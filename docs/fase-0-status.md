# Fase 0 — status

**Gate:** desbloqueado para o parser (Fixtures A e B presentes).

## Artefatos

| Artefato | Estado |
|---|---|
| PoC `docs/poc/index.html` | presente |
| Fixture A `.QRP` (produto 35, `AUDITORIA.QRP`) | presente — SHA-256 `cc98441ad0422fbe426165fd01e6ad9dff4d8eff183b1ebf426c80fb76dd365c` (66048 bytes) |
| Fixture B `.QRP` (produto 41) | presente — SHA-256 `4e1b7539aa9f33c131924a5eb6e6b9400e38041dff6a05c437e95a96bc8654fb` (473100 bytes) |
| Revisão humana de sensibilidade | pendente |

## Parser

A lógica binária do PoC foi portada para Java e as regressões §13.2 de A e B passam.
