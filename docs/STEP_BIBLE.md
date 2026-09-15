# STEP Bible Data no Verbum

Integração implementada em 2026-09-15. Fonte canônica dos dados estruturados importados:
[STEPBible/STEPBible-Data](https://github.com/STEPBible/STEPBible-Data).
O repositório indicado, [disciplenetwork/Bible-Data-STEP](https://github.com/disciplenetwork/Bible-Data-STEP),
é um fork desse projeto. O adaptador usa o upstream oficial, fixado no commit
`a51237d7a5f2dd2e0f26ccc7156b92ad79703ba9`; não acompanha `master` automaticamente.

## Recorte e dados incorporados

| Dataset | Registros normalizados | Campos utilizados |
|---|---:|---|
| TBESH | 11.682 | Formas hebraicas/aramaicas, transliteração, morfologia, glossa inglesa, eStrong/dStrong/uStrong. Inclui afixos e sinais classificados pelo próprio STEP. |
| TBESG | 11.034 | Formas gregas, transliteração, morfologia quando disponível, glossa inglesa e identificadores distintos. |
| TIPNR | 4.044 | Pessoas/lugares identificados, nome inglês, aliases, formas originais, identificadores e ocorrências nomeadas. |

Resultado: **26.760 registros**, **29.763 ocorrências** normalizadas e **4.648 relações**
entre entidades nomeadas e entradas lexicais. Ocorrências repetidas na mesma referência
(`49a`, `49b`) mantêm os localizadores originais; consultas por versículo deduplicam a associação.
Essas relações são `relatedTo`, não equivalência entre pessoa e palavra, nem alinhamento de tokens.

[entity-map.json](../pipeline/sources/step/entity-map.json) relaciona explicitamente 23 identidades
STEP a IDs já usados pelo app, incluindo David, Jesus, Paul, Jerusalem e Valley of Elah.
Nomes iguais nunca causam fusão automática. Por exemplo, os diferentes Johns, Marys e Bethlehems
continuam distintos. O mapeamento verifica nome único do STEP e tipo; a publicação verifica o tipo
existente e impede reatribuição silenciosa de um registro externo para outra entidade.

Os arquivos completos ficam fora do Git e do bundle mobile. O
[manifesto](../pipeline/sources/step/manifest.json) registra caminhos e SHA-256 dos três arquivos.
O [relatório reproduzível](../pipeline/sources/step/import-report.json) registra os totais e as
exclusões pontuais com linha/identificador. A ingestão não usa OpenAI nem gera embeddings pagos.

### Exclusões deliberadas

- Textos `@Briefest`, `@Brief`, `@Short`, `@Article` do TIPNR, que incluem descrições geradas por IA;
  também não importamos suas descrições narrativas, genealogias e resumos HTML.
- Grupos, seres sobrenaturais, categorias combinadas, pessoas anônimas inferidas, variantes,
  identificações incertas e formas combinadas: 213 registros e 516 sublinhas fora do recorte.
- Definições longas de ambos os léxicos; especificamente a coluna `Meaning` de TBESH tem
  restrição própria, descrita abaixo. Glossas não são definições completas nem texto bíblico.
- Uma entrada grega sem forma original; 33 vínculos lexicais não resolvidos são relatados e
  não geram uma entidade ou relação inventada.
- Quatro ocorrências de entrada referentes a `Rom.16.25`/`Rom.16.27`, ausentes do TSV WEB atual.
  Elas permanecem no relatório de exclusões; não são remapeadas para outro versículo.
- Ocorrências exclusivas da LXX e sua versificação não entram no corpus atual de 66 livros.
  O parser reconhece essa exclusão; não converte LXX em WEB por semelhança numérica.

## Arquitetura

O fluxo permanece **importação → validação/normalização → revisão → publicação transacional**.
`step.py` é um adaptador de entrada do pipeline Python existente. O `Store` Go continua sendo
a porta de leitura do conteúdo. Não há serviço STEP em tempo de execução, banco bíblico paralelo,
novo endpoint de escrita ou dependência direta das features mobile no fornecedor.

- Pessoas/lugares são `entities`; léxicos reutilizam o tipo **`originalTerm`**.
- O `Bundle` versão 2 acrescenta `enrichment` tipado. Bundles versão 1 e seus hashes de revisão
  permanecem compatíveis. O modelo rejeita misturas indevidas e referências sem evidência.
- `source_datasets` registra repositório, revisão, arquivo, hash, licença, atribuição e modificações.
  Cada registro guarda sua revisão: dados omitidos de um lote não ganham uma proveniência nova.
- `entity_source_records` guarda a identidade externa, linha, esquemas de identificadores e dados
  lexicais como extensão da entidade existente. **eStrong, dStrong e uStrong não são intercambiáveis**;
  sufixos e anotações originais são preservados.
- `entity_occurrences` associa registro de origem e localizador a um versículo OSIS validado.
  Não transforma toda ocorrência em `keyPassage` editorial ou cria um nó para cada versículo.
- `entity_passage_associations` é a projeção comum das passagens editoriais e ocorrências importadas,
  usada pelo contexto, recuperação de passagens e vínculo de entidades do Ask.
- Relações reutilizam `relationships` / `relationship_sources`. O STEP não substitui detalhes,
  datas, resumos, aliases editoriais, seleção diária ou identidades já existentes.

A migração é [0005_structured_enrichment.sql](../backend/db/migrations/0005_structured_enrichment.sql).
Os testes aplicam todas as migrações duas vezes em schemas descartáveis.

### Localização EN / PT-BR

STEP fornece aqui nomes/glossas em inglês; **não foi importada ou inventada uma tradução PT-BR**.
`entity_localizations` separa `language`, `name`, `aliases`, `description` e `source_id` da identidade
canônica e da forma lexical original. Uma tradução editorial deve possuir sua própria fonte,
sem atribuí-la ao STEP. O contrato de enriquecimento aceita registros `en` e `pt-BR`.

As rotas de entidades, grafo, contexto, timeline e busca recebem `lang=en|pt|pt-BR` (`pt` → `pt-BR`).
Selecionam o idioma solicitado, depois inglês, depois o registro legado. No mesmo idioma, uma fonte
editorial tem preferência sobre STEP; a ordem por ID resolve outros empates de forma determinística.
`nameLanguage` informa qual registro de apresentação foi escolhido. Para `originalTerm`, o nome
mantém o script original; a língua da palavra é `sourceRecords[].lexical.language` (`he`, `arc`, `grc`).
A descrição de apresentação é independente; sem descrição traduzida, um resumo editorial legado
pode continuar em inglês. Não há tradução automática silenciosa.

Os adapters iOS/Android agora enviam o idioma também em detalhe/grafo/contexto/timeline,
mantendo os mesmos clientes e caches por URL. Os campos extras de metadados são opcionais no
contrato; as telas existentes continuam consumindo seus modelos atuais. Uma UI lexical dedicada
com indicação visual do idioma de fallback é trabalho posterior.

## RAG e Ask Scripture

A busca de entidades considera nomes/aliases localizados, formas originais e identificadores
exatos. Retorna até 100 entidades por consulta. A busca de passagens combina a recuperação
lexical/semântica existente com candidatos estruturados de **nome, alias ou identificador exato**.
Um `H1732` ou um alias PT-BR revisado pode recuperar passagens das ocorrências de David, mesmo
quando a consulta não coincide com as palavras inglesas do versículo. A expansão considera até
64 entidades e respeita o limite existente de passagens. Não é extração de nomes de perguntas livres.

O Ask continua lendo o texto bíblico do corpus e validando os índices citados pelo modelo.
Os vínculos de entidade incluem as ocorrências STEP, e `sourceReferences` inclui a atribuição
STEP desses vínculos. As glossas **não** são inseridas no prompt como se fossem Escritura.
Perguntas lexicais sem passagens verificáveis continuam sujeitas à resposta sem evidência.

Metadados lexicais completos do recorte e sua proveniência estão disponíveis em
`GET /v1/entities/{id}` (`sourceRecords`, `localizations`). Isso prepara uma recuperação lexical
futura com citações de fonte próprias. Este lote não contém um corpus de tokens hebraico/grego
alinhados por versículo; não permite alegar que toda glossa ocorre em determinada passagem.

## Reprodução e atualização

A partir da raiz do projeto, obtenha uma cópia separada da fonte:

```sh
git clone https://github.com/STEPBible/STEPBible-Data.git /tmp/verbum-step-source
git -C /tmp/verbum-step-source checkout a51237d7a5f2dd2e0f26ccc7156b92ad79703ba9
```

A partir de `pipeline/`:

```sh
uv run --locked verbum-pipeline import-step /tmp/verbum-step-source \
  --output work/step/revision/bundle.json --report work/step/revision/report.json
uv run --locked verbum-pipeline validate work/step/revision/bundle.json
uv run --locked verbum-pipeline review work/step/revision/bundle.json \
  --queue work/step/revision/queue.json --decisions work/step/revision/decisions.json
```

Os comandos recusam sobrescrever artefatos. Use outro diretório para outra revisão. O `import-step`
confere SHA-256 antes do parsing e valida referências pelo TSV WEB já existente. O relatório
registra também o hash desse corpus. Uma alteração de formato desconhecida interrompe a importação.

A revisão existente cobre **também datasets e registros de enriquecimento**, com seus campos,
ocorrências, fontes e identidades. Após revisão editorial real e aplicação das migrações:

```sh
# Configure VERBUM_DATABASE_URL para o ambiente de destino antes de publicar.
uv run --locked verbum-pipeline publish work/step/revision/bundle.json \
  --decisions work/step/revision/decisions.json
```

Publicação usa o mesmo advisory lock, transação e `content_publications`. O mesmo hash retorna
`already_published`. Novos lotes substituem somente metadados/ocorrências/localizações dos registros
submetidos e da respectiva fonte. Erros desfazem o lote inteiro. Dados editoriais e traduções de
outras fontes sobrevivem. Nenhuma aprovação real é fabricada pelo importador.

Para atualizar: revisar diff, cabeçalhos/licenças e identidades no upstream; fixar novo commit e
hashes no manifesto; ajustar mapeamentos quando necessário; executar testes e comparar relatórios;
gerar nova fila e publicar após revisão. O pipeline é aditivo: **omitir registros/relações de um
lote não os retira do banco**. Retiradas ou fusões exigem uma migração editorial explícita, para
não apagar conteúdo de outra origem acidentalmente. Relações antigas devem ser consideradas nessa
revisão de atualização.

## Atribuição e licença

Atribuição: **Data created by STEPBible.org based on work at Tyndale House, Cambridge**,
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Foram selecionados campos estruturados,
normalizadas referências e omitidos campos fora do recorte. A licença, atribuição, link da fonte
fixada e descrição dessas modificações acompanham os dados persistidos/API.

O [cabeçalho TBESH](https://github.com/STEPBible/STEPBible-Data/blob/a51237d7a5f2dd2e0f26ccc7156b92ad79703ba9/Lexicons/TBESH%20-%20Translators%20Brief%20lexicon%20of%20Extended%20Strongs%20for%20Hebrew%20-%20STEPBible.org%20CC%20BY.txt)
distingue as glossas criadas por estudiosos de Tyndale da coluna `Meaning`, baseada no Abridged BDB
© Larry Pierce / Online Bible. Essa coluna exige permissão adicional segundo a própria fonte e
**não é copiada** para os artefatos, banco, API ou testes. O CC BY do arquivo não é usado para
ignorar a restrição específica da coluna. Os testes incluem marcadores que detectam sua cópia
acidental. Definições longas de TBESG também ficaram fora do recorte.

## Validação e situação de entrega

- Testes de parsing, Unicode, IDs distintos, referências, hashes, deriva de formato/identidade,
  gate de revisão, publicação, reexecução, atualização, preservação editorial/PT-BR e rollback.
- Teste opcional do snapshot oficial completo: `VERBUM_TEST_STEP_SOURCE_DIR` aponta para o clone.
- Testes Go de busca por Strong’s/aliases, contexto, grafo, fallback de idioma, metadados e Ask
  com citações verificadas/atribuição, sem chamadas pagas.
- Teste Python → HTTP Go preserva os sete exemplos de contrato anteriores.

O lote real e os arquivos editoriais estão em
`pipeline/work/step/a51237d7a5f2dd2e0f26ccc7156b92ad79703ba9/` (ignorado pelo Git).
**Lucas revisou e aprovou o lote na conversa em 15/09/2026. O conteúdo foi publicado em produção.**
A aprovação foi transcrita em `decisions.approved.json` e persistida no banco com o hash do lote;
`decisions.json` preserva o rascunho inicial. `publication.json` registra o resultado, com um recibo
resumido versionado em `pipeline/sources/step/publication-2026-09-15.json`.

A primeira tentativa perdeu a conexão e foi desfeita integralmente. A publicação com relações em
lote levou 29,8 segundos; a reexecução retornou `already_published`. Foram conferidos os 26.760
registros, 29.763 ocorrências e 4.648 relações STEP. O backup anterior foi restaurado com sucesso
em banco descartável. As migrações e o gatilho GitHub também foram verificados em produção.

A busca dos dois apps agora inclui a seção **Termos originais / Original terms**, reutilizando
os modelos, resultados e detalhe existentes. Glossas e identificadores detalhados continuam
na API; não há uma nova interface de léxico completo. Use um build atualizado do app para ver
essa seção. Um push no backend não atualiza automaticamente os aplicativos instalados.

Roteiro funcional: [QA_STEP_2026-09-15.md](QA_STEP_2026-09-15.md).
Próximo passo recomendado do MVP: completar o **Contexto editorial** (resumo fundamentado,
quem fala, destinatários, situação histórica/literária), incluindo localização PT-BR e avaliação
do percurso de leitura. A fonte STEP fornece evidências estruturadas, não substitui essa redação.
Salvos, notas, Biblioteca/Jornada e onboarding continuam pendentes na auditoria.

### Arquivos da integração

| Área | Arquivos novos/alterados |
|---|---|
| Ingestão | `pipeline/verbum_pipeline/step.py`, `cli.py`, `models.py`, `review.py`, `publish.py` |
| Fonte fixada | `pipeline/sources/step/manifest.json`, `entity-map.json`, `import-report.json` |
| Persistência/deploy | `backend/db/migrations/0005_structured_enrichment.sql`, `backend/Dockerfile`, `railway.json`, `backend/DEPLOY.md` |
| Domínio/API | `backend/internal/domain/enrichment.go`, `types.go`, `httpapi/server.go`, `api/openapi.yaml` |
| Recuperação | `backend/internal/store/language.go`, `store.go`, `postgres/postgres.go`, `context.go`, `evidence.go`, `backend/internal/ask/ask.go` |
| Idioma nos clientes | `VerbumKit/Sources/Clients/VerbumAPI/VerbumAPI+Routes.swift`, `Clients+Live.swift`; Android `clients/api/VerbumApi.kt`, `LiveClients.kt` |
| Testes | `pipeline/tests/test_step.py`, `backend/internal/store/postgres/enrichment_test.go`, `backend/internal/httpapi/language_test.go` |
| Documentação | Este documento, READMEs do backend/pipeline, `docs/ARCHITECTURE.md`, `API_ROUTES.md`, `ROADMAP.md` |

Validação realizada: **46 testes Python passaram**, incluindo publicação do snapshot oficial completo
e Python → HTTP Go; testes Go e `go vet` passaram; OpenAPI válido; builds iOS e Android passaram.
A compilação dos testes Android também passou. Conforme a orientação já registrada no roadmap,
a execução de testes mobile e QA em dispositivos permanece com o proprietário.
