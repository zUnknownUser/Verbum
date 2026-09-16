# Controle de custos e planos

Implementação de 16/09/2026. A política é aplicada pelo backend e compartilhada entre réplicas via PostgreSQL. O aplicativo informa a identidade Firebase e um identificador de instalação; não escolhe seu plano, suas cotas nem o orçamento. O deploy é rastreado em `backend/DEPLOY.md`; atualizações dos apps são distribuídas separadamente.

## Política inicial

Valores conservadores, configuráveis, sem promessa comercial de disponibilidade ilimitada. O teto global inicial é **US$ 5/dia**, adotado enquanto o proprietário não escolhe outro valor. As janelas diárias viram à meia-noite UTC; os apps mostram a data no horário local.

| Controle diário | Visitante Firebase anônimo | Free autenticado | Premium válido |
|---|---:|---:|---:|
| Novas respostas de IA | 3 | 10 | 100 |
| Novos capítulos de áudio | 1 | 3 | 20 |
| Novos embeddings de busca | 20 | 60 | 300 |
| Sessões de voz | 0 | 1 | 5 |
| Duração máxima por sessão | — | 60 s | 180 s |
| Orçamento estimado por usuário | US$ 0,10 | US$ 0,50 | US$ 3,00 |
| Operações pagas por hora, incluindo etapas de voz/embedding | 20 | 40 | 120 |

As cotas são tetos de geração, não garantias de que todas estarão disponíveis: o orçamento individual/global pode restringir antes. Áudio e respostas ainda em cache continuam disponíveis mesmo com orçamento esgotado. Respostas privadas são separadas por UID; áudio bíblico canônico é compartilhado.

- Minuto: UID 20, IP 60, instalação 40 operações novas, persistentes.
- Hora: IP 300 e instalação 150, além do limite do plano.
- Os limites rápidos por rota e de concorrência já existentes continuam antes desses controles, inclusive em acessos ao cache. São locais à réplica; cotas, orçamento e deduplicação são compartilhados.
- A instalação é um sinal adicional, alterável pelo cliente; não equivale a atestação de dispositivo. UID verificado, IP confiável e orçamento global continuam sendo os controles principais. Configure `VERBUM_TRUSTED_PROXIES` apenas para proxies reais.

## Reservas, cache e falhas

Antes de chamar um provedor, uma transação reserva custo e incrementa cotas. Uma trava PostgreSQL curta serializa reservas concorrentes; outra trava por conteúdo impede duas gerações simultâneas entre réplicas. Nenhuma transação fica aberta durante a chamada ao provedor. Após sucesso, o consumo informado reconcilia o custo. Tentativas contam na cota; uma falha anterior à chamada devolve a reserva monetária. Timeout ou consumo desconhecido mantém a estimativa, incluindo queda do processo.

Um marcador persistido antes da chamada bloqueia nova geração do mesmo conteúdo por 15 minutos após falha ambígua. Requisições simultâneas esperam o resultado compartilhado. `Idempotency-Key` (até 128 caracteres) é vinculado ao conteúdo por UID durante 24 horas; reutilizá-lo com outro conteúdo retorna `idempotency_conflict`. A reutilização do resultado segue a retenção do cache, não uma garantia de replay eterno.

| Conteúdo | Chave e retenção |
|---|---|
| Resposta IA | UID + pergunta + idioma + referências + modelo + revisão; 1 h |
| Embedding da consulta | UID + consulta + revisão; 24 h |
| Áudio | Texto canônico + voz + configurações + variante sincronizada + revisão; 7 dias no PostgreSQL, além do cache de disco existente |
| Capítulo canônico externo | Tradução + livro + capítulo; 30 dias |
| Tickets de voz | Hash de segredo aleatório, uso único, validade 60 s |

Limite por artefato compartilhado: 90 MiB. Limpeza horária remove cache vencido, vínculos de idempotência vencidos, tickets antigos e contadores/operações após 35 dias. O cache de disco do TTS mantém a política já existente; não foi criado um teto total de armazenamento do servidor. Dimensionar banco/volume e egress continua necessário. A revisão `VERBUM_CONTENT_REVISION` invalida caches econômicos quando corpus/prompt/preços/modelos mudarem.

## Recursos protegidos e experiência

- **Ask/RAG:** pergunta até 500 caracteres, corpo 8 KiB, prompt até 32 KiB, saída até 1.024 tokens; modelo tarifado `gpt-4o-mini`. Embeddings usam `text-embedding-3-large`. O limite da IA retorna HTTP 200 com `fallback`, resposta vazia e passagens da busca indexada, sem nova chamada paga. As telas distinguem esse resultado de uma resposta gerada. Busca semântica restrita degrada para busca lexical.
- **TTS:** exige livro, capítulo e tradução, confronta o texto com a fonte canônica `bible.helloao.org`, aceita apenas `por_blj`/pt-BR/Aoede e `BSB`/en-US/Standard-A. Impede texto arbitrário, voz arbitrária e capítulos inexistentes. Velocidade é aplicada no player, evitando pagar novamente. Os apps não geram antecipadamente o próximo capítulo e não repetem falhas pelo endpoint alternativo. Áudio local continua disponível; a alternativa de gravação em inglês existente permanece identificada como tal.
- **Voz:** o app recebe ticket Verbum e conecta ao mesmo host do backend. Nenhuma credencial OpenAI é entregue. O relay impõe duração, uma sessão ativa por UID, até 12 respostas, 384 tokens de saída por resposta, contexto de conversa truncado a 2.048 tokens após instruções, instruções até 16.000 bytes e ferramentas até 8.192 bytes. PCM é limitado à velocidade real e à duração da sessão. Cada resposta exige nova reserva; parâmetros arbitrários do cliente são removidos. Áudio/transcrições passam pelo relay sem persistência nele.
- **Profile iOS/Android:** plano e saldo vêm de `GET /v1/me/usage`, autenticado e sem cache. Não há uma flag local que conceda premium. Mensagens de limite incluem renovação quando disponível; leitura, favoritos e conteúdo local seguem acessíveis.

## Estimativas e o que o teto cobre

Contabilidade em microdólares: `1_000_000 = US$ 1`. Reservas iniciais: Ask 10.000, embedding 1.000, voz 20.000 por sessão para transcrição + 250.000 por resposta. Transcrição usa uma reserva conservadora fixa; respostas usam consumo retornado. TTS reserva 30 microdólares por caractere, usando a tarifa de Chirp 3 HD também como estimativa conservadora para a voz inglesa. Descontos de cache do provedor não são necessários para admissão.

Referências oficiais consultadas em 16/09/2026: [Google TTS](https://cloud.google.com/text-to-speech/pricing), [GPT Realtime](https://developers.openai.com/api/docs/models/gpt-realtime), [custos/contexto de voz](https://developers.openai.com/api/docs/guides/voice-latency-cost), [WebSockets](https://developers.openai.com/api/docs/guides/voice-websockets). Os coeficientes de [GPT-4o-mini](https://developers.openai.com/api/docs/models/gpt-4o-mini) são US$ 0,15/0,60 por milhão de tokens de entrada/saída; [embeddings large](https://developers.openai.com/api/docs/models/text-embedding-3-large) US$ 0,13/milhão; Realtime texto US$ 4/16 e áudio US$ 32/64 por milhão.

O teto bloqueia **novas operações pela estimativa**; não é um limite contratual da fatura. Diferenças de tarifa ou consumo acima da reserva são registradas e bloqueiam novas admissões. Custos de infraestrutura, armazenamento, tráfego, Firebase e comandos editoriais/pipelines executados fora da API não entram nesse contador. Não houve chamadas pagas nos testes. Revisar coeficientes ao mudar modelos ou tarifas.

## Configuração e ativação

1. Aplicar `backend/db/migrations/0007_usage_policy.sql` com o processo de migração do projeto, antes de iniciar este backend. O usuário do banco precisa escrever nas tabelas `usage_*` e usar advisory locks. Sem PostgreSQL, operações pagas ficam bloqueadas; leitura pública permanece disponível.
2. Manter `VERBUM_DATABASE_URL`, `VERBUM_FIREBASE_PROJECT_ID`, credenciais Firebase/Google e `OPENAI_API_KEY` conforme os recursos usados. As apps precisam do mesmo projeto Firebase do backend.
3. Configurar `VERBUM_DAILY_BUDGET_MICROS` e, se desejado, `VERBUM_{GUEST,FREE,PREMIUM}_{ASK,AUDIO,EMBEDDING,VOICE,HOURLY,DAILY_MICROS}`. Valores inteiros de 0 a 1.000.000.000; zero desativa o recurso/capacidade. Valores inválidos impedem inicialização. Durações de voz ficam na política de código.
4. Implantar o backend e os apps de forma coordenada: TTS antigo sem identificação canônica será recusado, e clientes antigos que tentam usar o novo ticket diretamente na OpenAI não conseguem iniciar voz. Em app já distribuído, preparar atualização obrigatória/versão mínima antes dessa troca. Não manter um endpoint pago legado que contorne as regras.
5. Habilitar WebSocket no proxy, com timeout suficiente para sessões de até 180 segundos. Observar negações, reservas sem conclusão e gasto no painel do provedor durante a validação real.

Ativação administrativa de premium, em ambiente autorizado com `VERBUM_DATABASE_URL` configurada:

```sh
cd backend
go run ./cmd/usage-admin --uid FIREBASE_UID --plan premium --expires 2026-10-16T00:00:00Z --source 'grant administrativo'
```

Para revogar, usar `--plan free` e uma expiração futura. Expiração de premium volta a free automaticamente; identidade anônima continua visitante mesmo que haja um registro premium.

**Compras Apple/Google ainda não integradas.** Não existe fluxo de compra configurado no projeto para conectar automaticamente aqui. Antes de vender assinaturas, implementar/verificar recibos e notificações de renovação/cancelamento/reembolso no servidor, atualizar `usage_entitlements` por esse caminho confiável e só então apresentar compra no app. O CLI atual é restrito ao operador com acesso ao banco; o aplicativo não pode conceder direitos.

## Validação reproduzível

```sh
cd backend
# Banco LOCAL descartável com pgvector e permissão de criar schemas:
VERBUM_TEST_DATABASE_URL='postgres://.../test?sslmode=disable' go test -race ./...
go vet ./...
```

Os testes usam schemas isolados e aplicam migrações duas vezes. Cobrem orçamento concorrente, persistência após reconstruir serviço, expiração de premium, cache após cota esgotada, deduplicação entre serviços, idempotência/timeout, ticket de uso único, relay com provedor WebSocket simulado, duração, contabilização, proteção de parâmetros, texto TTS canônico e fallback sem embeddings. Testes de clientes validam autenticação, ausência de cache do saldo, datas de limite e vínculo do relay ao host esperado.
