# Inventário de código sem uso

Data: 16/09/2026. Base examinada: `5fc4f664da4358040749faaa532f8f28f5b6d144`.

**Status: limpeza autorizada posteriormente pelo usuário e aplicada após revalidação.**

## Resultado da limpeza

Base da revalidação: `0ed6d37`. Os achados originais abaixo ficam preservados como histórico.

- CM-01 a CM-03: removidos o helper `Result.mapError`, o identificador de cancelamento não usado e a ação intermediária `verseTapped`.
- CM-04 e CM-05: removidos os fluxos inacessíveis de seleção/cópia, seu estado, formatadores e clientes de clipboard, incluindo a injeção Android. Os dois testes exclusivos do formatador eliminado foram retirados junto com a implementação. O teste Android de estudo do versículo agora aciona `StudyVerse`, exatamente como a interface atual, mantendo suas asserções.
- CM-06: removida somente a cadeia antiga de entrada em Contexto pelo leitor. Contexto pela exploração guiada continua ativo.
- CM-07: removidos os tokens listados, após nova busca confirmar ausência de consumidores.
- CM-08 e APIs da seção 5: preservados, por não serem remoções inequivocamente seguras. O estado de falha de anotações merece uma decisão funcional separada.

Leitura, estudo, destaques, notas, áudio, regras de custos e compatibilidade iOS 17 continuam fora do escopo da remoção. Não houve alteração no backend ou no pipeline.

## Validação da limpeza

- Android: `:app:assembleDebug` concluído. Nos testes selecionados de ChapterReader, ChapterNavigation, Scripture e AudioPlayer, 18 de 19 passaram.
- iOS: build do app no simulador iPhone 17 Pro / iOS 27 concluído. Nos testes selecionados de ChapterReader, ChapterNavigation, Scripture e AudioPlayer, 19 de 21 passaram.
- As falhas em `titleOpensTheShelfAndAChapterClosesItIntoTheReader` (ambos) e `steppingChaptersInReaderMovesTheShelfMarker` (iOS) foram reproduzidas em checkout isolado de `0ed6d37`, antes da limpeza, com as mesmas divergências de estado de navegação. Permanecem pendências anteriores; não foram alteradas para tornar a suíte verde.
- O runner de testes iOS da comparação terminou de relatar os cinco testes e ficou preso no encerramento; foi interrompido após registrar as duas falhas (12 ocorrências), iguais às da versão modificada.
- `git diff --check` e nova busca por referências aos símbolos removidos sem pendências.

## Auditoria original

O pedido inicial foi localizar e documentar os achados, deixando a limpeza para depois.

## Escopo e limites

Revisão estática de referências em `Verbum`, `VerbumKit/Sources`, `VerbumKit/Tests`,
Android, backend Go e pipeline Python. Foram cruzados declarações, chamadas, ações
emitidas pela interface, consumidores em testes, previews, manifesto Android e
decoradores do pipeline. As linhas abaixo se referem ao commit indicado acima.

A ausência de uma chamada textual não foi considerada suficiente para classificar
callbacks, implementações de protocolos, modelos serializados ou entradas de ferramentas
como mortos. Não foram executados builds, testes, serviços ou operações de publicação
nesta auditoria. Não foi utilizado um analisador semântico de alcance como Periphery;
este documento não representa uma prova exaustiva sobre todos os caminhos de execução.

Classificações:

- **Sem uso confirmado no código atual:** declaração sem referências ou ação sem
  emissor na aplicação e nos testes examinados.
- **Sem caminho pela interface atual:** implementação existe, mas sua entrada não é
  disparada pelas telas. Partes de sua infraestrutura podem continuar executando.
- **Somente testes:** consumidores existem; a utilidade da API precisa ser avaliada.
- **Requer decisão funcional:** pode ser funcionalidade incompleta, não algo a apagar.

## 1. Declarações sem uso confirmado no iOS

### CM-01 — Extensão local de `Result.mapError`

- Local: [ChapterReaderFeature.swift](../VerbumKit/Sources/Features/Scripture/ChapterReaderFeature.swift), linhas 289–296.
- Símbolo: `fileprivate func mapError` na extensão `Result where Failure == any Error`.
- Evidência: a busca por `mapError` nos fontes e testes Swift encontra somente essa
  declaração. A visibilidade `fileprivate` também restringe qualquer chamada a esse arquivo.
- Interpretação: helper remanescente, sem consumidor atual.

### CM-02 — Caso `ChapterReaderFeature.CancelID.load`

- Local: [ChapterReaderFeature.swift](../VerbumKit/Sources/Features/Scripture/ChapterReaderFeature.swift), linha 286.
- Evidência: nesse reducer, apenas `CancelID.annotations` é referenciado. O carregamento
  de capítulos usa a chave do capítulo em `.cancellable(id: key, ...)`.
- Interpretação: caso de enum sem uso. O método `load(_:)` continua ativo e não faz parte
  deste achado. Outros reducers têm seus próprios `CancelID.load`, que são utilizados.

### CM-03 — Ação antiga `verseTapped`

- Local: [ChapterReaderFeature.swift](../VerbumKit/Sources/Features/Scripture/ChapterReaderFeature.swift), linhas 87 e 220–221.
- Evidência: existem a declaração e o tratamento que encaminha para `studyVerse`, mas
  não foi encontrado emissor dessa ação nos fontes ou testes Swift.
- Caminho ativo: [ChapterReaderView.swift](../VerbumKit/Sources/Features/Scripture/ChapterReaderView.swift), linha 235,
  emite `studyVerse` diretamente.
- Interpretação: entrada intermediária antiga, sem consumidor no iOS atual.

## 2. Fluxos sem caminho pela interface atual

### CM-04 — Selecionar, limpar seleção e copiar versículos no iOS

- Locais: [ChapterReaderFeature.swift](../VerbumKit/Sources/Features/Scripture/ChapterReaderFeature.swift),
  linhas 15, 52–63, 88–89 e 223–229;
  [ChapterNavigation.swift](../VerbumKit/Sources/Features/Scripture/ChapterNavigation.swift), linha 28;
  [PasteboardClient.swift](../VerbumKit/Sources/Clients/PasteboardClient.swift).
- Evidência: `clearSelectionTapped` e `copySelectionTapped` aparecem apenas nas
  declarações e nos respectivos tratamentos. Nenhuma tela ou teste Swift emite essas ações.
- Dependências desse caminho: `selectionCitation`, `selectedText`, `SelectionFormatter`
  e a chamada `pasteboard.copy`. Não foi localizado outro consumidor de `pasteboard`
  no código de produção.
- Limite da conclusão: `selectedVerses` ainda é preenchido quando chega um capítulo
  com versículos solicitados e é limpo em saltos de navegação. Portanto, há trabalho
  executado nesse estado, embora o fluxo de cópia não seja alcançado pela interface atual.
  `SelectionFormatter` possui testes e deve ser tratado junto com eles numa futura decisão.
- O destaque atual de uma passagem utiliza `requestedVerses`; ele não deve ser
  confundido com o antigo estado de seleção.

### CM-05 — Mesmo fluxo antigo de cópia no Android

- Local: [ChapterReaderFeature.kt](../android/feature/scripture/src/main/kotlin/com/nexussoft/verbum/feature/scripture/ChapterReaderFeature.kt),
  linhas 15, 39–40, 61 e 163–164.
- Evidência: `ClearSelectionTapped` e `CopySelectionTapped` não possuem emissores nos
  fontes ou testes Kotlin examinados. Permanecem apenas a declaração e o tratamento.
- Dependências desse caminho: `selectedVerses`, `selectionCitation`, `selectedText`,
  `SelectionFormatter` e a operação `clipboard.copy`.
- Limite da conclusão: a infraestrutura de clipboard ainda é construída/injetada pelo
  app, e `selectedVerses` recebe atualizações. Não se trata de arquivos inteiros sem execução.
  O formatter também possui testes.
- Diferença em relação ao iOS: `VerseTapped` ainda é enviado por
  [ChapterReaderFeatureTest.kt](../android/feature/scripture/src/test/kotlin/com/nexussoft/verbum/feature/scripture/ChapterReaderFeatureTest.kt),
  linha 44. Nas telas, `ChapterReaderPane` envia `StudyVerse` diretamente.

### CM-06 — Entrada antiga de contexto a partir do leitor, nos dois apps

- iOS: `contextTapped` em [ChapterReaderFeature.swift](../VerbumKit/Sources/Features/Scripture/ChapterReaderFeature.swift),
  linhas 96 e 248–249; encaminhamento em
  [ScriptureFeature.swift](../VerbumKit/Sources/Features/Scripture/ScriptureFeature.swift), linhas 77–78.
- Android: `ContextTapped` em [ChapterReaderFeature.kt](../android/feature/scripture/src/main/kotlin/com/nexussoft/verbum/feature/scripture/ChapterReaderFeature.kt),
  linhas 72 e 190; encaminhamento em
  [ScriptureFeature.kt](../android/feature/scripture/src/main/kotlin/com/nexussoft/verbum/feature/scripture/ScriptureFeature.kt), linha 87.
- Evidência: nenhuma tela ou teste examinado dispara essas ações do leitor.
- **Escopo restrito:** a tela de Contexto continua acessível pela exploração guiada.
  `GuidedExplorationView` e `GuidedExplorationPane` emitem seus próprios delegates
  `openContext`/`OpenContext`. Não classificar a tela, os clientes ou toda a navegação
  de Contexto como mortos.

## 3. Tokens visuais sem consumidores no repositório

### CM-07 — Tokens iOS não referenciados

Não foram localizadas referências aos seguintes membros nos fontes, testes ou previews
Swift. Também foram examinadas as extensões dos respectivos namespaces para distinguir
uso interno sem qualificação de ausência real de consumidor.

| Arquivo | Símbolos | Linhas |
| --- | --- | --- |
| [Palette.swift](../VerbumKit/Sources/DesignSystem/Tokens/Palette.swift) | `background`, `surfaceElevated`, `fill`, `foreground`, `separator` | 13, 22, 24, 29, 32 |
| [Typography.swift](../VerbumKit/Sources/DesignSystem/Tokens/Typography.swift) | `largeTitle`, `title`, `callout` | 13, 14, 17 |
| [Radius.swift](../VerbumKit/Sources/DesignSystem/Tokens/Radius.swift) | `xl` | 10 |
| [Motion.swift](../VerbumKit/Sources/DesignSystem/Tokens/Motion.swift) | `quick` | 16 |

`Motion.Duration.quick` (linha 10) é utilizado somente para construir `Motion.quick`.
Ele integra a mesma cadeia sem consumidor externo, não é uma declaração isoladamente
sem referências. Outros tokens de `Motion` são utilizados.

Esses tokens são públicos. Sua ausência de uso no app atual não determina se devem
continuar como parte do vocabulário do design system; isso é uma decisão posterior.

## 4. Estado escrito sem consumidor funcional identificado

### CM-08 — `annotationLoadFailed`, nos dois apps

- iOS: [ChapterReaderFeature.swift](../VerbumKit/Sources/Features/Scripture/ChapterReaderFeature.swift),
  linhas 28 e 127–128.
- Android: [ChapterReaderFeature.kt](../android/feature/scripture/src/main/kotlin/com/nexussoft/verbum/feature/scripture/ChapterReaderFeature.kt),
  linhas 27 e 109–110.
- Evidência: o campo recebe `true` na falha e `false` no sucesso, mas nenhuma tela,
  regra de negócio ou asserção de teste consultada o lê explicitamente. Participação
  em igualdade/observação gerada do estado não significa tratamento funcional do erro.
- **Não recomendar remoção automática:** pode faltar apresentar o erro ou permitir
  nova tentativa de carregar as anotações. A decisão é entre completar esse comportamento
  e eliminar o estado redundante.

## 5. Usados somente por testes: avaliar separadamente

Os seguintes símbolos possuem consumidores em testes, mas não foi localizado consumidor
no app em produção. Não estão na lista de remoção confirmada.

| Símbolo | Declaração | Evidência de uso |
| --- | --- | --- |
| `VerbumAPI.dailyVerses` | [VerbumAPI+Routes.swift](../VerbumKit/Sources/Clients/VerbumAPI/VerbumAPI+Routes.swift):92 | `ContractTests.swift`, `VerbumAPITests.swift` |
| `VerbumApi.dailyVerses` | [VerbumApi.kt](../android/core/clients/src/main/kotlin/com/nexussoft/verbum/clients/api/VerbumApi.kt):262 | `ContractTest.kt`, `VerbumApiTest.kt` |
| `BibleClient.bundledChapters` | [BibleClient+Bundled.swift](../VerbumKit/Sources/Clients/BibleClient+Bundled.swift):15 | `BibleClientTests.swift` |
| `HelloAOBooks.osisByUSFM` | [HelloAOBooks.swift](../VerbumKit/Sources/Clients/HelloAO/HelloAOBooks.swift):18 | `HelloAOClientTests.swift` |
| `DailyVerseFeature.State.morningsActive` | [DailyVerseFeature.swift](../VerbumKit/Sources/Features/DailyVerse/DailyVerseFeature.swift):29 | `DailyVerseFeatureTests.swift` |
| `BookPickerFeature.State.oldTestament` / `newTestament` | [BookPickerFeature.swift](../VerbumKit/Sources/Features/Scripture/BookPickerFeature.swift):25–26 | `BookPickerFeatureTests.swift` |
| `ChapterReaderFeature.State.canGoToNextChapter` / `canGoToPreviousChapter` | [ChapterReaderFeature.swift](../VerbumKit/Sources/Features/Scripture/ChapterReaderFeature.swift):49–50 | `ChapterReaderFeatureTests.swift` |
| `PassageReference.isWholeChapter` | [PassageReference.swift](../VerbumKit/Sources/Models/PassageReference.swift):17 | `PassageReferenceTests.swift` |

Uma API pode ser mantida para representar um contrato ou permitir inspeção em testes.
Excluir seus consumidores de teste apenas para justificar uma remoção não faz parte
deste diagnóstico.

## 6. Falsos positivos e áreas sem achado confirmado

- `TokenGallery.swift` e `TokenGalleryPreview()` são referências visuais de desenvolvimento;
  ausência de navegação no app não torna esses previews mortos.
- `makeUIView`, `updateUIView`, `makeCache`, `updateCache` e `placeSubviews` são pontos
  de entrada de protocolos SwiftUI, não funções que precisam de chamadas explícitas do app.
- `VerbumApp` é a entrada Swift marcada com `@main`. `VerbumApplication` está registrada
  em `android/app/src/main/AndroidManifest.xml`. Ambas devem ser preservadas.
- Callbacks Android como `onGetSession`, `onPlaybackStateChanged`, `onIsPlayingChanged`,
  `onPlayerError`, `onClosed` e `removeEldestEntry` são implementações de contratos do framework.
- Os métodos `verse_range`, `chronology`, `single_verse`, `valid_fields` e `serialize_bundle`
  em `pipeline/verbum_pipeline/models.py` possuem decoradores `model_validator` ou
  `model_serializer`. A invocação é feita pelo Pydantic.
- A entrada `verbum_pipeline.cli:main` está registrada em `pipeline/pyproject.toml`.
- `statusRecorder.Unwrap` em `backend/internal/httpapi/server.go` expõe o writer encapsulado
  para consumidores que reconheçam esse contrato. Uma busca textual sem chamada não basta
  para concluir que é removível.
- Não foi confirmado código morto adicional no backend Go ou no pipeline Python nesta
  varredura. Isso não comprova a ausência de outros casos.
- As alternativas de interface e leitura para iOS 17 em `ReaderCompatibility.swift`
  possuem caminhos condicionais de execução; não são mortas por não rodarem no iOS 27.
- Entradas `stale` no catálogo de strings são indícios de recursos a revisar, não prova
  isolada de código morto. O catálogo não foi alterado.

## 7. Registro para uma possível limpeza futura

Este era o registro do diagnóstico inicial, antes da autorização posterior de limpeza. Para novas remoções,
revalidar as referências no commit então vigente e tratar separadamente helpers isolados,
ações antigas, estado sem consumidor, tokens públicos e APIs utilizadas por testes.
Preservar os caminhos ativos de estudo do versículo, destaque de passagens, Contexto e
compatibilidade com iOS 17. Não há estimativa de ganho de tamanho ou desempenho: a análise
não mediu o binário, e código sem consumidor no fonte pode já ser eliminado na compilação.

Comandos de referência usados na inspeção (executar a partir da raiz do repositório):

```sh
rg -n '\bmapError\b' VerbumKit Verbum --glob '*.swift'
rg -n 'CancelID|cancellable' VerbumKit/Sources/Features/Scripture/ChapterReaderFeature.swift
rg -n '\b(verseTapped|clearSelectionTapped|copySelectionTapped|contextTapped|annotationLoadFailed)\b' VerbumKit/Sources VerbumKit/Tests
rg -n '\b(VerseTapped|ClearSelectionTapped|CopySelectionTapped|ContextTapped|annotationLoadFailed)\b' android --glob '*.kt'
rg -n 'Palette\.(background|surfaceElevated|fill|foreground|separator)\b|Typography\.(largeTitle|title|callout)\b|Radius\.xl\b|Motion\.quick\b' VerbumKit/Sources VerbumKit/Tests Verbum --glob '*.swift'
```
