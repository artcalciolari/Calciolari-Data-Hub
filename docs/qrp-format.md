# Formato QRP / InterPDV — evidência observada

> Evidência extraída de `docs/poc/index.html` e validada contra Fixture B
> (`AUDITORIA 41, 01_07-20_07.QRP`, SHA-256 `4e1b7539…654fb`).

## Fontes

| Fonte | Estado |
|---|---|
| PoC `docs/poc/index.html` | presente |
| Fixture A (NHOQUE BATATA) | **ausente** |
| Fixture B (MOLHO POMODORO) | presente (`fixtures/qrp/fixture-b.qrp`) |

## Container QRP → páginas EMF

Portado de `isEmfAt` / `findEmfPages`:

| Campo | Offset / regra |
|---|---|
| EMR_HEADER type | `u32 LE == 1` no início candidato |
| Assinatura | bytes `20 45 4D 46` (`" EMF"`) em `+40` |
| `nBytes` da página | `u32 LE` em `+48` |
| Aceite | `nBytes > 80` e `i + nBytes <= length` |
| Varredura | após achar página, avança `i += nBytes - 1` |

Endianness: little-endian em todos os inteiros do PoC.

## Registros EMF de texto

Portado de `parseEmfTexts`:

| Campo | Valor observado |
|---|---|
| Cabeçalho do record | type `u32@0`, size `u32@4` |
| Texto | type `84` (`EMR_EXTTEXTOUTW`), `size >= 76` |
| Coordenadas | `x = i32@36`, `y = i32@40` |
| String | `chars = u32@44`, `off = u32@48` (relativo ao record); UTF-16LE |
| Fim de página | type `14` (`EMR_EOF`) encerra o walk |

## Layout InterPDV (Relatório de Auditoria)

1. Localizar `Produto: <id> - <nome>`, `Fabricante:`, `Estoque Atual:`, `Data/Hora:`.
2. Por página, registrar o `x` de cada cabeçalho:
   `Preço`, `Desconto`, `Total Item`, `Data`, `Hora`, `Saidas`, `Entradas`, `Anterior`, `Posterior`.
3. Agrupar textos por `(page, y)`.
4. Grupos com `Venda Numero: <n>` viram linhas; demais textos do grupo associam-se ao cabeçalho de menor `|x - headerX|` com tolerância **&lt; 75**.
5. Decimais BR via `brNumber` (agora `BrazilianDecimalParser` → `BigDecimal`).
6. Total declarado: texto `Total de Vendas:` seguido de valor numérico próximo (Fixture B: `52,986`).

## Fixture B — valores ouro reproduzidos pelo PoC/Java

| Métrica | Valor |
|---|---|
| Produto | `41` / `MOLHO POMODORO` |
| Páginas | 4 |
| Linhas | 134 |
| Vendas únicas | 93 |
| Entradas | 0 |
| Qtd. fonte / parseada | `52.986` / `52.986` |
| Faturamento (soma Total Item OUT) | `3013.07` |
| Última movimentação | `2026-07-19T13:07:03` |
| Venda `134409` | qtd `0.416`, preço `56.90`, desconto `8`, total `21.78` |

Hint de filename `01_07-20_07` → período incompleto dia/mês **sem ano**; não altera `lastMovementAt`.

## Proveniência

| Classe | Onde |
|---|---|
| `SOURCE_DATA` | movimentos / campos tipados do relatório |
| `CALCULATED_DATA` | somas e validações (`SOURCE_QUANTITY_MATCH`, totais de linha) |
| `INFERRED_DATA` | somente `FilenameHints` |

## Arredondamento e tolerância (provisório, observado)

- Dinheiro: escala 2, `HALF_UP`, tolerância de comparação `0.01` na validação de linha.
- Quantidade: tolerância `0.001` na comparação com `Total de Vendas`.
- PoC JS usava `Number` (drift IEEE); o backend **deve** usar `BigDecimal`.

## Incertezas ainda abertas

- Fixture A não disponível para regressão cruzada.
- Chave estável de item além de `(parse_attempt, source_record_index)`.
- Semântica oficial de devoluções / cancelamentos.
- Timezone de negócio (valores tratados como `LocalDateTime` sem offset).
- Biblioteca EMF Java vs port controlado: o port do PoC reproduz Fixture B; manter port até evidência contrária.
