# Plano de implementação — Calciolari Data Hub MVP

> Documento histórico de execução. O backend do MVP é **C# / ASP.NET Core 10** (ver `docs/decisions/0004-csharp-backend.md` e `AGENTS.md`). Trechos Java/Spring abaixo são histórico: ignore-os e implemente o equivalente em C#. Não há backend Java neste repositório.

## 1. Situação atual do repositório

Inspeção realizada em 08/08/2026:

- o repositório está na branch `main`, sem alterações locais antes da criação deste plano;
- existem apenas `README.md` e `LICENSE`;
- não há `index.html`, prova de conceito, arquivos `.QRP`, fixtures, código-fonte, testes ou arquivos de build;
- não há instruções locais adicionais (`AGENTS.md`) nem convenções de projeto já estabelecidas.

Isso contradiz a premissa do prompt de que o MVP HTML e os arquivos reais já estariam disponíveis. O Cursor **não deve inventar o formato QRP**. Antes de implementar o parser, será necessário adicionar ou fornecer, no mínimo:

1. o `index.html` da prova de conceito que já extrai os registros EMF;
2. os dois QRP reais descritos como Fixture A e Fixture B;
3. autorização para manter os arquivos no repositório, ou versões sanitizadas equivalentes;
4. hashes SHA-256 e resultados esperados revisados manualmente.

Se os arquivos reais forem sensíveis ou grandes, armazená-los fora do Git e versionar em `backend/src/test/resources/fixtures/manifest.json` apenas o nome lógico, SHA-256, tamanho, classificação e resultados esperados. Os testes deverão obter o pacote de fixtures por mecanismo documentado e falhar com mensagem clara quando ele não estiver presente.

## 2. Objetivo e resultado de negócio

Entregar um monólito modular que transforme exportações do InterPDV em dados normalizados e auditáveis da empresa, preservando o arquivo bruto e deixando o domínio preparado para outras fontes no futuro.

O fluxo mínimo de sucesso será:

```text
QRP não confiável
  -> upload multipart
  -> arquivo bruto imutável + SHA-256
  -> parser InterPDV no backend
  -> validações determinísticas
  -> Product / Sale / SaleItem
  -> PostgreSQL
  -> API JSON
  -> dashboard e auditoria mobile-first
```

O produto não será um “QRP Viewer”. Nenhum tipo específico de QuickReport deve escapar do módulo importador para o domínio, banco canônico ou frontend.

## 3. Princípios e decisões obrigatórias

1. **Parser no backend:** o frontend só envia arquivos e exibe respostas.
2. **Sem OCR e sem LLM:** extrair texto diretamente dos registros EMF e calcular resultados deterministicamente.
3. **Arquivo bruto imutável:** calcular SHA-256 sobre os bytes exatos recebidos e preservar esses bytes.
4. **Filename não é identidade:** `originalFilename` e `FilenameHints` são metadados; nunca chaves ou fontes autoritativas.
5. **Proveniência explícita:** distinguir `SOURCE_DATA`, `CALCULATED_DATA` e `INFERRED_DATA`.
6. **Dinheiro e quantidades com precisão decimal:** Java `BigDecimal` e PostgreSQL `numeric`; nunca `double`.
7. **Falha fechada:** resultado parcial ou inconsistente não pode ser publicado silenciosamente como válido.
8. **Monólito modular:** um backend e um frontend, sem microsserviços, Kafka ou Kubernetes.
9. **Evolução por evidência:** não criar abstrações para funcionalidades futuras além das duas variações já justificadas: parsers de fontes e armazenamento bruto.
10. **Mobile-first:** todas as funções principais precisam ser utilizáveis em smartphone e desktop.

## 4. Incertezas que precisam ser resolvidas

### Bloqueiam a conclusão da Fase 1

- estrutura binária exata do QRP e forma de encontrar cada página/EMF;
- tipos de registros EMF usados para texto, encoding, coordenadas e ordem de leitura;
- comportamento existente da prova de conceito HTML;
- versões de InterPDV/QuickReport representadas pelos fixtures;
- tamanho típico e máximo dos arquivos;
- presença de cabeçalhos, rodapés, quebras de página e variações de layout.

### Bloqueiam a publicação segura dos dados na Fase 3

- chave estável de uma linha/item dentro de uma venda;
- comportamento de relatórios sobrepostos com bytes diferentes contendo a mesma movimentação;
- representação de cancelamentos, devoluções, correções e entradas de estoque;
- unidade real de cada produto e possibilidade de alteração/reuso do código externo;
- regra oficial de arredondamento monetário e tolerância das validações;
- significado do estoque negativo e se deve apenas ser preservado ou também validado;
- timezone dos horários. Até confirmação, tratar o valor do relatório como `LocalDateTime`, sem inventar offset/instante.

### Decisões de produto/operação não bloqueantes para o parser

- ambiente de hospedagem e HTTPS;
- volume esperado, retenção e política de backup;
- autenticação/autorização antes de qualquer exposição fora da rede interna;
- navegadores/dispositivos mínimos suportados;
- se arquivos contêm dados pessoais, fiscais ou de pagamento.

### Atenção especial aos hints de data

`01_07-20_07` não contém ano. O `FilenameHintsParser` não deve usar o ano atual silenciosamente. Preservar mês/dia como hint incompleto ou somente transformar em `LocalDate` quando houver contexto explícito, documentado e não ambíguo (por exemplo, ano extraído do próprio relatório). O dado continuará classificado como `INFERRED_DATA`.

## 5. Arquitetura proposta

### 5.1 Organização do repositório

```text
Calciolari-Data-Hub/
├── backend/
│   ├── pom.xml
│   ├── mvnw, mvnw.cmd, .mvn/
│   └── src/
│       ├── main/
│       │   ├── java/br/com/calciolari/datahub/
│       │   │   ├── imports/
│       │   │   │   ├── application/
│       │   │   │   ├── domain/
│       │   │   │   ├── api/
│       │   │   │   └── infrastructure/
│       │   │   │       ├── interpdv/qrp/
│       │   │   │       └── storage/
│       │   │   ├── catalog/
│       │   │   ├── sales/
│       │   │   ├── analytics/
│       │   │   └── shared/
│       │   └── resources/db/migration/
│       └── test/
│           ├── java/...
│           └── resources/fixtures/
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
│       ├── app/
│       ├── features/imports/
│       ├── features/dashboard/
│       ├── features/products/
│       ├── features/sales/
│       └── shared/
├── docs/
│   ├── qrp-format.md
│   ├── api.md
│   └── decisions/
├── compose.yaml
├── .env.example
├── .gitignore
└── README.md
```

Organizar por capacidade de negócio e manter `application`, `domain`, `api` e `infrastructure` dentro de cada módulo. Evitar uma árvore global horizontal de controllers/services/repositories que espalhe cada mudança pelo projeto inteiro.

### 5.2 Módulos e interfaces

| Módulo | Interface pequena | Complexidade escondida |
|---|---|---|
| Importação | enviar arquivos, consultar job e reprocessar | hashing, deduplicação, storage, parser, estados e validações |
| Parser | `supports` e `parse` | QRP, EMF, layout InterPDV, locale, erros e métricas |
| Catálogo/Vendas | publicar e consultar dados canônicos | JPA, identidade externa, conflitos e paginação |
| Analytics | consultar resumo e séries temporais | agregações, filtros, unidades e datas |
| Raw storage | salvar, abrir e verificar artefato | filesystem agora; object storage futuramente |

Interfaces conceituais:

```java
public interface ImportParser {
    boolean supports(ParserInput input);
    ParsedImport parse(ParserInput input);
}

public interface RawFileStorage {
    StoredRawFile putIfAbsent(InputStream bytes, RawFileDescriptor descriptor);
    InputStream openVerified(String storageKey, String expectedSha256, long expectedSize);
    boolean exists(String storageKey);
}
```

`InterPdvQrpParser` será um adapter de `ImportParser`. `LocalRawFileStorage` será o primeiro adapter de `RawFileStorage`. DTOs EMF/QRP, offsets e detalhes de QuickReport ficam internos ao importador e nunca aparecem nas interfaces HTTP ou entidades canônicas.

`putIfAbsent` deve ser idempotente, atômico e nunca sobrescrever conteúdo existente. Em caso de chave já existente, a implementação verifica hash/tamanho e reutiliza o artefato; divergência vira falha de integridade. O módulo de importação não deve montar caminhos nem mover arquivos diretamente. `openVerified` detecta corrupção antes de qualquer reprocessamento.

### 5.3 Estrutura interna do parser

Manter uma única interface externa profunda e decompor internamente, conforme a evidência do PoC:

```text
InterPdvQrpParser
  -> QrpContainerReader
  -> EmfTextRecordExtractor
  -> InterPdvReportLayoutMapper
  -> InterPdvParsedImportValidator
```

Esses nomes são propostas e podem ser ajustados após a Fase 1. Não transformar cada etapa em interface pública. Só criar seams internas quando ajudarem testes ou quando houver mais de uma implementação real.

Comparar durante a Fase 1:

- portar a lógica binária comprovada pelo HTML para Java; e
- usar uma biblioteca Java mantida que exponha os registros EMF necessários.

Escolher pela capacidade de reproduzir os fixtures, impor limites e fornecer offsets/diagnósticos. Não adicionar uma biblioteca apenas porque declara suporte genérico a EMF.

## 6. Modelo de dados proposto

### 6.1 Importação e arquivo bruto

Separar cada envio do conteúdo lógico para preservar nomes diferentes sem duplicar bytes:

- `import_job`: uma operação multipart com um ou vários arquivos;
- `import_file`: uma ocorrência de upload dentro do job, com UUID, `original_filename`, hints e resultado individual;
- `raw_artifact`: conteúdo imutável, `sha256` único, tamanho, storage key e timestamps;
- `parse_attempt`: execução versionada do parser para um artefato;
- `validation_result`: validações do relatório e dos itens, com códigos e diferenças.

Campos essenciais:

```text
import_job
  id UUID PK
  status PENDING|PROCESSING|SUCCEEDED|PARTIAL_SUCCESS|FAILED
  created_at, completed_at

import_file
  id UUID PK
  import_job_id FK
  raw_artifact_id FK
  parse_attempt_id FK nullable
  original_filename
  source INTERPDV
  filename_hints JSONB nullable
  status PENDING|PROCESSING|IMPORTED|WARNING|INVALID|FAILED
  deduplicated BOOLEAN
  duplicate_of_import_file_id nullable
  created_at, completed_at

raw_artifact
  id UUID PK
  sha256 CHAR(64) UNIQUE NOT NULL
  byte_size BIGINT
  storage_key
  detected_type
  created_at

parse_attempt
  id UUID PK
  raw_artifact_id FK
  parser_name
  parser_version
  status PENDING|PROCESSING|VALID|WARNING|INVALID|FAILED
  records_found
  attempt_count
  lease_until nullable
  lease_owner nullable
  lease_generation
  started_at, completed_at
  error_summary nullable
  UNIQUE(raw_artifact_id, parser_name, parser_version, attempt_count)
  UNIQUE(raw_artifact_id, id)

artifact_publication
  raw_artifact_id PK/FK
  active_parse_attempt_id UNIQUE
  published_at
  composite FK (raw_artifact_id, active_parse_attempt_id)
    -> parse_attempt(raw_artifact_id, id)

validation_result
  id UUID PK
  parse_attempt_id FK
  code
  status VALID|WARNING|INVALID
  source_value NUMERIC nullable
  calculated_value NUMERIC nullable
  difference NUMERIC nullable
  tolerance NUMERIC nullable
  rule_version
  source_locator nullable

parsed_movement
  id UUID PK
  parse_attempt_id FK
  source_record_index
  direction OUT|IN|RETURN|UNKNOWN
  external_product_id, product_name
  external_sale_id nullable
  occurred_at
  quantity, unit_price, discount_percentage, total
  previous_stock, resulting_stock nullable
  manufacturer nullable
  source_locator
  UNIQUE(parse_attempt_id, source_record_index)
```

Cada upload, inclusive duplicado, ganha `import_file`, preservando seu `originalFilename`. O artefato e os dados canônicos não são criados novamente quando o hash já existe.

O FK composto impede publicar uma tentativa de outro artefato. O caso de uso de publicação, sob lock, só pode apontar para tentativa `VALID` ou para `WARNING` explicitamente permitido pela política versionada; repositórios ficam internos ao módulo e testes de integração devem impedir bypass dessa regra.

### 6.2 Dados canônicos

```text
product
  id UUID PK
  external_source
  external_id
  name
  unit nullable
  first_seen_parse_attempt_id FK
  created_at, updated_at
  UNIQUE(external_source, external_id)

sale
  id UUID PK
  external_source
  external_sale_id
  occurred_at TIMESTAMP WITHOUT TIME ZONE
  first_seen_parse_attempt_id FK
  created_at
  UNIQUE(external_source, external_sale_id)

sale_item
  id UUID PK
  sale_id FK
  product_id FK
  parse_attempt_id FK
  source_record_index
  quantity NUMERIC
  unit_price NUMERIC
  discount_percentage NUMERIC
  total NUMERIC
  previous_stock NUMERIC nullable
  resulting_stock NUMERIC nullable
  UNIQUE(parse_attempt_id, source_record_index)
```

Adicionar constraints de não nulidade, escalas e intervalos somente depois de confirmá-los nos fixtures. Uma sugestão inicial é `numeric(19,6)` para quantidades/estoque e `numeric(19,2)` para dinheiro, mas isso precisa ser validado contra os dados reais.

Não inventar uma chave semântica de `sale_item`. A Fase 1 deve descobrir se existe identificador/ordem estável. Até essa decisão:

- deduplicar exatamente o mesmo arquivo somente pelo SHA-256;
- unir `Sale` pelo identificador externo comprovado;
- não mesclar silenciosamente linhas de arquivos binariamente diferentes;
- detectar relatórios sobrepostos e registrar warning quando não houver regra segura de reconciliação.

A direção em `parsed_movement` também só pode vir do conteúdo. Somente records positivamente identificados como saída de venda viram `Sale`/`SaleItem`. Entradas e records desconhecidos permanecem preservados como dados normalizados da fonte e entram nas validações, mas não no faturamento; devoluções aguardam uma regra de negócio aprovada. Se o layout não permitir a distinção, documentar a limitação e bloquear publicação/analytics dependentes dela, em vez de classificar tudo como venda.

### 6.3 Proveniência

Não transformar o modelo canônico em EAV nem criar uma tabela polimórfica sem integridade referencial. No MVP, a separação é estrutural:

- `SOURCE_DATA`: colunas tipadas de `parsed_movement` e entidades canônicas ligadas por `parse_attempt_id` + `source_record_index`/locator; `Product` e `Sale` registram também a tentativa em que foram observados pela primeira vez;
- `CALCULATED_DATA`: projections de analytics não persistidas e `validation_result` com valores/rule version;
- `INFERRED_DATA`: somente `import_file.filename_hints`, fora das tabelas canônicas.

Se auditoria futura exigir lineage por atributo, criar tabelas tipadas com FKs reais por agregado, não `(entity_type, entity_id)` genérico.

Regras adicionais:

- campos extraídos do QRP são `SOURCE_DATA`;
- agregações e fórmulas versionadas são `CALCULATED_DATA`;
- filename hints são sempre `INFERRED_DATA` e ficam separados;
- inferred data não preenche nem sobrescreve silenciosamente um campo canônico ausente/conflitante;
- respostas de detalhes/admin podem expor lineage; a UI principal não precisa mostrar conceitos técnicos.

### 6.4 Reprocessamento pragmático

O MVP não precisa de event sourcing nem snapshots globais. Implementar:

1. artefato bruto imutável;
2. tentativas de parsing imutáveis por versão;
3. nova tentativa processada em staging/transação isolada;
4. somente após validação, publicar as novas linhas e trocar `artifact_publication.active_parse_attempt_id` atomicamente;
5. manter tentativas e resultados de validação anteriores para auditoria.

Todas as consultas e agregações devem considerar apenas tentativas apontadas por `artifact_publication`; listas de vendas/produtos também precisam partir de itens ativos para não exibir identidades órfãs de tentativas antigas. Consultas nunca devem enxergar metade de um reprocessamento nem somar tentativa antiga e nova. Se a substituição segura não puder ser garantida por causa de sobreposição entre arquivos, interromper a publicação e resolver a identidade antes de continuar. Rollback completo de datasets e seleção arbitrária de parser podem ficar para uma fase posterior; não são critérios de aceite atuais.

## 7. Fluxo de importação e consistência

### 7.1 Upload

1. Rejeitar no transporte apenas quantidade/tamanho acima dos limites e extensão não permitida. MIME informado pelo navegador não é confiável.
2. Gravar em arquivo temporário com nome gerado pelo servidor enquanto calcula SHA-256 sobre o stream.
3. Nunca usar `originalFilename` como caminho.
4. Persistir os bytes limitados via `RawFileStorage.putIfAbsent`; só depois inspecionar assinatura/estrutura e classificar o formato.
5. Em transação curta, criar o job, a ocorrência de upload e localizar/criar o artefato pelo hash. `LocalRawFileStorage` pode usar internamente um caminho derivado do hash, por exemplo `ab/cd/<sha256>.qrp`.
6. Em corrida de uploads idênticos, a constraint única do banco decide o artefato vencedor; ambos os jobs recebem resposta coerente.
7. Confirmar bytes duráveis antes de agendar parsing; limpar apenas temporários próprios, nunca conteúdo compartilhado pelo hash.

Uma falha entre filesystem e commit do banco pode deixar arquivo órfão, mas nunca uma linha apontando para bytes inexistentes. Criar rotina idempotente de reconciliação para verificar referências ausentes e remover órfãos somente após período de segurança. Arquivos `.QRP` limitados, porém malformados/não suportados, permanecem armazenados e auditáveis. Requests rejeitados antes da ingestão por tamanho, quantidade ou extensão não são preservados.

### 7.2 Processamento

- retornar `202 Accepted` com o ID do job e resultados iniciais por arquivo;
- processar cada arquivo independentemente para permitir `PARTIAL_SUCCESS` no batch;
- usar executor interno e estados persistidos, sem broker;
- reivindicar trabalho com lease persistido e recuperar tanto `PENDING` quanto `PROCESSING` com lease expirado após reinício;
- cada claim incrementa `lease_generation` e grava `lease_owner`; heartbeat, conclusão e publicação usam updates condicionais por ambos os valores;
- worker cujo lease expirou perde o direito de escrever/publicar, mesmo que termine depois de outro worker; testar explicitamente expiração + reclaim simultâneo;
- processamento precisa ser idempotente para suportar retry depois de crash;
- parsing não deve manter uma transação de banco aberta;
- persistência canônica de um arquivo deve ser atômica;
- arquivos `INVALID` ou `FAILED` preservam raw file, tentativa e diagnósticos, mas não publicam dados;
- decidir após os fixtures quais warnings ainda permitem publicação.

### 7.3 Duplicação

- mesmo conteúdo e mesmo nome: `deduplicated = true`;
- mesmo conteúdo e nomes diferentes: `deduplicated = true`, mesmo `raw_artifact`, ambos os nomes preservados nas ocorrências;
- conteúdo diferente e mesmo nome: arquivos distintos;
- duplicate não cria `Product`, `Sale` ou `SaleItem` novamente;
- a resposta deve referenciar a importação original e informar claramente que os dados já existiam.

O select-or-create deve ocorrer sob lock do artefato (com retry da transação que perder a constraint única). A ocorrência duplicada liga seu `import_file.parse_attempt_id` à tentativa compartilhada e acompanha seu estado:

- tentativa `PENDING`/`PROCESSING`: não agendar outra; devolver status atual;
- `VALID`/`WARNING` já publicado: devolver `IMPORTED` + `deduplicated = true`;
- `INVALID`: reutilizar o resultado determinístico e seus diagnósticos;
- `FAILED` por falha de sistema: criar nova tentativa com `attempt_count` seguinte sob o mesmo lock.

Testar uploads concorrentes em cada estado. A deduplicação é uma propriedade separada do status; não marcar sucesso terminal enquanto a tentativa compartilhada ainda estiver em andamento.

## 8. Contratos do parser e validações

### 8.1 `ParsedImport`

O resultado interno deve conter, no mínimo:

- fonte e versão do parser;
- produto extraído do conteúdo;
- linhas/movimentações normalizadas com localizador de origem;
- totais declarados pelo InterPDV, quando presentes;
- primeira e última movimentação calculadas dos registros;
- estatísticas (páginas, linhas, vendas únicas, entradas e saídas);
- lista ordenada de issues;
- `FilenameHints` separado, produzido fora do parser de conteúdo.

O parser deve ser puro quanto possível: sem banco, rede, relógio, locale ou timezone default. Mesmos bytes + mesma versão + mesma configuração devem produzir o mesmo resultado e a mesma ordem de issues.

### 8.2 Erros estruturados

Cada issue deve possuir:

```text
code
severity INFO|WARNING|ERROR|FATAL
stage CONTAINER|EMF|LAYOUT|MAPPING|VALIDATION
sourceLocator opcional
message sanitizada
```

- `FATAL`: não foi possível reconhecer/coordenar o relatório;
- `ERROR`: campo/linha obrigatória inválida; bloqueia publicação por padrão;
- `WARNING`: inconsistência explícita que pode ser mostrada ao usuário;
- exceção inesperada: tentativa `FAILED`, log técnico protegido e resposta sem stack trace.

Limitar quantidade/tamanho dos diagnósticos para que um arquivo hostil não esgote memória ou banco. Nunca logar o binário inteiro nem trechos potencialmente sensíveis sem sanitização.

### 8.3 Validações determinísticas

Implementar, quando o campo existir:

1. total de quantidade informado pelo InterPDV versus soma das quantidades interpretadas;
2. `quantity × unitPrice × (1 - discount/100)` versus total do item;
3. continuidade entre estoque anterior e posterior, sem presumir que estoque negativo seja erro;
4. contagem de registros, vendas únicas e páginas;
5. campos obrigatórios e formatos de decimal/data/hora;
6. primeira/última movimentação somente a partir dos registros encontrados.

Definir em `docs/qrp-format.md` a regra de arredondamento e tolerância confirmada pelos fixtures. Não recalcular o faturamento descartando o total informado por item: preservar o valor fonte e manter o valor calculado apenas para validação.

Exemplo de resposta de validação:

```json
{
  "code": "SOURCE_QUANTITY_MATCH",
  "status": "VALID",
  "sourceValue": "52.986",
  "calculatedValue": "52.986",
  "difference": "0"
}
```

### 8.4 `FilenameHintsParser`

- aceitar qualquer nome como entrada não confiável;
- reconhecer apenas padrões documentados e ancorados;
- retornar estrutura vazia em `AUDITORIA.QRP`, `AUDITORIA JULHO FINAL.QRP` ou qualquer caso ambíguo;
- nunca lançar erro que interrompa a importação;
- preservar o texto original;
- nunca alimentar identidade, período efetivo ou totais canônicos;
- comparar hint e source data apenas para gerar metadata/diagnóstico informativo.

## 9. API REST

Usar `/api` conforme solicitado; documentar com OpenAPI e manter DTOs separados das entidades JPA.

```text
POST /api/imports/qrp                 multipart: files[]
GET  /api/imports                     paginado
GET  /api/imports/{jobId}
GET  /api/imports/{jobId}/files/{id}  detalhes/validações/admin

GET  /api/products                    busca e paginação
GET  /api/products/{id}

GET  /api/sales                       from, to, productId, page/size
GET  /api/sales/{id}

GET  /api/dashboard                   from, to, productId opcionais
```

Planejar um endpoint administrativo de reprocessamento, mas não expô-lo na navegação principal até existir regra de autorização:

```text
POST /api/imports/files/{id}/reprocess
```

Enquanto autenticação/autorização não existir, o endpoint de reprocessamento fica desabilitado fora do perfil local e o deploy deve permanecer atrás de rede/proxy interno controlado. Antes de qualquer exposição externa, implementar autenticação, papéis mínimos (`VIEWER`, `IMPORTER`, `ADMIN`), autorização por endpoint e testes `401/403`; ausência de configuração de segurança em produção deve impedir o startup. Isso evita overengineering no primeiro incremento sem tratar a API como permanentemente pública.

Regras do contrato:

- `POST` retorna `202`, `Location` e estado por arquivo;
- duplicata é resposta de negócio bem-sucedida, não erro genérico;
- request inválido usa Problem Details (`application/problem+json`);
- paginação e ordenação têm defaults e limites máximos;
- datas são ISO-8601 no JSON e formatadas em PT-BR apenas na UI;
- dinheiro e quantidades viajam como strings decimais no JSON; o frontend formata com biblioteca decimal/`Intl` sem usar `number` para cálculos;
- `LocalDateTime` fonte viaja como string ISO sem offset e não deve ser convertido para `Date` do JavaScript até o timezone de negócio ser confirmado;
- detalhes técnicos (hash, parser version, hints) aparecem apenas no DTO de detalhe/admin;
- nunca disponibilizar download do raw file por padrão.

## 10. Analytics e definições

Calcular por query/projection no MVP; não persistir cubos ou métricas redundantes.

- **Faturamento:** soma de `SaleItem.total` fonte dos itens publicados; como apenas saídas de venda confirmadas viram `SaleItem`, entradas/unknown não participam. Devoluções ficam excluídas até existir regra aprovada de sinal/estorno.
- **Quantidade vendida:** soma de movimentos `OUT` de venda por produto/unidade; entradas/devoluções ficam separadas conforme semântica confirmada.
- **Vendas:** `count(distinct Sale.id)` no filtro aplicado.
- **Itens:** contagem de linhas publicadas.
- **Ticket médio:** faturamento / vendas únicas, quando o denominador for maior que zero.
- **Primeira/última movimentação:** `min/max(occurredAt)` dos itens efetivos.
- **Evolução diária:** quantidade e faturamento agrupados pela data local do relatório.

Não somar quantidades com unidades incompatíveis. Se houver kg e unidade, apresentar grupos separados. “Produto mais vendido” precisa dizer se o critério é faturamento ou quantidade; quantidade só pode ser comparada entre unidades compatíveis.

## 11. Frontend e PWA

### 11.1 Rotas e telas

```text
/                         Resumo
/imports                  Importação e histórico
/products                 Produtos
/products/:id             Produto e evolução temporal
/sales                    Vendas/auditoria
/sales/:id                Detalhe da venda
```

### 11.2 UX

- texto em PT-BR, contraste WCAG AA, fontes legíveis e touch targets de pelo menos 44 px;
- bottom navigation no smartphone: Resumo, Vendas, Produtos, Importar;
- desktop pode usar tabela; smartphone usa cards ou layout responsivo sem scroll horizontal obrigatório;
- desconto exibido como `8%`; dinheiro e quantidade são recebidos como strings decimais, validados por biblioteca decimal e apenas convertidos para formatação em ponto controlado, sem aritmética com `number`;
- filtros simples e persistência de filtro durante a navegação;
- estados claros de vazio, carregamento, erro, warning, duplicata e sucesso;
- upload múltiplo com progresso por arquivo, polling do job e resumo de divergências;
- gráfico somente para série útil de quantidade/faturamento por dia, com alternativa textual acessível.

### 11.3 PWA

- manifest com nome, short name, tema, display `standalone` e ícones adequados;
- service worker gerado pela integração Vite escolhida;
- cache somente do app shell e assets estáticos versionados;
- API de dados, diagnósticos e uploads permanece network-first/não cacheada por padrão;
- nenhuma tentativa de parsing ou persistência offline;
- mostrar atualização disponível e evitar frontend antigo incompatível com a API;
- validar instalação em HTTPS ou localhost e nos navegadores acordados.

## 12. Segurança, limites e observabilidade

### Segurança mínima

- configurar limites de request, quantidade de arquivos, tamanho por arquivo, bytes decodificados, páginas/registros e tempo de parsing;
- validar extensão e conteúdo detectado; não confiar em MIME/filename;
- tratar offsets, comprimentos e contadores binários com verificação de overflow e bounds;
- impedir path traversal usando somente storage keys geradas;
- não executar nada contido no arquivo;
- configurar CORS por ambiente; em produção, preferir frontend/backend same-origin;
- sanitizar mensagens e nunca retornar stack trace;
- adicionar headers de segurança e não publicar o serviço diretamente na internet sem autenticação;
- manter segredos fora do Git e documentar variáveis em `.env.example`.

Os valores exatos dos limites devem ser definidos depois de medir os fixtures. Configurá-los por ambiente e testá-los.

### Logging e métricas

Logs estruturados devem incluir:

```text
jobId, importFileId, originalFilename sanitizado, sha256,
parserName, parserVersion, status, recordCount, validationStatus, duration
```

Nunca incluir bytes completos. Adicionar health/readiness e métricas básicas para quantidade/duração de imports, duplicatas, warnings, falhas e espaço consumido pelo raw storage.

## 13. Estratégia de testes

### 13.1 Manifest dos fixtures

Para cada fixture real, registrar:

- SHA-256 e tamanho;
- nome lógico e versão/origem conhecida;
- classificação de sensibilidade;
- páginas, produto, contagens, totais e timestamps esperados;
- warnings esperados;
- revisão humana responsável pelos valores ouro.

### 13.2 Testes obrigatórios do parser

#### Fixture A — NHOQUE BATATA

- produto externo `35` e nome `NHOQUE BATATA`;
- venda `134808` em `07/08/2026 12:22:13`;
- quantidade `0.510`, preço `63.90` e total `32.59`;
- validação monetária dentro da tolerância aprovada.

#### Fixture B — MOLHO POMODORO

- produto externo `41` e nome `MOLHO POMODORO`;
- 4 páginas;
- 134 linhas;
- 93 vendas únicas;
- quantidade total `52.986`;
- faturamento `3013.07`;
- 0 entradas;
- última movimentação `19/07/2026 13:07:03`;
- `sourceTotal = 52.986`, `parsedTotal = 52.986`, diferença zero e status `VALID`;
- venda `134409`: quantidade `0.416`, preço `56.90`, desconto `8` e total `21.78`;
- o hint `20/07` não altera a última movimentação fonte de `19/07`.

### 13.3 Casos negativos e de robustez

- arquivo duplicado e corrida de dois uploads idênticos;
- mesmo conteúdo com nomes diferentes;
- arquivo inválido, truncado e bytes aleatórios;
- QRP reconhecido sem registros;
- QRP/relatório não suportado;
- falha parcial com issue explícita e sem publicação silenciosa;
- nome simples, nome com hints e nome arbitrário;
- limites de tamanho, registros e issues;
- execução repetida em locales/timezones diferentes produzindo o mesmo resultado;
- caracteres acentuados e encoding observado;
- fuzz/property tests nas leituras de comprimentos/offsets depois que a gramática estiver documentada.

### 13.4 Demais camadas

- testes unitários de regras de domínio, analytics e hints;
- testes de integração com PostgreSQL real via Testcontainers, nunca H2 para validar SQL/Flyway;
- migrations Flyway desde banco vazio;
- integração do filesystem em diretório temporário;
- MockMvc/WebTestClient para multipart, erros e paginação;
- testes de transação: parser inválido não publica linhas; falha de reprocessamento preserva o pointer anterior; sucesso troca o pointer sem janela de duplicação; reprocessamentos concorrentes são serializados;
- corrupção de raw file é detectada por hash/tamanho antes do reprocessamento;
- API persiste e devolve `sourceValue`, `calculatedValue`, `difference`, tolerância e versão da regra sem perda decimal;
- crash com lease em `PROCESSING` é recuperado sem publicação duplicada;
- frontend com typecheck, testes de interação e Playwright em viewport de smartphone e desktop;
- teste de instalação/update do PWA e garantia de que respostas de negócio/raw não entram no cache.

## 14. Plano incremental de execução

Cada fase tem um gate explícito. O Cursor não deve avançar quando o gate anterior falhar.

### Fase 0 — Preparação e recuperação dos artefatos ausentes

1. Obter/adicionar o PoC `index.html` e os QRP reais ou sanitizados.
2. Criar manifest com hashes e expectativas.
3. Confirmar política de versionamento dos binários.
4. Medir tamanho/tempo do PoC e registrar versões conhecidas.
5. Fixar versões suportadas de Java 21, Spring Boot, PostgreSQL, Node e dependências usando documentação oficial atual.

**Gate:** PoC e ao menos os Fixtures A/B estão acessíveis e seus hashes/valores esperados foram confirmados. Sem isso, parar; não criar parser fictício.

### Fase 1 — Documentar o formato observado

Arquivos principais:

- `docs/qrp-format.md`;
- fixtures/manifest;
- eventualmente um script descartável de inspeção em diretório de desenvolvimento, sem virar parser de produção.

Tarefas:

1. Ler o PoC e mapear cada operação JavaScript para estruturas binárias reais.
2. Identificar encapsulamento, páginas, records EMF, encoding, coordenadas e ordenação.
3. Mapear textos para campos sem inventar os ausentes.
4. Documentar dados fonte, calculados e hints.
5. Catalogar variações, campos opcionais, limites e formato de erro.
6. Avaliar biblioteca EMF Java versus port controlado da lógica comprovada.

**Gate:** `docs/qrp-format.md` explica como os fixtures são lidos e contém offsets/records/campos suficientes para implementar testes. Incertezas restantes estão marcadas, não preenchidas por suposição.

### Fase 2 — Parser e regressão, sem UI

1. Criar o projeto backend mínimo e testes.
2. Definir `ImportParser`, `ParserInput`, `ParsedImport`, provenance e issues.
3. Implementar `InterPdvQrpParser` somente com estruturas observadas.
4. Implementar `FilenameHintsParser` independente e opcional.
5. Implementar validações de totais e itens.
6. Adicionar limites defensivos e parser fingerprint/version (`interpdv-qrp-v1`).
7. Executar todos os testes positivos, negativos e determinísticos.

**Gate obrigatório:** Fixtures A/B, todos os testes da seção 13.2 e os casos da seção 13.3 que pertencem ao parser puro passam. Fixture B precisa atingir exatamente os valores de aceite. Não iniciar PostgreSQL/API/frontend antes disso; testes das demais camadas são gates das fases correspondentes.

### Fase 3A — Persistência, raw storage e deduplicação

1. Resolver e registrar, com base nos fixtures, identidade de produto/venda/item, direção das movimentações, conflito e sobreposição entre relatórios.
2. Adicionar PostgreSQL, JPA, Bean Validation, Flyway e Testcontainers.
3. Criar as migrations iniciais para importação, artefato, tentativa, publicação ativa, movimentos fonte, produto, venda, item e validação.
4. Implementar `LocalRawFileStorage` com raiz configurável, `putIfAbsent` no-clobber, verificação de integridade e volume no Compose.
5. Implementar stream + SHA-256 + temporário e finalização atômica dentro do adapter.
6. Implementar dedup concorrente por constraint única.
7. Definir transações por arquivo, leases e cleanup/recovery.

**Gate:** decisões de identidade/reconciliação estão documentadas; caso ainda não haja identidade segura, a publicação é explicitamente bloqueada. Mesmo conteúdo, inclusive com nomes diferentes e uploads concorrentes, gera um artefato e um conjunto de fatos; todos os filenames enviados permanecem auditáveis; raw bytes sobrevivem a reinício e corrupção é detectada.

### Fase 3B — Casos de uso e API

1. Implementar criação/execução/consulta de jobs com vários arquivos.
2. Persistir o resultado normalizado somente após validação permitida.
3. Implementar queries de produtos, vendas e dashboard.
4. Implementar DTOs, paginação, Problem Details e OpenAPI.
5. Implementar publicação via `active_parse_attempt_id` e reprocessamento mínimo, transacional e serializado.
6. Implementar worker com lease e recuperação de crash.
7. Adicionar logs, health e métricas.
8. Atualizar Compose e README com setup local, storage, backup e comandos.

**Gate:** fluxo HTTP completo importa os dois fixtures, preserva raw, reporta validação/duplicata, consulta os dados esperados e não publica arquivos inválidos.

### Fase 4 — Frontend mobile-first

1. Criar React + TypeScript + Vite com checks estritos.
2. Implementar shell, navegação e client HTTP tipado.
3. Implementar importação múltipla, progresso/polling e resultados.
4. Implementar resumo, produtos, produto/gráfico, vendas e detalhe.
5. Aplicar formatação PT-BR, acessibilidade, estados de erro/vazio e layouts responsivos.
6. Adicionar testes de interação e end-to-end.

**Gate:** os critérios de aceite aplicáveis podem ser exercitados no navegador de smartphone e desktop, exceto instalação PWA, que pertence à Fase 5.

### Fase 5 — PWA e acabamento operacional

1. Adicionar manifest, ícones e service worker.
2. Configurar cache seguro do app shell e atualização.
3. Validar installability em HTTPS/localhost.
4. Rodar auditoria de responsividade, acessibilidade e desempenho.
5. Testar backup/restore do PostgreSQL e diretório de raw files como uma unidade lógica.
6. Fixar CORS, limites e variáveis por ambiente.
7. Confirmar bloqueio de exposição externa sem autenticação; se o deploy deixar a rede controlada, implementar papéis e testes de autorização antes da liberação.

**Gate:** frontend instalável onde suportado, sem parsing offline e sem cache indevido de dados; restore recupera raw + dados/metadados consistentes.

## 15. Ordem sugerida de entregas revisáveis

Manter mudanças pequenas e validar cada uma antes da próxima:

1. artefatos + `docs/qrp-format.md`;
2. scaffold backend + contratos do parser + manifest de fixtures;
3. extração QRP/EMF + testes ouro;
4. mapping InterPDV + validações + hints;
5. Flyway/modelo canônico/Testcontainers;
6. raw storage + hash/dedup + concorrência;
7. import jobs + API de importação;
8. APIs de query + analytics;
9. shell frontend + importação;
10. dashboard/produtos/vendas responsivos;
11. PWA, segurança e documentação operacional.

Para cada entrega, revisar o diff real, executar testes relevantes, verificar migrations e preservar alterações não relacionadas.

## 16. Critérios de aceite rastreáveis

| Critério | Evidência planejada |
|---|---|
| Upload no smartphone | Playwright mobile + teste manual no dispositivo acordado |
| Parser backend | teste HTTP e ausência de parser QRP no bundle frontend |
| Raw preservado | integração de storage + reinício/restore |
| Dedup por conteúdo | testes mesmo hash/nomes diferentes/concorrência |
| Vendas normalizadas | queries de `Product`, `Sale`, `SaleItem` sobre os fixtures |
| Validação InterPDV | teste ouro `52.986 == 52.986`, status `VALID` |
| Dashboard correto | API/UI com `3013.07`, `52.986`, 93 e 134 no Fixture B |
| Detalhes de venda | teste da venda `134409` e fluxo UI |
| Desktop e mobile | matriz Playwright + inspeção responsiva |
| PWA | manifest/service worker/installability audit |
| Filename não autoritativo | testes de nomes simples, hints, arbitrários e conflitantes |
| Nova fonte possível | domínio/API sem tipos QRP e teste arquitetural de dependências |
| QRP ingerido com conteúdo inválido | issue estruturada, raw preservado e zero publicação canônica; rejeições de transporte por tamanho/quantidade/extensão ficam fora desse critério |
| Reprocessamento | nova tentativa/version e substituição atômica após sucesso |

## 17. Definition of Done do MVP

O MVP somente está concluído quando:

- todos os testes obrigatórios, build, lint e typecheck passam;
- migrations funcionam em PostgreSQL vazio;
- os valores ouro dos dois fixtures são reproduzidos;
- dedup, raw storage, falha parcial e reprocessamento têm testes;
- nenhum dado inferido substitui dado fonte;
- API e UI não expõem detalhes QRP como modelo do negócio;
- dashboard não agrega unidades incompatíveis;
- upload e consulta funcionam nos viewports acordados;
- PWA é instalável onde suportado e não promete parsing/offline de dados;
- README documenta setup, comandos, storage, limites, backup/restore e limitações;
- Git diff/status foram revisados e alterações alheias preservadas;
- riscos residuais e questões não resolvidas estão documentados, não escondidos em defaults.

## 18. Fora de escopo

Não implementar Stone, bancos, Excel/CSV, ERP completo, produção, compras, funcionários, IA generativa, previsão, automação do InterPDV, app nativo, microsserviços, event streaming, Kafka, Kubernetes ou parsing offline. O desenho apenas mantém o seam de `ImportParser` e o modelo canônico independentes do InterPDV para que fontes futuras possam ser adicionadas sem remodelar o produto inteiro.
