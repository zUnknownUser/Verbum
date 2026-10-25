# Cinco temas aprofundados — 21/10/2026

Atualização em 24/10: API, migrações e este lote foram publicados em produção.
Veja [o registro de implantação e verificação](DEPLOY_PUBLICATION_2026-10-24.md).
O relato abaixo preserva o estado da entrega original.

## Escopo entregue

Aliança, Fé, Adoração, Obediência e Santidade, nas cinco fichas já existentes.
O catálogo continua com 80 temas; esta edição aprofunda cinco deles. São 25
leituras comentadas, 10 comparações e 15 conexões explicadas, integralmente em
português brasileiro e inglês. Cada tema apresenta uma visão geral, cinco
leituras em sequência, duas comparações e três conexões com pessoas, lugares
ou eventos existentes.

Cada leitura diferencia contexto, observação do texto, significado editorial e
pergunta específica de estudo. As comparações explicam tanto as relações quanto
as diferenças entre as passagens. O conteúdo preserva as 15 seleções anteriores e
acrescenta dez entradas: nove no percurso de Êxodo/Números e Lucas 22:14–20 na
comparação sobre aliança. Romanos 12:1–2 aparece em dois temas com ênfases distintas.
Não foram criadas novas entidades, etapas de jornada ou categorias.

## Fontes e limites editoriais

Os textos originais estão em `pipeline/sources/theme-studies/notes.json`; o
Bundle v10 reproduzível está em `catalog.json`. Todas as 32 faixas distintas de
versículos foram conferidas no corpus WEB versionado, em 22 capítulos. Os 22
links oficiais de capítulos responderam HTTP 200 a GET, incluindo o endereço
específico de Salmos (`PSA095.htm`). A edição consultada é a
[World English Bible](https://ebible.org/engwebp/), de
[domínio público](https://ebible.org/engwebp/copyright.htm).

O comentário é editorial, separado do texto bíblico. A fonte externa identifica
a edição inglesa consultada; a prévia interna usa a tradução selecionada pelo
usuário no app. Observação, interpretação e releitura posterior não são tratadas
como equivalentes. Jeremias mantém Israel e Judá como destinatários; a comparação
com Lucas não afirma uma substituição automática. Fé não é apresentada como
garantia de cura, e os textos sobre justificação não pretendem resolver toda a
discussão posterior. Santidade relaciona ritual e ética sem confundi-los.

As verificações e os limites de cada tema estão em
`pipeline/sources/theme-studies/source-review.md`. A revisão delegada de IA foi
registrada em `pipeline/review/theme-studies-2026-10-21.json`, com 44 itens e hash:

`9ac5ff9087a1da6532122d9f226b046a282759838a84e368c9d0a041746f0ca2`

Esse registro não representa revisão humana, teológica ou acadêmica independente.

## Arquitetura e experiência

A migração `0016_theme_studies.sql` adiciona tabelas de guias, leituras e fontes
vinculadas às identidades existentes. A API expõe `EntityDetail.themeStudy` como
campo opcional. Sem guia no idioma solicitado, mantém a ficha anterior daquele
idioma; não preenche a interface portuguesa com comentário inglês.

A publicação utiliza as validações, revisão por hash, transação, bloqueio entre
publicadores e recibos idempotentes existentes. Os guias preservam entidades,
catálogos, leituras antigas e relações anteriores. Conexões equivalentes não criam
arestas paralelas. Alterar texto publicado exige `previousHash` correspondente ao
estado armazenado. Esta edição bloqueia a troca de identidades das conexões em
uma revisão: isso exigirá um fluxo explícito de reconciliação, evitando deixar
arestas antigas divergentes da ficha. Atualizações de prosa continuam suportadas.

O Contexto encontra os temas pelos capítulos estudados. Ficha, contexto e busca
de evidências incluem as fontes do guia. As conexões da API resolvem as entidades
canônicas e seus nomes localizados; não há cópias editoriais de personagens.

No iOS e Android, o componente do guia aparece tanto na ficha de Explorar quanto
no estudo aberto pelo leitor. Leituras e comparações podem ser expandidas conforme
o interesse. Referências abrem uma prévia local, com carregamento, erro e nova
tentativa, sem perder o ponto do guia. Abrir uma conexão e voltar preserva o estudo;
notas ainda não salvas continuam protegidas pelo fluxo existente. É possível
abrir explicitamente a passagem no leitor principal.

Fontes, referências e entidades já explicadas não são repetidas em listas
paralelas. Leituras adicionais e evidências anteriores continuam acessíveis.
Foram reutilizados os clientes, padrões de estado, tipografia, cores, espaçamentos
e navegação do app, respeitando `App → Features → Clients → Models`. Não há rede
nas Views, geração de texto ao abrir o tema, dependência mobile nova ou alteração
do design system.

## Verificação

- iOS: compilação do app para simulador e 319 testes em 70 suítes aprovados, incluindo
  contratos reais EN/PT, resposta antiga sem guia, estado de navegação, erro e nova
  tentativa, abertura do leitor e preservação de notas.
- Android: 99 testes Clients e 148 testes Scripture aprovados; APK Debug compilado.
  Ajuste final do botão Voltar na camada visível recompilado.
- Backend: `go test -race ./...` e `go vet ./...` aprovados com PostgreSQL isolado.
  Migrações aplicadas duas vezes; ausência de idioma testada sem vazamento de texto.
- Pipeline: 102 testes aprovados e um teste opcional do snapshot externo STEP não
  executado. Os 12 testes dos temas passaram novamente após a proteção de revisão.
  Todos os cinco guias nos dois idiomas foram publicados em schemas descartáveis
  e consultados pela API Go: leituras, fontes, contexto, conexões e nomes.
- Testadas idempotência, rollback integral, identidade incompatível, revisão
  obsoleta, troca de conexão bloqueada e preservação dos hashes anteriores.
- OpenAPI, Ruff dos módulos novos e `git diff --check` válidos. Os exemplos
  `api/examples/entities/faith-study.en.json` e `.pt.json` vieram de respostas HTTP
  locais e são confrontados com a API; não são mocks inseridos no aplicativo.

## Publicação e pendências

Nenhuma alteração foi aplicada ao banco de produção nesta rodada. O relato
anterior de indisponibilidade do Railway não foi usado como confirmação do estado
atual do serviço. O push envia código; não publica os catálogos editoriais.

Implantar a API e as migrações pendentes, incluindo `0013` a `0016`, e publicar os
lotes pendentes na ordem documentada: capítulos do Êxodo, perfis de Pessoas/Lugares
e estes cinco temas. Este lote exige as identidades STEP, os 80 temas e os eventos
canônicos já existentes. De `pipeline/`, usando a conexão fornecida pelo ambiente
seguro:

```sh
.venv/bin/python -m verbum_pipeline.theme_studies
.venv/bin/python -m verbum_pipeline.cli validate sources/theme-studies/catalog.json \
  --decisions review/theme-studies-2026-10-21.json
.venv/bin/python -m verbum_pipeline.cli publish sources/theme-studies/catalog.json \
  --decisions review/theme-studies-2026-10-21.json
```

Conferir depois os cinco temas EN/PT-BR na API publicada. O cache mobile existente
pode conservar a resposta anterior por até uma hora online; offline, mantém a
última cópia disponível. Pendente: implantação/publicação e conferência em produção,
revisão humana independente das interpretações e traduções, e homologação visual,
de acessibilidade e navegação em aparelhos reais por último.

Os outros 75 temas mantêm a profundidade anterior. A expansão deles é outra
entrega e não impede fechar este recorte. Nenhum áudio pago ou trabalho de loja
faz parte desta rodada.
