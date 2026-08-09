# Fixtures QRP

| Id | Arquivo | Estado |
|---|---|---|
| `fixture-a` | `qrp/fixture-a.qrp` | ausente |
| `fixture-b` | `qrp/fixture-b.qrp` | presente (MOLHO POMODORO / produto 41) |

Metadados e valores ouro: `manifest.json`.

## Pacote externo (opcional)

```bash
export DATAHUB_FIXTURES_DIR=/caminho/absoluto/para/pasta-com-qrp
```

Os testes resolvem `DATAHUB_FIXTURES_DIR` antes do classpath e validam SHA-256/tamanho do manifesto.
