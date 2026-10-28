# Orientações dos 66 livros

Publicado em produção em 28/10/2026. Ver [relatório de deploy e publicação](../../../docs/DEPLOY_PUBLICATION_2026-10-28.md) e `publication-2026-10-28.json`.

Escopo fechado: 39 livros do AT e 27 do NT, com apresentações `en` e `pt-BR`. Cada ficha contém gênero, contexto literário, foco de leitura, estrutura do livro inteiro e até três capítulos de entrada. Não amplia eventos, temas, perfis ou estudos de capítulos.

`notes.psv` é a fonte editorial editável. `catalog.json` é o lote versionado gerado e validado. Os textos são sínteses editoriais originais produzidas com assistência de IA; não constituem revisão humana independente. A base citada é o próprio livro bíblico, na World English Bible, de domínio público. As fichas não afirmam datas de composição ou autorias disputadas como fatos. As divisões são propostas de leitura, não divisões editoriais universais.

A fonte externa está em inglês e isso é informado nas duas interfaces. As ações de leitura usam referências estruturadas e a tradução selecionada no leitor; as fontes externas não substituem a Bíblia em português.

## Arquitetura e disponibilidade

- Migração aditiva `0017_book_orientations.sql`: tabelas de apresentações e fontes; nenhum conteúdo anterior removido.
- `GET /v1/passages/{book}.{chapter}/context`: novo campo opcional `bookOrientation`, separado de `study`. A orientação pode existir mesmo sem entidades ou estudo do capítulo. Idiomas ausentes não emprestam inglês silenciosamente.
- Modelos puros; decodificação em Clients; apresentação e navegação em Features. iOS e Android reutilizam as superfícies de contexto e estudo, tokens e ações existentes.
- Estrutura e leituras abrem capítulos no fluxo existente. Não há geração ao vivo, novo serviço, cobrança ou nova aba.
- Apps anteriores ignoram o campo aditivo; apps novos continuam decodificando respostas antigas sem orientação. A migração e a publicação do lote no backend foram concluídas em 28/10/2026; a exibição requer o build atualizado dos apps.

## Gerar e conferir

Na raiz do repositório:

```sh
PYTHONPATH=pipeline pipeline/.venv/bin/python -m verbum_pipeline.book_orientations
pipeline/.venv/bin/python -m pytest pipeline/tests/test_book_orientations.py
```

Os testes de integração requerem um banco **local de teste**, `VERBUM_TEST_DATABASE_URL`, e o binário local `VERBUM_TEST_API_BINARY`. Não usar produção. Cobrem publicação idempotente, rollback integral, proteção contra substituição de edição mais recente, preservação de entidades existentes e HTTP em ambos os idiomas no primeiro e último capítulo de cada livro.

O pipeline mantém o fluxo de revisão ligado ao hash exato. Não foi fabricada aprovação editorial. A publicação continua separada da geração do catálogo e foi autorizada explicitamente por Lucas após esta etapa local. Homologação manual em dispositivos permanece para o final do escopo combinado.

## Fontes e limites da verificação

Os 66 endereços seguem os índices de livros de https://ebible.org/engwebp/. Referências e limites de capítulos foram conferidos contra o corpus WEB incluído no projeto. Os índices de Gênesis, Êxodo, Cantares, Joel, Hebreus e Apocalipse foram abertos pela ferramenta de navegação. A sondagem automatizada com urllib recebeu HTTP 403 em parte dos endereços; portanto não certifica disponibilidade externa universal. O acesso às leituras dentro do app não depende dessas páginas externas.

A revisão de estilo e equivalência dos textos não substitui a homologação editorial humana. Não foram produzidos os 12 estudos ou 12 perfis da próxima etapa.

## Resultado local — 28/10/2026

- Lote: 66 fichas, 132 apresentações, 175 seções estruturais e 186 leituras de entrada (mesmas referências nos dois idiomas).
- Hash do lote: `e75c823b9dddf85481c0da3efe02594ff55ea638047f3c6600ecb54f09bf1756`.
- Pipeline completo: 114 testes passaram; um teste opcional do dataset STEP integral foi pulado porque `VERBUM_TEST_STEP_SOURCE_DIR` não estava configurado.
- Backend: `go test ./...` com PostgreSQL local e `go vet ./...` passaram.
- Android: testes de Clients e Scripture, contrato novo e `assembleDebug` passaram.
- iOS: build do app passou; suíte nova BookOrientationTests (dois casos de idioma e compatibilidade legada), ContextFeatureTests e VerseStudyFeatureTests passaram no simulador.
- Lint dos novos módulos Python e `git diff --check` passaram.
- Revisão inicialmente preparada como pendente; a autorização posterior de publicação consta em `pipeline/review/book-orientations-2026-10-28.json`. Nenhuma revisão humana independente foi simulada.
- A etapa de testes locais não alterou produção. O deploy e a publicação posteriores estão registrados abaixo; permanecem revisão humana independente e homologação manual ao final.

## Autorização de publicação — 28/10/2026

Após a entrega local, Lucas autorizou explicitamente nesta conversa o commit, push, deploy e publicação destas 66 orientações. Essa autorização do lote foi registrada em `pipeline/review/book-orientations-2026-10-28.json`, substituindo as decisões pendentes sem alterar o conteúdo nem seu hash. A autorização de publicação não é apresentada como revisão humana independente de cada afirmação. Deploy, migração e publicação foram confirmados. O recibo registra 66 livros, 132 apresentações, 144 respostas HTTP verificadas e preservação das contagens anteriores do catálogo.

Checks remotos do commit implantado: iOS, Android e backend/conteúdo passaram no [run 40760276991](https://github.com/zUnknownUser/Verbum/actions/runs/40760276991).
