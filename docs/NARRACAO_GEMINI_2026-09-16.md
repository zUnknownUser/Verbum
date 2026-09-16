# Narração bíblica — Gemini 2.5 Pro TTS

Implementação: Google Cloud Text-to-Speech (`gemini-2.5-pro-tts`), voz masculina **Charon**, português brasileiro. A escolha do timbre é inicial: a avaliação auditiva fica com Lucas. Nenhuma geração real de áudio foi executada nesta implementação.

## Direção editorial

`backend/internal/tts/narration.go` contém a direção geral e seis perfis: narrativa, contemplação/poesia, sabedoria, ensino, profecia e diálogo. Ritmo e expressividade orientam o prompt; velocidade de reprodução continua no dispositivo. A direção é enviada em `input.prompt`, separada de `input.text`, com instrução de leitura integral, sem comentários, música ou representação de personagens.

Os padrões por livro são complementados por exceções de capítulo e intervalo de versículos. João 1:1–18 é contemplativo, João 1:19 em diante e João 11 são narrativos; João 3:1–21 recebe direção de diálogo. Romanos 8 é ensino; Salmo 23 é contemplativo. Mateus 5–7 e Daniel 1–6 têm exceções iniciais. É um conjunto editorial inicial, não uma classificação exaustiva de todas as passagens. Não existe chamada de IA para classificar a cada Play.

O texto canônico é validado antes de selecionar contexto. O cliente não pode enviar modelo, prompt ou perfil. Trechos sincronizados respeitam mudanças editoriais e até 3.800 bytes, mantendo versículos inteiros sempre que possível. Isso dá mais contexto ao narrador e reduz reinícios; os indicadores de leitura acompanham grupos de versículos, não alinhamento palavra por palavra. Trechos com versículos individuais enormes ainda são divididos sem perder bytes. Requisições antigas sem lista de versículos usam a direção inicial do capítulo.

## Cache e custo

Modelo, voz, prompt, versão editorial, perfis, exceções e segmentação compõem a identidade da narração. A mudança atual invalida os áudios portugueses antigos no manifesto dos dispositivos, no disco do backend e no cache permanente do PostgreSQL. A atualização do manifesto pode levar até uma hora nos clientes; offline eles podem continuar com o último áudio conhecido. Áudios antigos não são apagados. Inglês mantém sua voz e identidade anteriores.

Os áudios novos são gerados sob demanda e reutilizados; não há regeneração em massa da Bíblia. Uma mudança editorial futura invalida a versão portuguesa inteira nesta primeira implementação; a geração continua apenas sob demanda.

Preço de referência: US$ 1/milhão de tokens de entrada e US$ 20/milhão de tokens de áudio (25 tokens por segundo). A reserva cobre o máximo documentado de 16.384 tokens de saída por trecho, mais os bytes de entrada como limite conservador de tokens, incluindo cada repetição do prompt. Sucesso é contabilizado por duração WAV medida antes de junções e arredondamento de tokens, mais a estimativa conservadora de entrada. Não é uma reprodução exata da fatura do Google. Falhas, timeout e cancelamento conservam a reserva inteira.

O orçamento global existente de US$ 5/dia não foi aumentado. A reserva temporária é deliberadamente maior que o custo típico; capítulos extensos ou pouco saldo disponível podem impedir uma geração nova. Cache existente continua disponível. Sem novas cotas diárias por usuário para ouvir narração. Mantidos idempotência, serialização global e ausência de retries pagos automáticos.

Cada chamada tem timeout de 180 segundos; capítulo completo continua limitado a 10 minutos. WAV com duração próxima do limite de truncamento de 655 segundos é rejeitado antes de publicar. Validação de formato/duração não comprova fidelidade verbal: a escuta editorial é necessária para avaliar a narração generativa.

## Ativação e rollback

`VERBUM_TTS_NARRATOR=gemini` ativa o novo narrador (também é o padrão sem variável). `VERBUM_TTS_NARRATOR=chirp3` conserva o narrador anterior e seu cache. Não há fallback pago automático entre modelos.

A conta `verbum-tts@verbum-app1.iam.gserviceaccount.com` precisa de `aiplatform.endpoints.predict`, normalmente via `roles/aiplatform.user`, no projeto **verbum-app1**. A credencial atual não consegue ativar Cloud Resource Manager, que está desativado, portanto a consulta IAM não pôde ser concluída. O proprietário pediu a ativação imediata. `VERBUM_TTS_NARRATOR=gemini` foi configurado para o deploy. A consulta IAM não foi concluída. Um preflight deliberadamente sem texto retornou HTTP 400 “Please provide text to synthesize”, sem gerar áudio; isso não comprova a permissão de uma síntese real. A primeira geração e a escuta continuam a cargo de Lucas.

## Validação

Testes Go com race detector; testes de contrato HTTP com servidor simulado; perfis e limites por passagem; versão do cache; reserva por todos os segmentos; montagem MP3, duração dos indicadores e cache em container com FFmpeg e rede desativada. WAVs sintéticos de silêncio, sem Google, custo ou avaliação auditiva.

Referências consultadas em 16/09/2026:
- https://docs.cloud.google.com/text-to-speech/docs/gemini-tts
- https://cloud.google.com/text-to-speech/pricing

## Recibo de publicação

- Commit da narração: `bcf6ebc`.
- Commit conjunto publicado (inclui Luna): `efe3999b41ea045ca3df1cb457ac62a518180ea2`.
- Railway deployment: `d8fe0825-f41d-4880-aa01-892b4a2c6ccf`, **SUCCESS**, criado em 16/09/2026 às 18:22:24 UTC.
- Configuração confirmada: `VERBUM_TTS_NARRATOR=gemini`, `VERBUM_ASK_MODEL=gpt-5.6-luna`.
- API pública: `https://api.vendlydigital.com.br`; `/healthz` e `/readyz` retornaram 200.
- Manifesto português em produção: `2f259b36683fd3a97aa2b6d6f2939454178a0717067d9c6bf9b4489ca74cae30` (diferente do anterior).
- Manifesto inglês preservado: `b9889517f9d5f54f39eca42c2e7d94fe45037a40a02b304e017530b69f3337e1`.
- Nenhuma versão mobile foi publicada; os clientes existentes já consultam o manifesto.
- A síntese real, a permissão específica do modelo e a qualidade auditiva não foram validadas por geração: o usuário pediu para testar o áudio pessoalmente.

## Incidente de idioma e rollback — 16/09/2026

Um Play em Provérbios 2 fez `POST /v1/tts` retornar 503 às 18:30:36 UTC (reqID `68670fee9b75`). O app português então escolheu automaticamente uma gravação BSB em inglês. A causa não foi a atualização do cache: esse caminho de fallback já existia nos clientes. O log antigo não permitiu distinguir permissão do Google, indisponibilidade do provedor ou falha de rede, por isso a causa específica do 503 permanece não confirmada. A permissão `aiplatform.endpoints.predict` continua sem confirmação.

`VERBUM_TTS_NARRATOR` foi temporariamente revertido para `chirp3` para restabelecer áudio português, preservando o cache anterior. O backend agora aceita a versão Gemini do manifesto retida por até uma hora nos celulares durante esse rollback, mas gera sempre com a voz portuguesa ativa. iOS e Android deixam de substituir falha do TTS português por gravação inglesa; uma falha aparece como indisponibilidade do áudio. Novos logs separam falha de credencial/permissão de falha de rede/provedor sem expor tokens ou texto.

O código Gemini permanece pronto e pode ser reativado após conferir a permissão no projeto `verbum-app1`. Não houve geração real de áudio pelo agente.

### Publicação da correção

Commit `696ba578a219b151d5aee7dd12ac04e599d710a1` enviado para `main`. Deploy Railway `77be9bc2-09cd-4fb9-94ab-76964350ef94` **SUCCESS**. A variável `VERBUM_TTS_NARRATOR=chirp3` foi confirmada; `/healthz` e `/readyz` responderam HTTP 200 e o manifesto português voltou a `12abd06af4eda81e138c979a7a11f0c07461dab65b23a9a7074eac2a66011627`. O seletor de horário e a retirada do fallback inglês estão no código iOS/Android e precisam de nova versão dos apps para chegar aos aparelhos.

## Diagnóstico confirmado da ativação

Após autorização de Lucas para uma síntese técnica curta, a chamada direta com `gemini-2.5-pro-tts`, `Charon` e `pt-BR` retornou HTTP 403: API `aiplatform.googleapis.com` desativada/não utilizada no projeto `verbum-app1` (944218527462). Nenhum áudio foi gerado. Essa é uma causa concreta observada; não é atraso de manifesto nem prova de falta do papel `roles/aiplatform.user`.

A tentativa de ativar apenas essa API via Service Usage também retornou HTTP 403, negando à conta de serviço a permissão de ativação. É necessário que um administrador do projeto ative a API em https://console.cloud.google.com/apis/library/aiplatform.googleapis.com?project=verbum-app1 . Depois disso, repetir a síntese técnica; se surgir uma negativa de IAM específica, tratar a permissão indicada antes da reativação. Não trocar a variável de produção para Gemini enquanto a chamada permanecer bloqueada.
