# Fixtures QRP

Os binários **não** estão no repositório. O gate da Fase 0 exige Fixture A/B
(reais ou sanitizados) mais revisão humana dos hashes.

## Layout esperado

```text
fixtures/
  manifest.json          # versionado — expectativas ouro + metadados
  qrp/
    fixture-a.qrp        # opcional no Git; preferir pacote externo
    fixture-b.qrp
  README.md
```

## Como apontar um pacote externo

```bash
export DATAHUB_FIXTURES_DIR=/caminho/absoluto/para/pasta-com-qrp
```

A pasta deve conter os arquivos referidos por `relativePath` no manifesto
(por exemplo `qrp/fixture-a.qrp`), ou os mesmos nomes na raiz do diretório
configurado. Os testes resolvem nessa ordem:

1. `DATAHUB_FIXTURES_DIR`
2. `classpath:fixtures/` (recursos de teste)

## Quando o pacote estiver ausente

- `packageStatus` no manifesto permanece `ABSENT`.
- Testes que precisam dos bytes usam assume/fail com mensagem explícita.
- **Proibido** fabricar `.QRP` sintéticos que “pareçam” InterPDV.
