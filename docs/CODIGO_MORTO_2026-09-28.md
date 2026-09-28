# Revalidação de código sem uso e pontos de atrito

Base inspecionada: `bc93347`, após Biblioteca/Jornada, player, RAG, adaptação de
largura e Swagger. Esta revisão complementa [o inventário anterior](CODIGO_MORTO_2026-09-16.md).

## Método e alcance

Varredura de declarações e referências nos fontes e testes Swift, Kotlin, Go e
Python; conferência manual dos candidatos, do caminho ativo de áudio, das telas
substituídas e dos contratos. Comparação com os achados anteriores e execução
dirigida de build/testes. Não houve chamada paga, mudança no banco ou deploy.

A contagem de referências foi usada para encontrar candidatos, não como prova
automática. Callbacks de frameworks, previews, entrypoints, métodos de protocolos,
serializadores, testes e recursos carregados dinamicamente exigem interpretação.
Não se afirma prova formal de ausência de todo código morto ou de toda fricção.

## Remoções confirmadas

| Item | Evidência anterior à remoção | Limite da limpeza |
|---|---|---|
| `EmptyPage`, Android | Só a declaração em `ui/HomeScreen.kt`; Biblioteca/Jornada já usam `ReadingCollectionScreen` | Removida a tela antiga e seu import exclusivo `width`. O helper `Page` permanece, usado por Home e Explore |
| `synthesizeChapterSpeech`, Swift | Só a declaração em `VerbumAPI+Routes.swift`; `NativeSpeechAudio` chama `startSpeechPlayback` | Removido o método e os dois DTOs privados exclusivos (`SpeechVerse`, `TimedSpeechRequest`) |
| `postTimedAudio`, Swift | Único consumidor era o método acima; nenhum teste chamava o helper | Removida somente a cadeia sem consumidores |
| `synthesizeChapterSpeech`, Kotlin | Só a declaração em `VerbumApi.kt`; `NativeScriptureAudioClient` usa a sessão progressiva | Removido o método. `WireTimedSpeech`, `sendBinaryResponse` e `decodeAudioCues` permanecem: atendem caminhos ativos |

A rota backend `POST /v1/tts`, o Swagger, os arquivos de áudio em cache, os controles
de reprodução e a implementação progressiva permanecem ativos. Não foram removidos
testes para fazer uma declaração parecer sem uso. Não há medição de redução do
binário ou ganho de desempenho decorrente dessa limpeza.

## Fricções confirmadas, sem classificar como código morto

### F-01 — Falha de anotações sem apresentação no leitor

Em ambos os `ChapterReaderFeature`, `annotationLoadFailed` recebe sucesso/falha,
mas nenhuma view ou regra de interação lê o campo. O usuário pode ver o capítulo
sem suas marcações e não receber explicação ou uma tentativa específica de recuperação.
No Swift, o mesmo `catch` também transforma cancelamento em falha de anotações.

Evidências: [Swift reducer](../VerbumKit/Sources/Features/Scripture/ChapterReaderFeature.swift),
[Swift view](../VerbumKit/Sources/Features/Scripture/ChapterReaderView.swift),
[Kotlin reducer](../android/feature/scripture/src/main/kotlin/com/nexussoft/verbum/feature/scripture/ChapterReaderFeature.kt),
[Kotlin view](../android/feature/scripture/src/main/kotlin/com/nexussoft/verbum/feature/scripture/ui/ChapterReaderPane.kt).

Próxima correção indicada: aviso localizado e nova tentativa só das anotações,
mantendo o texto legível e tratando cancelamento separadamente. Não apagar o campo:
o problema é a ausência de tratamento funcional. A inspeção não demonstra perda de dados.

### F-02 — Jornada Android acoplada à leitura de anotações

`ReadingCollectionFeature.Started` monta `Loaded(activity, lastRead, annotations.load())`
em uma única operação. `ReadingCollectionScreen` dispara essa ação também na Jornada.
Se a leitura das anotações falhar, a ação `Failed` impede que os dados válidos de
histórico/última leitura sejam entregues à tela, embora ela não exiba anotações.

No iOS, Jornada lê histórico por `@Shared`, mas a view também inicia uma leitura
de anotações que não utiliza. São caminhos diferentes que merecem recuperação
independente, não remoção das funcionalidades de histórico ou notas.

Evidências: [reducer Android](../android/feature/scripture/src/main/kotlin/com/nexussoft/verbum/feature/scripture/ReadingCollectionFeature.kt),
[tela Android](../android/feature/scripture/src/main/kotlin/com/nexussoft/verbum/feature/scripture/ui/ReadingCollectionScreen.kt),
[tela Swift](../VerbumKit/Sources/Features/Collections/ReadingCollectionView.swift).

### F-03 — Helper legado de TTS coberto apenas por testes de transporte

`synthesizeSpeech(text, language, revision)` ainda tem consumidores em testes dos
dois clientes; por isso não foi eliminado como declaração sem uso. Seu corpo não
envia `bookId`, `chapter` ou `translation`, exigidos pelo verificador canônico atual.
Uma chamada autenticada desse helper não constitui um pedido válido ao servidor.

Os testes simulam respostas de transporte e verificam autenticação/erros; não provam
compatibilidade do payload com o backend. O player atual usa `startSpeechPlayback`
com os identificadores necessários, portanto esse achado não demonstra falha do
áudio usado no app.

Evidências: [cliente Swift](../VerbumKit/Sources/Clients/VerbumAPI/VerbumAPI+Routes.swift),
[cliente Kotlin](../android/core/clients/src/main/kotlin/com/nexussoft/verbum/clients/api/VerbumApi.kt),
[verificação canônica](../backend/internal/scripture/speech.go).
Próximo passo: migrar a cobertura de transporte para requisições canônicas e decidir
se o helper legado ainda deve fazer parte da API interna; preservar as asserções úteis.

### F-04 — Expectativas antigas dificultam usar a suíte como sinal confiável

Executado novamente no estado atual, antes da limpeza:

```sh
cd android
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew :feature:scripture:testDebugUnitTest --tests '*ScriptureFeatureTest'
```

Resultado: **3 passaram, 1 falhou**. `titleOpensTheShelfAndAChapterClosesItIntoTheReader`
espera, após abrir 1 Samuel 17, `flow=[John 3]`, `navigationRevision=0` e nenhuma
posição de restauração. O reducer entrega `flow=[1Sam 17]`, revisão 1 e posição
inicial, coerentes com a entrada em outro capítulo. Isso confirma uma expectativa
antiga; não basta para concluir que o fluxo de navegação está quebrado.

O teste Swift homônimo também não contempla os novos campos, por inspeção. Não foi
reexecutado nesta revisão, e sua falha histórica não é apresentada como resultado novo.
Não foram alteradas as expectativas apenas para produzir uma suíte verde.

Além disso, não há esquema de testes iOS compartilhado versionado. Os 61 testes
registrados na entrega anterior usaram um runner temporário, conforme o registro
original. Tornar essa execução reproduzível no repositório é uma melhoria de manutenção.

### F-05 — Histórico de documentação confundido com estado atual

O roadmap ainda apresenta Biblioteca/Jornada como trabalho futuro em entradas antigas.
Foi adicionado um resumo atual com links para a entrega e esta revisão, preservando
o histórico. Datas e evidências devem prevalecer sobre uma marca isolada de conclusão.

## Candidatos descartados

- `VerbumAttestationFactory.createProvider`: implementação de `AppCheckProviderFactory`.
- `makeUIView`, `updateUIView`, `makeCache`, `updateCache`, `placeSubviews`: protocolos SwiftUI.
- `callAsFunction`: ambiente de navegação de conta, chamado pela sintaxe de função.
- `onGetSession`, callbacks Media3/OkHttp, `removeEldestEntry`: invocados pelos frameworks.
- `VerbumApp` / `VerbumApplication`: entradas registradas do app; `TokenGalleryPreview`: preview.
- Validadores/serializador em `pipeline/verbum_pipeline/models.py`: decorados pelo Pydantic;
  CLI registrado em `pipeline/pyproject.toml`.
- Nenhuma nova função Go com ausência confirmada de consumidores nessa varredura.
  Rotas públicas e callbacks HTTP não dependem de chamada textual direta pelo app.
- APIs com consumidores apenas em testes e compatibilidade iOS 17 não foram
  removidas automaticamente. Ausência de uso no iOS 27 não elimina caminhos no iOS 17.

## Validação da limpeza

- Nova busca por `EmptyPage`, `synthesizeChapterSpeech` e `postTimedAudio`: nenhum
  consumidor remanescente nos fontes/testes.
- Build iOS Simulator / iPhone 17 Pro: **BUILD SUCCEEDED**.
- Android `:app:assembleDebug` e testes `SpeechPlaybackTest`, `AudioCueWireTest`,
  `ApiAuthorizationTest`: **BUILD SUCCESSFUL**.
- A falha antiga de navegação permanece registrada em F-04; não é ocultada por
  esses testes selecionados de áudio/cliente.
- `git diff --check` passou. Nenhuma interface visível ou texto de produto mudou;
  os recursos PT-BR/EN foram preservados.

Esta inspeção registra as fricções acima; não afirma que elas foram corrigidas pela
remoção de código sem uso. O push continua dependendo da autenticação GitHub descrita
no [registro de publicação](RELEASE_2026_09_28.md).
