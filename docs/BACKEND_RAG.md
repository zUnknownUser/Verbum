# Backend e RAG — mapa e ponto de retomada

Escopo definido pelo proprietário em 2026-09-12: trabalhar por blocos somente no backend e no
RAG. Android e iOS ficam fora desta frente. Este mapa registra a execução; não substitui nem
amplia os requisitos de `PRODUCT.md` ou o contrato de `api/openapi.yaml`.

## Documentação existente

| Documento | Responsabilidade nesta frente |
|---|---|
| [PRODUCT.md](PRODUCT.md) | Fonte de verdade: princípios (§3), Ask (§13), backend/banco (§23–26), busca/RAG (§27–31), pipeline/proveniência (§32–34), contrato (§45), privacidade (§47), qualidade (§51–58), sequência (§60), regras obrigatórias (§73, §78). |
| [ROADMAP.md](ROADMAP.md) | Histórico e tarefas. Task 11: persistência/API e conteúdo; Task 12: Ask somente após recuperação confiável. |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Arquitetura dos consumidores; interfaces e semântica de grafo, timeline e versículo diário que o servidor precisa respeitar. |
| [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md) | Regras visuais dos apps; lido para mapear o projeto, sem implementação nesta frente. |
| [api/openapi.yaml](../api/openapi.yaml) | Contrato HTTP atual, schemas, parâmetros e respostas. |
| [api/README.md](../api/README.md) e [examples](../api/examples) | Respostas de referência usadas pelos testes de contrato. |
| [backend/README.md](../backend/README.md) | Estrutura Go, execução, validação e próximos passos explícitos. |
| [pipeline/README.md](../pipeline/README.md) | Pipeline offline Python ≥3.12 com uv; importação JSON, normalização, revisão e publicação implementadas. Extração por IA e embeddings são os próximos incrementos. |

## Ponto de retomada atual (2026-09-13) — leia isto primeiro

**Backend/RAG: completo e testado com chamadas reais até o Bloco 8 (abaixo).** Postgres, pipeline
editorial com revisão humana, extração por IA, Bíblia inteira (WEB) indexada com embeddings,
busca híbrida, Ask Scripture (`/v1/ask`), sessão de voz (`/v1/realtime/session`), observabilidade.
Tudo commitado e enviado para `main` (commit `92ee889`).

**App: zero integração com esse backend.** Nenhum arquivo em `Verbum/Verbum` (alvo iOS),
`VerbumKit` ou `android/` foi tocado nesta frente — por instrução explícita do proprietário
(2026-09-12/13), não porque algo tenha travado tecnicamente. Isso significa que hoje, testando o
app, você **não vai ver** nenhuma busca semântica, nenhum Ask, nenhuma sessão de voz — o app
ainda lê fixtures locais, não este servidor.

**Quando for pegar o lado do App**, o trabalho é: implementar os clientes `.live` (iOS/Android)
que chamam este backend de verdade, seguindo exatamente `api/openapi.yaml` (já documenta
`/v1/search` com `passages` real, `/v1/ask` e `/v1/realtime/session`). Pontos de atenção:
- `/v1/ask` e `/v1/realtime/session` são endpoints **novos**, sem exemplo de contrato
  byte-a-byte (respostas não-determinísticas) — o app precisa lidar com o formato, não comparar
  contra um fixture fixo.
- Antes de expor Ask/busca semântica pro usuário final, resolver a pendência de produto sobre o
  `internal/ask/safety.go` (ver Bloco 8) — a nota de "procure ajuda profissional" ainda dispara
  por palavra-chave da pergunta, não pelo conteúdo da resposta; o proprietário pediu revisão
  antes de estender/expor isso.
- Conteúdo real (§68) e licença PT-BR (§34) continuam pendentes — o RAG só cobre inglês (WEB)
  com a fixture pequena de entidades por enquanto.

## Estado encontrado

- Commits `c59f773`, `5abe18c` e `44f2aff`: contrato, esqueleto Go e instruções de retomada.
- API em `net/http`, interface `store.Store`, armazenamento em memória e fixtures compartilhadas.
- Schema PostgreSQL existente, mas sem consultas, carga inicial ou ligação em `cmd/api`.
- Busca apenas por nome de entidade; texto bíblico não é servido por esta API.
- Sem pipeline executável, índice semântico ou endpoint `/v1/ask` no OpenAPI atual.
- Os testes existentes com armazenamento em memória passaram antes das alterações deste bloco.

O resumo inicial do README e o trecho “Golden path” do roadmap divergem de alguns status
detalhados dos apps. Não foram usados como comprovação de aceite dos apps. A instrução histórica
de deixar testes/simuladores para o proprietário está no contexto dos apps; os testes de backend
exigidos por `backend/README.md` foram executados. A alteração preexistente em
`VerbumKit/Package.resolved` foi preservada.

## Bloco 1 — Task 11, armazenamento PostgreSQL

Implementado:

- `internal/store/postgres`: todos os métodos de `Store`, consultas parametrizadas, conexão via
  pgxpool e leitura do banco a cada chamada; cada resposta composta usa uma única instrução SQL.
- `cmd/api`: escolhe PostgreSQL por `VERBUM_DATABASE_URL`, verifica conexão e fecha o pool no
  encerramento. Ausência da variável mantém o modo explícito de fixtures.
- `cmd/seed`: importa as fixtures existentes em transação e exige tabelas de conteúdo vazias.
  Falha de integridade reverte a carga; conteúdo existente não é sobrescrito.
- Migração aditiva `0002_content_order.sql`: posições para preservar a ordem editorial das
  listas do contrato. Schema 0001 e fixtures não foram reescritos.
- Dependência pgx justificada pela conexão PostgreSQL já prevista no README/roadmap; versão
  fixada em `go.mod` e checksums em `go.sum`. Sem framework HTTP adicional.

Validação: Go 1.24 e PostgreSQL 17 em Docker; sete exemplos HTTP com ambos os stores,
comparação de todas as entidades das fixtures (detalhes, vizinhanças e timeline), busca,
contextos, ausência de cobertura, cancelamento e rollback/preservação na carga inicial.
Os testes PostgreSQL usam schemas isolados e exigem `VERBUM_TEST_DATABASE_URL`.
`go vet ./...`, formatação e build da imagem Docker também passaram. Os sete exemplos foram
conferidos novamente via HTTP na imagem em execução, com banco carregado por `cmd/seed`.

Limite: persistência validada com conteúdo de desenvolvimento, não cobertura editorial de
produção. O contrato HTTP e seus exemplos permanecem iguais. Nenhum app foi alterado.

## Próximos checkpoints documentados

Atualização do proprietário: continuar o desenvolvimento local antes da publicação externa.
Ao final de cada implementação, informar o que foi feito, o que foi validado e o que o
proprietário já pode testar, com comandos e resultados esperados. A integração OpenAI foi
solicitada durante o bloco 2 e depois explicitamente adiada até sua conclusão.

1. **Task 11 — operação da API:** configuração do ambiente de implantação, host e TLS,
   conforme `backend/README.md`. O repositório não define o host de produção.
   A integração dos clientes móveis será feita na frente dos apps.
2. **Task 11 — conteúdo:** pipeline Python offline (§32), com arquivos inspecionáveis,
   evidências e revisão humana antes de publicar. Carga editorial inicial conforme §68;
   licenças dos conteúdos reais precisam ser verificadas antes da produção (§34).
3. **Recuperação confiável:** busca textual, referências, entidades e busca vetorial híbrida
   (§27–29), com embeddings/pgvector produzidos pelo pipeline. O modelo de embeddings e o
   provedor de síntese não estão escolhidos na documentação existente.
4. **Task 12 — Ask Scripture:** classificar intenção, extrair referências/entidades,
   recuperar e ordenar evidências, sintetizar e validar citações (§13, §29–31). Estrutura de
   resposta de §30, fontes navegáveis, incerteza explícita e fallback para resultados de busca
   quando não houver resposta confiável (§21.3, §51). Formalizar `/v1/ask` no contrato neste bloco.

Observabilidade continua prevista em `backend/README.md` e §54. Autenticação/camada pessoal
seguem sua fase documentada; não foram antecipadas. A regra de §73 permanece: a geração não
começa antes da recuperação confiável, e propostas de IA não se tornam conteúdo publicado sem
revisão humana (§32).

## Bloco 2 — pipeline editorial inicial

Implementado: Python ≥3.12, uv/lockfile, importação JSON das fixtures existentes sem alteração
de conteúdo, normalização estrita, fila de propostas com fontes, decisões humanas por item e
publicação transacional em PostgreSQL. A migração 0003 armazena proveniência bibliográfica,
fontes por entidade e auditoria da publicação. O hash da revisão cobre conteúdo, evidências e
metadados de licença; modificações exigem nova revisão. Repetir um lote aprovado é idempotente.

A API mantém o contrato; entidades sem detalhe curado passam a expor suas fontes editoriais
quando publicadas pelo pipeline. As fixtures antigas mantêm o comportamento de referência.

Validação: 20 testes Python, incluindo importação fiel, revisão pendente/rejeitada/incompleta,
alterações após revisão, integridade de referências, rollback, atualizações, idempotência e
publicação Python → sete exemplos HTTP Go em schema isolado. Uma entidade sintética adicional
verifica a exposição de evidência editorial e o armazenamento de licença/página/seção.

Fila local em `pipeline/review/fixtures/`: 148 decisões pendentes (incluindo a lista diária como
um item), sem aprovação editorial real. O lote possui 2 fontes, 45 entidades, 59 relações,
25 detalhes, 16 eventos e 101 referências diárias. Comandos de teste em `pipeline/README.md`.

Limites: entrada de propostas estruturadas JSON; não há ainda extração por IA, ingestão de
texto bíblico, embeddings ou Ask. Aprovações locais dependem do editor, não de autenticação
digital. Licenças são metadados a verificar humanamente. Publicação preserva registros omitidos
e não implementa retirada de conteúdo. A publicação real deste lote aguarda revisão humana;
as aprovações usadas nos testes são sintéticas e não saem dos schemas temporários.

A migração 0003 foi aplicada ao banco local; a imagem Go foi reconstruída e a API em
`localhost:8080` voltou a passar nos sete exemplos. A tabela de auditoria local tem zero
publicações: a fila real permanece pendente. Ruff e os testes de regressão Go também passaram.

Depois de concluir o pipeline, a chave OpenAI fornecida pelo proprietário foi guardada com
DPAPI em `%LOCALAPPDATA%\Verbum\secrets\openai-key.dpapi`, fora do repositório, com ACL restrita.
O script `backend/scripts/Set-OpenAIKey.ps1` permite rotacioná-la sem gravar texto puro. Leitura
criptografada validada; integração e chamadas OpenAI permanecem para o próximo bloco.

## Bloco 3 — extração assistida por IA no pipeline

Escopo confirmado pelo proprietário em 2026-09-12: esta frente cobre backend e RAG (pipeline
incluído); apenas os apps (iOS/Android) ficam de fora.

Implementado: `pipeline/verbum_pipeline/extract.py`, o estágio "entição de entidades/relações"
do §32. Ele lê um texto-fonte bruto mais `Source`/`Provenance` fornecidos pelo editor (a licença
continua sendo verificação humana, §34, nunca inferida pela IA) e chama a OpenAI (modo JSON,
temperatura 0) para propor entidades e relacionamentos. O resultado é validado pelos mesmos
modelos Pydantic estritos usados em qualquer lote editorial manual — nenhum caminho novo de
confiança foi criado. A saída é um `Bundle` comum (`kind=editorial`) que segue exatamente o
mesmo fluxo já existente: `normalize` → `review.prepare` → aprovação humana item a item →
`publish`. Nada proposto pela IA entra no banco nem é marcado como aprovado por este estágio.

IDs de entidade são atribuídos pelo código (não confiados ao modelo): `{source.id}.{type}.{slug
do nome}`, evitando colisões e nomes inválidos. Relacionamentos só são aceitos entre entidades
propostas na mesma resposta; qualquer referência a uma entidade inexistente é rejeitada antes de
chegar à fila de revisão.

Credencial: `pipeline/run.ps1` agora descriptografa o segredo DPAPI apenas quando o comando é
`extract`, injeta `OPENAI_API_KEY` no container só por nome de variável (nunca como argumento
literal do Docker, nunca em disco) e limpa a variável do processo ao final, com sucesso ou erro.

Validação: 28 testes Python (8 novos), incluindo construção de bundle válido a partir de uma
resposta simulada, rejeição de chave de relacionamento inexistente, chave de entidade duplicada,
tipo de entidade inválido, JSON malformado do modelo, ausência de `OPENAI_API_KEY`, e um teste de
ponta a ponta que roda o comando `extract` via CLI (com cliente OpenAI substituído por um dublê)
e confirma que o resultado flui normalmente por `normalize`/`review`. Nenhuma chamada real à
OpenAI foi feita — os testes usam um cliente falso, e a validade da chave continua não testada.
Ruff limpo; suíte completa (28) e testes de integração PostgreSQL/HTTP existentes (schemas
isolados) seguem passando sem alteração.

Limite: nenhuma extração real foi executada contra texto-fonte de verdade; isto é só o estágio
mecânico. Ainda não há ingestão de texto bíblico licenciado, geração de embeddings, indexação
(pgvector) ou busca híbrida — item 3 dos próximos checkpoints abaixo. O modelo padrão
(`gpt-4o-mini`, sobrescrevível por `--model`/`VERBUM_OPENAI_MODEL`) não teve sua disponibilidade
nem custo confirmados pelo proprietário; a extração assistida ainda não roteia geração de
eventos de linha do tempo (`timeline`) — apenas entidades/relacionamentos/detalhes — extração de
eventos fica para um incremento futuro, se necessário.

## Bloco 4 — texto bíblico e embeddings para recuperação

Autorização do proprietário em 2026-09-12: testar a chave OpenAI com uma chamada real (custo
pequeno) e escolher a fonte de texto para o RAG.

Decisão de fonte: **WEB (World English Bible)**, domínio público, já embutida no repositório
(`VerbumKit/Sources/Clients/Resources/web.tsv`, mesma usada pelos apps como fallback offline em
inglês). Motivo: zero verificação de licença pendente (§34 já resolvido por herança). PT-BR fica
de fora deste bloco — a fonte que os apps usam para português (Bíblia Livre via helloao) nunca
foi verificada quanto à licença; não construí o índice de RAG em cima disso sem essa confirmação.

Teste real da chave: extração de 1 Samuel 17:48-50 (WEB) via `extract.py` — chamada real à
OpenAI, resultado validado (`kind=editorial`, `approved=false` até revisão humana). Custo
irrelevante (poucos tokens).

Implementado: `pipeline/verbum_pipeline/scripture.py`, o estágio "geração de embeddings" do §32,
mas só para o texto bíblico — deliberadamente fora do fluxo de revisão editorial (Bundle/review),
porque copiar texto de domínio público byte a byte não é uma alegação interpretativa a revisar
(mesmo raciocínio já usado pelo `cmd/seed` Go para as fixtures). Migração
`0004_scripture_search.sql`: extensão `pgvector`, tabela `scripture_verses`
(tradução/livro/capítulo/versículo/texto/embedding), índice de texto completo e índice HNSW.
`docker-compose.yml` trocou a imagem do Postgres de dev para `pgvector/pgvector:pg17`.

Limite técnico descoberto: o índice HNSW do pgvector rejeita vetores acima de 2000 dimensões; o
`text-embedding-3-large` nativo tem 3072. Resolvido pedindo 1536 dimensões via parâmetro
`dimensions` da própria OpenAI (truncamento tipo Matryoshka) — a OpenAI documenta que o
`3-large` truncado em 1536 ainda supera o `3-small` na mesma largura, então a decisão do
proprietário por "large" foi preservada.

Execução real (2026-09-12): os 31.098 versículos da WEB foram embutidos com `text-embedding-3-large`
a 1536 dimensões e carregados no Postgres de desenvolvimento. Custo real: poucos centavos de
dólar. Idempotente (upsert por tradução/livro/capítulo/versículo); testado repetindo a carga sem
duplicar linhas.

Validação: 37 testes Python (9 novos), incluindo leitura do TSV, lotes, upsert com embeddings
simulados em schema isolado, idempotência e rejeição de resposta de embedding com contagem
incompatível. Suíte completa do backend Go (Postgres incluso) também revalidada após a migração
0004 — nenhuma regressão.

Limite: nenhum código Go lê `scripture_verses` ainda — não há ranking híbrido em `/v1/search`.
Esse é o próximo checkpoint (item 3 abaixo). PT-BR permanece fora do RAG até uma licença real ser
confirmada.

## Bloco 5 — sessões efêmeras da Realtime API (fora do escopo original, pedido do proprietário)

O proprietário já possui a chave OpenAI configurada e pediu, à parte do plano original, que o
backend deixe pronta a integração com a Realtime API da OpenAI (voz em tempo real), para uso
futuro em um app que ele mesmo construirá depois. Esclarecimento entregue: a chamada **não** é
só do lado do app — o app nunca deve embutir a chave real (seria extraível do binário), então o
backend precisa emitir um **token efêmero de curta duração** que o app usa para conectar direto
na OpenAI (WebRTC/WebSocket), sem a chave real nunca sair do servidor.

Implementado: `POST /v1/realtime/session` (`internal/realtime`, Go), que chama
`POST https://api.openai.com/v1/realtime/client_secrets` com a chave real e devolve
`{clientSecret, expiresAt, model}` com `Cache-Control: no-store`. Fora do contrato de conteúdo
somente-leitura (§45): não lê o `store.Store`, apenas media uma credencial de terceiro. Sem
`OPENAI_API_KEY` configurada, o servidor sobe normalmente e só esse endpoint responde
`503 realtime_unavailable` — nenhum outro comportamento muda.

Especificação confirmada via documentação oficial da OpenAI (não presumida): endpoint, formato
do corpo da requisição (`session`, `expires_after`) e da resposta (`value`, `expires_at`).
Testado com chamada real: sessão efêmera genuína (`ek_...`) recebida com sucesso rodando o
binário localmente.

Validação: 8 testes Go novos (`internal/realtime`: requisição/resposta, modelo customizado,
status não-200, segredo ausente; `internal/httpapi`: não configurado → 503, sucesso, falha do
upstream não vaza detalhe). Contrato adicionado a `api/openapi.yaml`
(`RealtimeSession`, código de erro `realtime_unavailable`) — sem exemplo de contrato
byte-a-byte, pois a resposta é não determinística por natureza (segredo/expiração mudam a cada
chamada).

Limite: só a "canalização" do servidor existe; nenhum cliente (app) foi construído ou testado
contra este endpoint. Isso fica para quando o proprietário decidir construir a feature no app.

## Bloco 6 — busca híbrida em Go (`/v1/search`)

Autorização do proprietário em 2026-09-12: "manda bala" para a camada de busca sobre o texto
bíblico já indexado no Bloco 4.

Implementado: `Store.SearchPassages` (nova assinatura na interface `store.Store`), combinando
busca lexical (full-text do Postgres sobre `scripture_verses.text`) e busca semântica (distância
de cosseno via `pgvector`, `internal/embeddings` gera o vetor da pergunta do usuário em tempo
real). As duas trilhas propõem seus próprios candidatos top-N; o conjunto combinado é reordenado
pela soma das duas pontuações — uma heurística inicial documentada como ajustável, não um modelo
de relevância calibrado (validação no nível de citação é trabalho do Task 12, §31, não deste
endpoint). O campo `passages` de `/v1/search`, que antes sempre voltava vazio do servidor, agora
é preenchido de verdade — sem mudar o formato do contrato.

Bug real encontrado e corrigido pelos próprios testes de integração: o operador `<=>` do
pgvector também precisa ser qualificado por schema (`OPERATOR(public.<=>)`), não só o tipo
`vector` — do contrário falha com "operador não existe" em qualquer conexão cujo `search_path`
não inclua `public` (todos os schemas de teste isolados, e potencialmente qualquer configuração
de banco que não use `public` como schema padrão).

Degradação graciosa: sem `OPENAI_API_KEY`, a busca continua funcionando (lexical + entidade),
apenas sem a trilha semântica — nunca falha a requisição inteira por causa disso. Falha da
chamada de embedding (rede, limite de taxa) também degrada para lexical-only com log, não
propaga erro 500 pro usuário.

`internal/embeddings` (Go) é o lado "consulta" — gera o vetor da pergunta a cada busca, em tempo
real. Isso é diferente do `pipeline/scripture.py` (lado "corpus", offline, já embutiu a Bíblia
inteira uma vez). Os dois precisam concordar em modelo/dimensões (`text-embedding-3-large`,
1536) ou a distância de cosseno não tem sentido — documentado nos dois lados do código.

Testado com consultas reais contra o corpus completo (31.098 versículos): "David" retorna
passagens onde o nome aparece literalmente; "leviathan" (palavra rara) acerta as ocorrências
exatas; **"why did job suffer"** (pergunta em linguagem natural, sem citar Jó/sofrimento
literalmente na maioria dos versículos) trouxe só capítulos de Jó — evidência de que a busca
semântica está funcionando de verdade, não só a lexical.

Validação: suíte Go completa revalidada (build, vet, testes), incluindo testes novos de
integração PostgreSQL com embeddings sintéticos determinísticos (vetores "one-hot") pra provar o
ranqueamento sem depender de chamada real à OpenAI nos testes automatizados, e testes de handler
que confirmam a degradação graciosa (embedder ausente ou com falha).

Limite: a heurística de combinação de pontuações (soma lexical + semântica) não foi calibrada
com avaliação humana de relevância — é um primeiro corte razoável, não uma medida de qualidade
comprovada. A busca ainda não prioriza explicitamente referências bíblicas diretas no servidor
(§28) — isso continua sendo feito pelo parser do lado do app. Custo por busca é irrelevante
(uma chamada de embedding pequena por consulta), mas agora é uma dependência **contínua** de
produção, não uma carga pontual.

## Bloco 7 — Task 12: Ask Scripture no backend

Autorização do proprietário em 2026-09-12/13: seguir com Ask usando OpenAI para a síntese
também, confirmando o formato exato de resposta do §30 do PRODUCT.md antes de começar.

Implementado: `POST /v1/ask` (`internal/ask`, `internal/synthesis`), devolvendo exatamente o
contrato do §30 (`answer`, `summary`, `passageReferences`, `entityReferences`,
`sourceReferences`, `confidence`, `interpretiveVariance`). Pipeline: recuperação híbrida (a mesma
busca do Bloco 6) → texto das evidências → montagem do prompt → síntese via OpenAI (chat
completions, modo JSON) → validação de citação → resposta. Nunca em streaming — a recuperação
sempre termina antes da síntese começar (§73).

**Decisão central de segurança**: o modelo nunca escreve uma referência bíblica. Ele só devolve
números de índice apontando pra uma lista de evidências que o `internal/ask` já recuperou e
confirmou que existe de verdade no banco; qualquer índice fora do intervalo é descartado
silenciosamente, nunca confiado. Uma resposta que acaba citando zero evidência válida é
substituída pela mesma resposta vazia/baixa-confiança usada quando a recuperação não encontra
nada — nunca aparece como se fosse fundamentada. Isso torna "rejeitar versículo inventado" (§31)
uma garantia estrutural do código, não uma esperança de que o prompt funcione. `entityReferences`
fica sempre vazio nesta etapa (sem vinculação de entidades ainda) e `sourceReferences` é montado
pelo próprio código (não pelo modelo), a partir da tradução usada de fato.

**Achado real de teste que exigiu uma segunda camada determinística**: a regra do §31 de
recomendar ajuda profissional em perguntas de alto risco (saúde mental, jurídico, financeiro)
**não foi seguida de forma confiável só com instrução no prompt** — a pergunta "I feel hopeless
and depressed" recebeu uma resposta puramente devocional, duas vezes, mesmo depois de eu reforçar
o texto do prompt. Corrigido com uma segunda camada, agora determinística
(`internal/ask/safety.go`): uma nota fixa (não gerada pelo modelo) é anexada sempre que a
**pergunta** (não a resposta do modelo) bate com uma lista conservadora de palavras-chave,
pulando a adição só se a resposta do modelo já cobrir isso sozinha. Documentado como um piso sob
o prompt, não substituto dele — a lista não é exaustiva e não passou por uma revisão de segurança
mais ampla além dos casos testados aqui.

Testado com perguntas reais: "why did job suffer" → resposta fundamentada citando Job 2:7 (real);
pergunta fora do escopo bíblico ("qual a melhor linguagem de programação") → fallback honesto,
vazio, confiança baixa, sem nada inventado; "will everyone eventually be saved" → citou 6
passagens reais e corretamente marcou `interpretiveVariance: true` (é uma questão genuinamente
disputada entre tradições); pergunta de saúde mental → nota de ajuda profissional presente após a
correção (uma vez incluída pelo próprio modelo, com a duplicação corretamente evitada pelo
código; a garantia determinística cobre o caso em que o modelo não inclui).

Validação: suíte Go completa (build, vet, todos os pacotes), incluindo testes novos e isolados de
`internal/ask` (sem chamada real à OpenAI — cliente de síntese e retriever substituídos por
dublês) cobrindo: sem evidência não chama o sintetizador; citação válida; índices fora do
intervalo descartados; nenhuma citação sobrevivente vira fallback; confiança rebaixada com pouca
evidência; degradação quando o embedder falha; falha do sintetizador propaga erro (diferente do
embedder, que é opcional); JSON malformado do modelo rejeitado; nota de ajuda profissional
anexada e não duplicada. `internal/synthesis` testado com servidor HTTP falso (mesmo padrão de
`internal/realtime`/`internal/embeddings`).

Limite: nenhuma avaliação sistemática de qualidade/precisão de citação foi feita além das
verificações manuais acima — não é uma medida de qualidade comprovada, é um primeiro corte
funcional e razoavelmente seguro. `entityReferences` não implementado. PT-BR continua bloqueado
pela mesma lacuna de licença do Bloco 4. Sem streaming. A lista de palavras-chave de alto risco é
conservadora e não passou por revisão de segurança formal — é uma rede de proteção adicional,
não uma garantia completa de que toda pergunta sensível será tratada com o cuidado ideal.

## Bloco 8 — referência direta, vínculo de entidades no Ask, observabilidade

Autorização do proprietário em 2026-09-13: "faça o 2 e o 4" (observabilidade e os polimentos:
prioridade de referência direta na busca + vínculo de entidades no Ask), enquanto ele decide o
item 1 (conteúdo editorial real).

**Feedback recebido e ainda não resolvido**: o proprietário deixou claro que o Ask não pode virar
um orientador de vida genérico — a nota de "procure ajuda profissional" precisa nascer do que a
própria resposta já está dizendo, não de bater a pergunta contra uma lista de palavras-chave como
o `internal/ask/safety.go` faz hoje. Isso fica registrado (inclusive em memória) para revisar com
ele antes de estender essa lista.

Implementado:
- **Referência direta (§28)**: `internal/reference.ParseVerse` reconhece "Livro capítulo:versículo"
  (nome em inglês ou id OSIS — vocabulário mais restrito que o parser completo dos apps, de
  propósito). Quando bate e o versículo existe em `scripture_verses`, `/v1/search` devolve só
  aquele versículo e **nem chama a API de embedding** — economia real, não só prioridade de
  ranking. Testado: "John 3:16" acerta na hora; "John 99:99" (formato válido, capítulo
  inexistente) cai pra busca híbrida normal em vez de dar erro.
- **Vínculo de entidades no Ask**: `Store.EntitiesForPassages` liga as passagens já citadas
  (validadas) às entidades do grafo editorial que têm aquele versículo como `keyPassage` —
  nunca inventado a partir da pergunta em si, herda a mesma garantia estrutural das citações.
  Testado com "how did David defeat Goliath": ligou corretamente a David, Golias, Saul, o vale de
  Elá e o tema fé — todas entidades reais da fixture.
- **Observabilidade (§54), com escopo reduzido de propósito**: logs estruturados em JSON (`slog`,
  `VERBUM_LOG_FORMAT=text` pra terminal local) + um ID de correlação por requisição
  (`internal/reqid`), permitindo juntar as linhas de latência de embedding/recuperação/síntese e
  a linha de requisição total pelo mesmo ID. Também loga citações rejeitadas e referências que
  parseiam mas não existem no corpus. **Decisão consciente de não implementar OpenTelemetry
  ainda**: não existe coletor/vendor escolhido pra receber os traces — seria uma dependência de
  infraestrutura nova que ninguém pediu. Caminho de migração documentado no próprio código.

Validação: suíte Go completa (build, vet, todos os pacotes, incluindo `internal/reference` e
`internal/reqid` novos), testado com chamadas reais mostrando os logs correlacionados por ID
(ex.: embedding 1180ms, recuperação 248ms/6 resultados, síntese 2316ms, total 3758ms, mesmo
`reqID` nas quatro linhas).

Limite: "cache hit rate" do §54 não se aplica — este backend não tem cache. A lista de
palavras-chave de alto risco do Ask permanece sem revisão à luz do feedback do proprietário
acima — próxima conversa antes de mexer nela de novo.
