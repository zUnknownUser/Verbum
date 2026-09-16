# Desempenho — análise de abertura, navegação e leitura

Escopo: iOS e Android. Inspeção do código e correções pontuais, sem alterações visuais. Não houve perfil de CPU, memória, frames ou tempo de abertura em aparelhos físicos. Os achados abaixo identificam trabalho desnecessário ou bloqueante; não representam uma medição do impacto percebido pelo usuário.

## Ajustes aplicados

| Área | Evidência anterior | Alteração |
| --- | --- | --- |
| iOS, áudio/cache | `CloudSpeechRenderer` era `@MainActor`: leitura de marcações, escrita do MP3, listagem e limpeza de arquivos executavam na fila da interface, inclusive ao terminar o download progressivo. | Actor próprio mantém a exclusão de tarefas duplicadas e executa operações de disco fora do MainActor. |
| Android, conteúdo | `VerbumApi.get` lia/escrevia o cache e decodificava JSON no dispatcher do chamador; os efeitos de produção usam `viewModelScope`, normalmente Main. O transporte já usava IO, mas o trabalho antes/depois dele voltava ao Main. | O fluxo GET cache/rede/decodificação executa em `Dispatchers.IO`, mantendo validade de uma hora, idioma, autenticação opcional e fallback offline. |
| Android, áudio/cache | Diretório, hash, existência do MP3 e leitura das marcações no dispatcher do chamador. | Entrada do cliente de áudio executa em IO; player Media3 continua com suas exigências de Main. |
| Android, raiz/abas | O container que aplica o tema observava todo o estado do app, incluindo ticks do áudio. | Observa somente `profile.appearance`, com `distinctUntilChanged`; mudanças do tema do sistema continuam observadas pelo Compose. |
| iOS, rolagem | Offset numérico armazenado em `@State` era atualizado em cada mudança de geometria, embora usado somente para persistência. | Snapshot por referência sem observação das suas propriedades. Persiste no repouso e na saída como antes; não invalida a tela a cada pixel. Apenas caminho iOS 18+, que inclui iOS 26/27; caminho legado preservado. |
| Ambos, capítulos | Cada busca de posição percorria até 1.189 referências. No pager iOS, a busca também era repetida nos itens. | Índice imutável por livro/capítulo, ignorando seleção de versículos. No iOS, posição corrente calculada uma vez para os itens da página. |

Preservados: PT-BR, voz e perfis, identidade e limites dos caches, limites de custo, destinos de navegação, persistência de posição e aparência. Não foi adicionada geração antecipada de áudio nem chamada paga para aquecer capítulos.

## Achados que precisam de uma segunda etapa

1. **Abertura e persistência:** `RootView` aguarda a sessão local e executa `LocalAccountData.prepare`; Android faz `AccountPreferencesClient.prepare` antes de mostrar conteúdo. Existe IO síncrono nesse caminho, mas a migração tem marcadores para evitar repetir o trabalho integral. Medir abertura em instalação nova e existente separadamente antes de reorganizar o fluxo; preservar isolamento por conta e tratamento de falha.
2. **Android, gravações durante uso:** `SharedPreferencesClient.setString` usa `commit()` síncrono, com chamadas a partir dos reducers (histórico/preferências). Pode bloquear a interface. Não foi trocado simplesmente por `apply()`: o código atual confirma persistência e trata falhas. Uma mudança deve manter ordem, isolamento por conta e confirmação de gravação em uma fila própria.
3. **iOS, paginação:** `TabView` enumera os 1.189 índices, embora apenas o capítulo corrente e vizinhos tenham conteúdo de leitura. Isso não significa baixar a Bíblia toda nem renderizar 1.189 capítulos completos. O custo real de enumeração/hosting precisa ser medido; avaliar pager com janela limitada sem perder estabilidade de seleção, swipe, saltos e acessibilidade.
4. **Leitura, estudo:** `ReaderEntityLinker` recompila o padrão de nomes por versículo, dentro do processamento do contexto no reducer, em ambos os apps. Avaliar compilação uma vez por capítulo e cálculo fora da interface, com controle de respostas antigas em navegação rápida.
5. **Sessões longas:** estados do leitor acumulam capítulos, contextos e marcações consultados; o cache de disco já tem política própria, mas a memória da tela deve ser medida durante leitura extensa. Uma eventual expulsão precisa preservar o fluxo contínuo e o retorno ao ponto anterior.
6. **Home e rede:** carregar o versículo diário e, quando autorizado, preparar notificações envolve conteúdo/cache/rede. A primeira consulta de conteúdo remoto depende da conexão; fluidez da interface e latência de rede devem ser medidas separadamente. O TTS novo tem geração remota variável e agora dispõe do fluxo progressivo documentado em `AUDIO_PROGRESSIVO_2026-09-16.md`.

## Validação realizada

- Build iOS Simulator aprovado (`xcodebuild`, scheme Verbum, iOS mínimo existente).
- 45 testes reais do target Models Swift aprovados em pacote temporário macOS 13+, com cópia dos fontes/testes do repositório, sem dependências de UI/Firebase. Inclui os 1.189 índices, seleção de versículos e referências inválidas. Isso não equivale à execução de testes de UI iOS.
- Android APK debug aprovado. 87 testes de clients, 3 de ReaderStudy e 12 de leitor/player aprovados. Os testes de clients incluem cache em memória/disco, fallback offline, manifesto, autenticação e playback; novo teste verifica execução fora do dispatcher do chamador e retorno a ele após a chamada.
- Seis expectativas antigas de URL nos testes Android foram atualizadas para os parâmetros de idioma/versículo que o código já enviava antes desta rodada. O comportamento de URL não mudou.
- `git diff --check` aprovado. Nenhuma avaliação auditiva ou síntese paga.

## Medição recomendada no aparelho

Comparar o build anterior e este no mesmo dispositivo, idioma PT-BR e configuração, em condições equivalentes: abertura fria/quente; 20 trocas de abas; rolagem de Salmos 119; avanço/retorno de capítulos e salto Gênesis–Apocalipse; leitura durante download final do áudio; modo contínuo e restauração do ponto de leitura. Medir tempo até interação, frames atrasados, bloqueios da thread principal e memória. Executar testes de navegação também sem áudio, com áudio em cache e com Reduce Motion habilitado. Sem esses dados, não declarar porcentagem de melhora nem garantia de 60/120 FPS.
