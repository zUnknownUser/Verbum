# Áudio acompanhado, destaques e foco de leitura

## Uso

- Em **Bíblia → … → Ouvir**, inicie a narração do capítulo. O trecho atual recebe
  uma indicação discreta na margem, com fundo quase transparente. Não há animação palavra a palavra.
- **PT-BR:** a voz Chirp 3 HD Aoede existente permanece. Cada trecho tem até três versículos;
  versículos muito longos podem ocupar mais de um segmento. Tempos são medidos nos WAVs reais,
  com compensação do crossfade de 60 ms, antes da codificação de um MP3 contínuo.
- **EN:** os tempos por versículo vêm dos arquivos `audioTimings` do helloao. São usados somente
  quando tradução, livro, capítulo, narrador e URL do áudio correspondem exatamente à gravação.
- A página rola apenas quando o começo do próximo trecho sai da área visível. Rolar manualmente,
  trocar de página ou abrir o estudo suspende o acompanhamento. **Acompanhar leitura** o retoma.
  Pausa, busca de posição, ±15 s e velocidade usam o relógio do player existente.
- O destaque nunca acompanha áudio em outra tradução. Sem tempos válidos, a reprodução continua
  normalmente, sem sincronização. Falha no TTS sincronizado tenta a leitura PT-BR existente antes
  do fallback inglês. Nenhum tempo é estimado pelo tamanho do texto.
- **Destacar:** escolha **Fundo suave**, **Sublinhado** ou **Margem**, com cores suaves.
  As anotações anteriores continuam válidas e recebem o estilo de fundo por padrão.
- **Foco de leitura:** um toque no ícone de foco esconde cabeçalho, números, controles, tabs e
  miniplayer. Tocar no texto restaura a interface; a narração pode continuar. Fora do foco,
  os toques mantêm a investigação de palavras e versículos. Há ação de acessibilidade para sair.

## Implementação

O domínio ganhou `AudioCue` e `AudioReadingPosition`. O player existente projeta a posição
para as views; a posição publicada muda por trecho/estado, sem mover o relógio para o reducer
do reader. Models continuam independentes de UI e rede; geração/cache permanecem nos Clients
mobile e no serviço TTS existente. Não há nova dependência, modelo de IA ou tabela no banco.

`POST /v1/tts` aceita `verses` opcional. Seus textos, unidos por nova linha, precisam corresponder
exatamente a `text`; números devem ser crescentes. A resposta permanece MP3 e inclui o header
`X-Verbum-Audio-Cues` com os intervalos medidos. Requests antigos mantêm o comportamento/cache.
Áudio e tempos são publicados atomicamente em um único artefato no cache do servidor, com
identidade separada da geração antiga. Antes de publicar, ffprobe confere que a duração do MP3
corresponde à linha temporal medida (tolerância de 150 ms para o contêiner MP3); divergências
não publicam sincronização. Duas chamadas limitadas ao provedor reduzem o tempo de
preparo; o gate existente limita a geração por capítulo. A primeira preparação pode demorar;
replays e prefetch do próximo capítulo usam cache. O cache mobile existente mantém seus limites.

No Android, a posição sincronizada usa uma projeção `distinctUntilChanged`, independente do
fluxo do relógio do miniplayer. No iOS, o ambiente carrega somente a posição de leitura. A
rolagem respeita redução de movimento do sistema; o usuário pode interromper o acompanhamento.

## Verificação

A suíte de áudio é validada em Debian Bookworm/FFmpeg 5.1, como o Dockerfile de produção.
Testes Go cobrem duração PCM real, duração final do MP3, crossfade, cache persistente, validação de texto, header HTTP
 e compatibilidade legada; testes com detector de concorrência verificam a geração limitada.
Novas fontes de testes mobile cobrem limites temporais/seek, metadados inválidos, identidade da
tradução/gravação e compatibilidade das anotações. Execução dos testes mobile e QA em aparelho
continuam com o proprietário (ROADMAP); compilação não equivale a validação perceptiva.

Roteiro de aparelho: João 1 em PT-BR e EN; pausar/retomar, ±15 s, 0,8×/1,5×, narrador diferente,
scroll manual/retomar, mudança automática de capítulo, modo contínuo/páginas, foco, texto grande,
VoiceOver/TalkBack, gravação sem tempos e áudio inglês enquanto o texto está em português.

Fontes técnicas:
- https://bible.helloao.org/docs/reference/translations/standard.html
- https://github.com/HelloAOLab/bible-api/blob/main/packages/helloao-cli/README.md
- https://docs.cloud.google.com/text-to-speech/docs/chirp3-hd

Chirp 3 HD não oferece `mark` entre os elementos SSML documentados. Por isso a implementação
mede trechos do áudio em vez de simular tempos por palavra ou trocar silenciosamente de voz.
