# Reprodução progressiva — iOS, Android e backend

## Comportamento

A primeira geração pode começar a tocar antes de o restante do capítulo ficar pronto. O app cria ou entra em uma sessão de reprodução, consulta seu estado e entrega a playlist HLS ao player nativo assim que o primeiro trecho está disponível. Não existe promessa de latência fixa ou ausência de buffering: a geração do Google e a conexão ainda podem ficar atrás da reprodução.

Gemini 2.5 Pro TTS / Charon / PT-BR e os perfis editoriais foram mantidos. O primeiro trecho é limitado a aproximadamente 450 bytes; os demais usam normalmente 1.500 bytes, crescendo até 3.800 em capítulos grandes para não multiplicar a reserva conservadora de orçamento. Versículos, ordem e estilos editoriais são preservados. A política faz parte da versão da narração.

O backend publica segmentos HLS AAC/MPEG-TS de aproximadamente seis segundos a partir de cada trecho pronto. A playlist EVENT cresce em ordem, começa no início do capítulo e só recebe ENDLIST após sucesso integral. Falhas posteriores ao início tornam a sessão indisponível; não transformam um trecho incompleto em capítulo concluído. As fronteiras de trechos usam discontinuidades HLS; o MP3 final mantém a montagem e o crossfade existentes.

## Cache e custo

- Mesma verificação de texto canônico, autenticação, App Check e limites por usuário/IP/dispositivo do TTS existente.
- Mesma reserva de orçamento, exclusão mútua e cache permanente do PostgreSQL. Não há geração iniciada por GET de playlist, estado ou segmento.
- Repetir Play para a mesma identidade compartilha a sessão do processo. As proteções persistentes continuam evitando sínteses duplicadas entre processos.
- Os áudios Gemini anteriores continuam sendo consultados no disco e PostgreSQL. Cache completo é entregue diretamente como MP3, sem reempacotar HLS. A nova segmentação não exige pagar para refazer capítulos já existentes.
- Após conclusão, o app baixa o MP3 completo e as marcações para o cache offline existente. Só artefatos completos são persistidos no dispositivo. Essa primeira escuta pode transferir HLS e MP3, em troca da reprodução antecipada e do cache offline.
- Um trabalho já iniciado continua após desconexão do celular, por no máximo dez minutos, para concluir o áudio compartilhado. Não há retry pago automático.
- Orçamento global de US$ 5/dia preservado; a admissão continua podendo negar novas gerações quando não houver saldo suficiente.

## Transporte e operação

`POST /v1/tts/playback` retorna imediatamente um `statusPath`. O estado indica prontidão, conclusão, playlist e caminho do MP3. URLs de leitura são capacidades aleatórias de 256 bits, válidas por uma hora, limitadas à mídia daquela sessão, sem informação de identidade. Os caminhos são ocultados nos logs da aplicação. Máximo de 32 sessões por processo; arquivos e registros temporários expiram em uma hora. O produto permanente continua no armazenamento existente.

A infraestrutura atual tem uma réplica. As sessões HLS são locais ao processo: reinício/deploy invalida sessões em andamento; tocar novamente permite obter outra sessão, reutilizando o cache completo existente. Para múltiplas réplicas sem afinidade, será necessário compartilhar também os artefatos HLS e a descoberta de sessões antes de escalar esse transporte.

iOS usa AVPlayer e atualiza a duração disponível à medida que a playlist cresce. Android mantém Media3 e adiciona seu módulo HLS. Os apps validam os caminhos recebidos e não aceitam playlists de um domínio arbitrário. A rota `/v1/tts` continua compatível com versões antigas, que esperam o MP3 inteiro.

Na primeira escuta progressiva, a marcação por versículo fica desativada para não apresentar tempos imprecisos na linha de tempo HLS. O MP3 completo em cache mantém a sincronização existente. Não houve publicação nas lojas: a experiência progressiva exige instalar os builds atualizados.

## Validação

- Builds iOS Simulator e APK Android aprovados.
- Testes Android de criação da sessão, polling sem cache de estado pendente, rejeição de URLs externas, autenticação e feature do player aprovados.
- Testes Swift de validação de URL adicionados. Os schemes do projeto não possuem ação de testes configurada; nesta rodada, iOS validado por build, não por execução desses testes.
- Go race detector e vet nos pacotes TTS/HTTP; testes de orçamento/cache/idempotência com PostgreSQL descartável.
- Integração FFmpeg em container sem rede externa: publicação antes da conclusão, ordem, playlist incompleta/completa, falhas, autenticação/capacidade, admissão de orçamento e decodificação integral do HLS.
- Áudio sintético de silêncio nos testes; nenhuma nova síntese paga de capítulo nem avaliação auditiva nesta implementação.

Referências de formato: [playlist EVENT da Apple](https://developer.apple.com/documentation/http-live-streaming/event-playlist-construction) e [HLS no Media3](https://developer.android.com/media/media3/exoplayer/hls).

## Deploy confirmado

- Código: `01d977ab8cb6f5aa47dccca24e0c02f662f414cf`, enviado para `main`.
- Railway produção: `4744a069-9e0e-46e4-ac40-0f21af7e3e05`, `SUCCESS`.
- Checagens posteriores ao sucesso: `/healthz` e `/readyz` 200; POST anônimo em `/v1/tts/playback` 401; capacidade inexistente em `/status` 404. Nenhuma síntese paga nesses probes.
- Manifesto PT-BR: `bd66a2958747418105ff40d2c11d608a94cb30d7c6c1b47073cb6169484609f6`. Manifesto EN inalterado: `b9889517f9d5f54f39eca42c2e7d94fe45037a40a02b304e017530b69f3337e1`.
- Builds móveis gerados e validados; publicação nas lojas não realizada.
