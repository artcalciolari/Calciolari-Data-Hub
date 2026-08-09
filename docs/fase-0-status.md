# Fase 0 — status

**Gate:** parcialmente desbloqueado.

## Artefatos

| Artefato | Estado |
|---|---|
| PoC `docs/poc/index.html` | presente |
| Fixture B `.QRP` (produto 41) | presente — SHA-256 `4e1b7539aa9f33c131924a5eb6e6b9400e38041dff6a05c437e95a96bc8654fb` (473100 bytes) |
| Fixture A (NHOQUE BATATA) | **ainda ausente** |
| Revisão humana de sensibilidade | pendente |

## Parser

A lógica binária do PoC foi portada para Java (`InterPdvQrpParser` e colaboradores) **sem inventar** estruturas além das observadas no HTML. A regressão da §13.2 para Fixture B está coberta por teste.

## Ainda bloqueia o gate completo da Fase 0/2

- Fixture A e seus hashes/valores ouro exercitados no mesmo runner;
- política final de versionamento/sensibilidade dos binários (hoje o Fixture B está no Git sob `backend/src/test/resources/fixtures/qrp/` para regressão).
