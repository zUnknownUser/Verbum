# Sincronização privada, leitor e áudio — 28/09/2026

## Comportamento entregue

- iOS e Android, PT-BR e EN: falha ao carregar anotações aparece no leitor, com tentativa independente. O texto bíblico continua disponível.
- Jornada carrega visitas e dias de leitura sem depender de notas/destaques. Corrupção ou falha das anotações não derruba a Jornada.
- Notas, destaques, favoritos, histórico de capítulos, dias de leitura e último capítulo sincronizam entre aparelhos da mesma conta registrada. Visitantes continuam locais.
- Alterações continuam disponíveis offline; a fila persistente tenta novamente no retorno ao primeiro plano e enquanto o app está aberto. Não há promessa de execução contínua em segundo plano.
- Conta e armazenamento são vinculados ao UID; trocar de conta não reaproveita a fila da anterior. Cadastro publica a nova sessão somente após promover os dados do visitante.
- iOS troca o pager de 1.189 páginas por UIPageViewController com capítulo visível e vizinhos, preservando a transição interativa e Reduzir Movimento. Android só confirma a página após o gesto terminar e distingue mudanças programáticas.

## Protocolo e conflitos

`POST /v1/me/sync` usa token Firebase validado no servidor; UID nunca vem no corpo. Migração aditiva `0008_personal_sync.sql`, tabelas privadas separadas do corpus de busca/RAG. Nenhum texto pessoal é enviado à IA.

Cada alteração tem identificador durável, base e revisão do servidor. Escritas são serializadas por conta em transação. Retentativas usam o mesmo identificador; o cursor só avança após persistência local. No iOS, gravações compartilhadas são explicitamente concluídas antes do cursor para resistir ao encerramento do processo.

Campos independentes da anotação são combinados. Duas edições concorrentes da nota preservam os textos separados por `---`; limpar uma nota não elimina uma edição concorrente ainda não vista. Exclusão integral da anotação prevalece sobre edição antiga. Recriação exige conhecer a revisão da exclusão. Visitas/dias são cumulativos; histórico local ausente é restaurado, nunca interpretado como exclusão. Alterações locais feitas durante uma requisição não são sobrescritas pela resposta.

Limites: 100 alterações/requisição, 200 registros/página, 30 requisições/minuto/UID, notas de até 32 KiB UTF-8, 50.000 registros, 32 MiB de valores e 100.000 recibos de alteração por conta. Somam-se proteções existentes de IP, concorrência e App Check. Uma falha preserva a fila local e apresenta sincronização pendente; os limites não truncam notas.

`DELETE /v1/me/data` faz parte da exclusão da conta, após reautenticação recente e antes da exclusão Firebase. Elimina registros/recibos e mantém um marcador mínimo de UID fechado, impedindo requisições atrasadas de recriar os dados. Se o Firebase falhar depois, repetir a exclusão é seguro; a sincronização desse UID fica bloqueada. Sair da conta não chama essa rota. O estado de App Check em produção continua o já configurado, sem alteração nesta entrega.

## Diagnóstico e correção de Jó 36

Os logs do simulador mostraram `CoreMedia -12888`, playlist sem atualização por mais de 1,5 vezes o target duration, seguida de `AVFoundation -11866`. A geração já iniciada concluiu posteriormente; isso foi confirmado consultando somente seu status existente.

O modelo pode demorar para gerar um trecho literário enquanto o player espera uma atualização HLS muito mais rápida. O backend agora oferece snapshots imutáveis `snapshot-NNNNNN.m3u8`, com playlist VOD e ENDLIST. Os clientes novos tocam o trecho disponível, consultam por GET o mesmo trabalho e retomam no ponto anterior quando houver mais áudio. Final de trecho parcial não significa final do capítulo. A conclusão retorna o MP3 inteiro, que continua no cache global/local.

Não há nova chamada paga para continuar o trecho. O endpoint EVENT anterior permanece para compatibilidade; aparelhos antigos precisam atualizar para receber a correção completa. A primeira geração ainda pode exigir pausas se a síntese não acompanhar a escuta. O limite de espera mantém recuperação explícita; não se promete áudio instantâneo ou continuidade sem pausa em um capítulo inédito.

Sessões HLS continuam locais ao processo e expiram: deploy/reinício pode exigir tentar novamente. Arquivos completos em cache são reutilizados. Escala horizontal sem afinidade continua exigindo compartilhar sessões/segmentos. Não foi iniciada pré-geração da Bíblia.

Referências do transporte: [Apple, EVENT playlists](https://developer.apple.com/documentation/http-live-streaming/event-playlist-construction) e [RFC 8216](https://www.rfc-editor.org/rfc/rfc8216).

## Validação e limites de QA

- Build iOS Simulator 27 com deployment target iOS 17; 73 testes Swift em 18 suítes passaram, incluindo falha/retry de anotações, fila durável, troca de conta, persistência antes do cursor e continuação do mesmo capítulo.
- Android assembleDebug; 117 testes em 25 suítes passaram (core clients e suítes selecionadas de leitor, Jornada, navegação e áudio).
- Go: `go test -race ./...` e `go vet ./...` passaram. PostgreSQL local executou migrações e testes em schemas isolados, incluindo repetição, paginação, isolamento de contas e bloqueio após exclusão. Testes de empacotamento que dependem de ffmpeg não são executados pelo Go local quando o binário não está no PATH.
- OpenAPI 3.1 validado com 19 rotas, incluindo autenticação Firebase correta para os dados pessoais.
- Avisos encontrados no código Swift/Kotlin corrigidos: isolamento/sendability, macro de reducer, ícones espelhados, APIs adaptativas e opt-in de coroutines nos testes. Android terminou sem avisos nessa compilação. Xcode ainda emite o aviso de ferramenta `Metadata extraction skipped, no AppIntents.framework dependency found`; não foram suprimidos avisos globalmente.
- Sem teste subjetivo da voz ou geração TTS paga. Escuta de Jó 36 e aceitação da narração ficam com Lucas.
- Não se afirma teste E2E de sincronização com duas contas reais em dois aparelhos físicos. O protocolo/storage foi validado por testes locais. Android não estava conectado para teste manual.
- iPhone Duo não está disponível neste Xcode/runtime; não foi substituído por um teste de outro iPhone. Layout segue a largura disponível, mas a validação específica permanece pendente.

Publicação e instalação são registradas abaixo após verificadas; build local não equivale a distribuição pelas lojas.
