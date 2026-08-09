# ADR 0002 — Identidade e reconciliação (Fase 3A)

## Status

Aceito com base nos Fixtures A e B.

## Decisões

### Produto

- Identidade canônica: `(external_source='INTERPDV', external_id=<código do relatório>)`.
- Evidência: `Produto: 35 - NHOQUE BATATA`, `Produto: 41 - MOLHO POMODORO`.
- Nome e fabricante são atributos mutáveis da observação; a chave é o código externo.

### Venda

- Identidade canônica: `(external_source='INTERPDV', external_sale_id=<Venda Numero>)`.
- Evidência: campo `Venda Numero:` no layout InterPDV.

### Item / linha

- **Não há** chave semântica estável além da ordem no parse.
- Fixture A contém **duas** linhas com o mesmo `Venda Numero: 134808` e totais diferentes.
- Identidade de linha: `(parse_attempt_id, source_record_index)` apenas.
- Não mesclar silenciosamente linhas de arquivos com SHA-256 distintos.

### Direção

- `OUT` quando a coluna `Saidas` está preenchida (fluxo de faturamento).
- `IN` quando só `Entradas` está preenchida.
- `RETURN` ainda sem regra de negócio — não publicar como venda.
- Somente `OUT` vira `Sale` / `SaleItem`.

### Deduplicação de arquivo

- Chave do artefato bruto: SHA-256 dos bytes.
- Mesmo conteúdo + nomes diferentes → um `raw_artifact`, vários `import_file`.
- Uploads concorrentes: constraint `UNIQUE(sha256)` + retry de select-or-create.

### Sobreposição entre relatórios distintos

- Dois SHA-256 diferentes que compartilham `external_sale_id` já publicado por outro artefato ativo.
- **Publicação canônica é bloqueada** (parse permanece com issue `OVERLAPPING_REPORT`); `parsed_movement` e raw são preservados.
- Reconciliar sobreposição fica fora desta fase.

### Publicação

- Ponteiro `artifact_publication.active_parse_attempt_id` só aponta para tentativa sem `FATAL`/`ERROR`.
- Consultas futuras devem considerar apenas tentativas ativas.
