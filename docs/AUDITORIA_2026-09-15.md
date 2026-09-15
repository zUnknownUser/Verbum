# Auditoria da documentação e das lacunas do Verbum

Data: 15/09/2026.

Atualização após a auditoria: proteção das APIs pagas e integração STEP foram implantadas em
produção em 15/09, com identidade Firebase, limites e integração nos dois apps. O proprietário
revisou/aprovou o lote STEP, publicado com reexecução idempotente. Ask e TTS passaram por uma
verificação real autenticada; aceite amplo de qualidade e QA em dispositivos continuam pendentes.
O texto abaixo
preserva o diagnóstico original; progresso em [ROADMAP.md](ROADMAP.md) e detalhes em
[backend/SECURITY.md](../backend/SECURITY.md).

## Escopo e conclusão

Foram lidos os 14 documentos Markdown existentes no repositório e o contrato
`api/openapi.yaml`, com conferência dirigida do código iOS, Android, backend e pipeline.
Esta é uma revisão estática: não foram executados builds, testes, chamadas pagas,
consultas ao banco de produção ou verificações de consoles externos. Relatos de testes
e implantação nos documentos são evidências históricas, não validações novas.

**O Verbum tem uma base implementada ampla, mas ainda não cumpre todo o MVP definido
em PRODUCT.md §20.** As principais lacunas são profundidade editorial do Contexto,
conteúdo revisado, camada pessoal, proteção das APIs pagas e validação de lançamento.
A documentação também precisa distinguir estado atual de histórico: algumas
instruções mandam implementar integrações que já existem.

## 1. Estado das funcionalidades

| Área | Estado encontrado | O que falta |
| --- | --- | --- |
| Leitor e navegação | Implementados nos dois apps; referências, capítulos, seleção, tamanho de texto e cache | Aceite em dispositivos; melhor aproveitamento de títulos, parágrafos e notas das fontes; política de versões/traduções |
| Busca | Parser local, nomes de livros, entidades e busca híbrida no backend | Avaliação sistemática de relevância e cobertura em português; contrato atualizado |
| Entidades, grafo e timeline | Telas, navegação e API existem | Conteúdo editorial real e aceite do percurso completo; salvar/anotar ainda não funciona como camada pessoal |
| Contexto | Entidades, passagens relacionadas e fontes por capítulo | Resumo contextual, interlocutores e explicação histórica/literária previstos no produto |
| Ask Scripture | Backend e clientes reais existem; referências estruturadas são validadas | Avaliação da fidelidade das respostas, cobertura PT-BR, fontes contextuais e revisão da regra de segurança pendente |
| Voz | Sessão efêmera, transporte de áudio e ferramentas implementados | QA de áudio/dispositivos e política de qualidade própria; o caminho de voz não passa pelo mesmo validador final do Ask |
| Áudio de leitura | Google TTS em português, gravações em inglês, cache e versionamento | QA das mudanças recentes, gestão operacional do cache; tempos por palavra/versículo e downloads explícitos continuam futuros |
| Conta | Firebase: email/senha, anônimo, verificação, recuperação e exclusão | QA real/configuração externa e autorização no backend; conta não significa sincronização |
| Jornada | Tela vazia nos dois apps | Histórico, eventos, agregações e interface funcional |
| Biblioteca | Tela vazia nos dois apps | Itens salvos, filtros, pesquisa e persistência |
| Notas | Previstas na especificação; implementação não localizada | Modelo persistente, editor, vínculo a entidades/passagens, privacidade e eventual sincronização |
| Onboarding | Obrigatório no MVP; fluxo não localizado | Introdução à exploração e controle da primeira abertura |
| Analytics | Eventos e métricas propostos; implementação não localizada | Instrumentação, definições dos eventos e avaliação da hipótese do produto |
| Exploração guiada por sentimento | Caminhos locais predefinidos, explicitamente editorial-preview | Definir se essa versão é suficiente para lançamento; não descrevê-la como personalização por RAG |
| Monetização | Planejada | Entitlements, limites, compras/restauração e validação no servidor, se fizer parte do lançamento |

Evidências: [MVP](PRODUCT.md), [roadmap](ROADMAP.md),
[telas vazias iOS](../VerbumKit/Sources/Features/Explore/ExploreView.swift),
[navegação Android](../android/feature/scripture/src/main/kotlin/com/nexussoft/verbum/feature/scripture/ui/AppScreen.kt),
[clientes reais iOS](../VerbumKit/Sources/Clients/VerbumAPI/Clients+Live.swift),
[exploração guiada](../VerbumKit/Sources/Clients/GuidedExplorationClient.swift).

## 2. Lacunas prioritárias

### 2.1 Contexto ainda não entrega toda a promessa central

PRODUCT.md §10, §21.2 e §22.4 preveem resumo, quem fala, para quem, onde, quando e
contexto histórico/literário. O modelo e contrato atuais entregam apenas referência,
entidades, passagens relacionadas e fontes. A consulta associa entidades e passagens;
isso não produz uma explicação contextual revisada.

É necessário ampliar, em conjunto, conteúdo editorial, modelo, API e interface. Não
basta adicionar um parágrafo gerado à tela. As relações entre passagens também precisam
distinguir associação temática, citação, paralelo e outros tipos quando houver evidência.

Evidências: [modelo](../VerbumKit/Sources/Models/PassageContext.swift),
[consulta](../backend/internal/store/postgres/context.go), [contrato](../api/openapi.yaml).

### 2.2 O conteúdo versionado ainda é de desenvolvimento

A fixture atual tem **45 entidades**: 13 pessoas, 10 lugares, 10 temas, 4 eventos e
8 passagens. Também contém 59 relações, 25 detalhes, 16 entradas de timeline, 2 fontes
e 101 referências diárias. Os 16 itens de timeline não equivalem a 16 entidades do tipo evento.
As **148 decisões** da fila editorial versionada continuam `pending`.

Ter API e banco funcionando não demonstra cobertura editorial de produção. Falta
definir o recorte de lançamento, suas fontes, responsáveis, revisão, traduções e critérios
de completude. A documentação do deploy registra seed de fixtures; não consultei o banco
remoto para determinar o conteúdo servido hoje.

Há ainda um problema de requisito: pipeline/README.md atribui a §68 a meta aproximada
de 50 pessoas, 30 lugares, 20 temas e 40 eventos, mas PRODUCT.md §68 apresenta exemplos,
sem estabelecer esses números. A meta precisa ser formalizada ou corrigida.

Evidências: [fixture](../backend/db/seed/fixtures.json),
[decisões](../pipeline/review/fixtures/decisions.json), [pipeline](../pipeline/README.md).

### 2.3 APIs com custo não têm proteção de acesso no código do servidor

O roteador publica Ask, TTS, busca semântica e emissão de credenciais Realtime com
apenas middleware de logs. Não há autenticação de clientes, limite de requisições por
usuário/IP ou controle de consumo nessa camada. Firebase está nos apps, sem validação
de identidade no backend. A quota de TTS recebida do provedor não substitui limites do app.

Isso deixa uma lacuna concreta frente a PRODUCT.md §56: quem alcança essas rotas pode
acionar recursos pagos. O parâmetro de modelo da sessão de voz também vem do cliente.
Antes de acesso público amplo, definir identidade inclusive para convidados, autorização,
limites, modelos permitidos e monitoramento de consumo. Proteções externas eventualmente
configuradas não foram verificadas nesta auditoria.

Evidências: [roteador](../backend/internal/httpapi/server.go),
[handlers](../backend/internal/httpapi/handlers.go), [autenticação](AUTHENTICATION.md).

### 2.4 Ask valida a referência, mas não prova cada afirmação da resposta

O validador garante que índices citados apontam para evidências recuperadas existentes.
`answer` e `summary` continuam sendo texto gerado; não há verificação automática de
que cada afirmação é sustentada por essas evidências. Uma referência válida pode
acompanhar uma interpretação incorreta. A confiança também não está calibrada por avaliação.

Falta um conjunto de perguntas com avaliação editorial: relevância, fidelidade ao texto,
qualidade das citações, recusa/fallback e divergências interpretativas. A recuperação atual
usa versículos, sem recuperação de comentários ou explicações históricas revisadas.
O fallback sem resposta devolve listas vazias, embora a experiência prevista mencione
mostrar as passagens próximas; hoje o usuário precisa voltar à busca.

A voz responde diretamente pelo Realtime e usa ferramentas de recuperação sob instruções.
Documentar e testar esse comportamento separadamente; não atribuir automaticamente à voz
a garantia estrutural da resposta HTTP de Ask.

Evidências: [orquestração e validação](../backend/internal/ask/ask.go),
[prompt](../backend/internal/ask/prompt.go), [limitações registradas](../backend/README.md),
[arquitetura de voz](ARCHITECTURE.md).

### 2.5 Português está em níveis diferentes entre as camadas

A interface e a leitura suportam português; o corpus documentado do RAG é WEB em inglês.
A busca textual usa configuração inglesa, o Ask não recebe um idioma explícito no seu
contrato e alguns fallbacks são strings em inglês. Isso não prova que toda pergunta em
português falhe, mas impede afirmar cobertura equivalente e comportamento de idioma garantido.

Faltam uma decisão documentada de licenciamento por uso (leitura, distribuição offline,
indexação e áudio), corpus PT-BR, tratamento de idioma/tradução nas consultas e avaliações.
Não basta carregar outra tradução: o contrato de busca retorna referências sem tradução,
e a consulta precisa definir como filtrar/combinar corpus diferentes.

Não foi feita análise jurídica das licenças: a lacuna encontrada é ausência de uma
decisão consolidada, explicitamente pendente nos documentos do próprio projeto.

### 2.6 Camada pessoal e offline estão incompletos

Cache de capítulos, respostas GET, áudio, preferências e última leitura já existem.
O que falta é armazenamento durável de itens do usuário: salvos, notas, histórico e
fila de sincronização, incluindo conflitos, migração e comportamento entre contas.

A especificação precisa conciliar §39 (notas editáveis offline) com §48 (conta para
notas/salvos), inclusive o que acontece com um convidado. Exclusão de conta Firebase
hoje não exclui preferências locais; isso está corretamente explicado em AUTHENTICATION.md.
Downloads garantidos são diferentes de arquivos em cache sujeitos a remoção. Português
nunca aberto depende da rede; o fallback bíblico completo embutido é inglês.

### 2.7 Qualidade e operação ainda não têm aceite consolidado

Existem muitos testes de domínio, reducers, contratos e backend. Portanto, não é correto
dizer que o projeto não tem testes. Porém, entregas recentes registram testes apenas
adicionados/compilados, QA pendente e compilação de testes iOS interrompida com código 137.
Não localizei configuração de CI no repositório.

Falta registrar um aceite por versão/commit para: percurso David → grafo → Golias →
1 Samuel 17 → Contexto → passagem relacionada; dispositivos reais; acessibilidade;
erros/offline; áudio em segundo plano; autenticação real e orçamentos de desempenho.
Navegação interna e abertura por notificação existem, mas não localizei roteamento de
links externos para entidades/passagens, requerido por §76.

O deploy Railway está documentado como realizado em 14/09. Faltam procedimentos
consolidados de backup e restauração, recuperação de falhas, rollback, ambientes,
retenção de logs, alertas e limite/limpeza do cache TTS. `/healthz` só retorna `ok`:
não demonstra banco acessível, corpus carregado ou serviços pagos configurados.
O migrador reaplica arquivos idempotentes sem tabela de versões; mudanças futuras
precisam de política explícita de migração e compatibilidade.

O TTS serializa novas gerações por processo, mantém cache local sem expiração automática
e pode ocupar uma requisição por até dez minutos. Definir capacidade esperada, disco,
comportamento sob concorrência e observação da espera antes de aumentar o tráfego.

Evidências: [QA de conta](AUTHENTICATION.md), [deploy](../backend/DEPLOY.md),
[TTS](../backend/TTS.md), [migrador](../backend/cmd/migrate/main.go).

## 3. Contradições e documentação desatualizada

| Documento | Problema | Correção necessária |
| --- | --- | --- |
| BACKEND_RAG.md, “Ponto de retomada atual” | Afirma zero integração nos apps | Registrar clientes reais, Ask, voz e áudio; mover relato antigo para histórico |
| pipeline/README.md | Diz que nenhum código Go lê scripture_verses e Ask é próximo bloco | Atualizar para busca híbrida e Ask já implementados |
| backend/README.md | “Apps go live” ainda aparece como próximo passo e voz como backend apenas | Atualizar integração mobile |
| ARCHITECTURE.md e OpenAPI | Descrevem endereço como túnel para máquina local | Conciliar com DEPLOY.md, que registra migração Railway em 14/09 |
| ROADMAP.md, áudio | Mantém descrições de voz nativa, CAF/WAV e limites antigos | Registrar estado vigente: Google TTS/MP3, cache versionado; preservar histórico separado |
| BACKEND_RAG.md, início | Pede integração TTS no app | Integração já existe em NativeSpeechAudio.swift e NativeScriptureAudioClient.kt |
| OpenAPI, Search | Afirma que servidor não prioriza referência direta | Handler já usa parser e consulta direta antes da busca híbrida |
| OpenAPI, AskResponse | Diz que entityReferences é sempre vazio | Vínculo por passagens citadas já implementado |
| OpenAPI, convenções | Trata tudo fora de /me como leitura cacheável | Excluir explicitamente os POSTs de geração e sessão |
| OpenAPI, respostas | Ask/Realtime descrevem 200/503, mas handlers também retornam 400/502 conforme rota | Documentar erros efetivos e compatibilidade |
| DEPLOY.md, exemplo Ask | Envia language, que o handler não usa | Remover parâmetro sem efeito ou implementar contrato de idioma |
| DESIGN_SYSTEM.md | Diz que barra do leitor esconde ao rolar | ChapterReaderView fixa barra visível; roadmap registra motivo |
| DESIGN_SYSTEM.md | Regra “somente cores semânticas” seguida de cores paper/ink próprias | Delimitar regra de chrome e exceção da página |
| ROADMAP.md | Fase pessoal toda vazia apesar da autenticação entregue | Separar conta, salvos, notas, jornada e sync |
| ROADMAP.md | Marcas de conclusão convivem com QA/e2e pendente | Separar implementado, compilado, testado, validado e implantado |
| ARCHITECTURE.md, versículo diário | Menciona /daily-verse?date= | Atualizar para /v1/daily-verse?from=&days= |
| API_ROUTES.md | Lista /v1/tts/config sob “Not routes” | Mover para a seção de rotas TTS |
| PRODUCT.md §79 | Continua mandando começar bootstrap e adiar IA | Identificar como sequência histórica; apontar próximo marco atual |

Outros pontos de contrato: o limite de `question` é descrito em caracteres, mas o
handler Go usa `len`, contando bytes UTF-8. Alinhar o comportamento para texto acentuado.
`SourceReference` público só expõe id/citação/URL, enquanto §33 prevê página/seção/licença;
o pipeline armazena metadados adicionais, mas é preciso decidir o que o usuário inspeciona.

## 4. Documentos e decisões que faltam

1. **README de entrada útil:** mapa do monorepo, pré-requisitos, setup macOS/Android/backend,
   variáveis por ambiente, comandos, exemplos e índice de documentação. Hoje tem cinco linhas.
2. **Estado atual do produto:** matriz de funcionalidades e critérios de saída do MVP,
   separada do changelog. Resolver o que é obrigatório em §20 versus prioridades de §72.
3. **Modelo de dados:** documentação de tabelas, IDs, relações, traduções, migrações,
   dados pessoais e contratos de evolução. DATA_MODEL.md é sugerido em §77 e não existe.
4. **Guia editorial:** escolha de fontes, revisão, licenças, graus de confiança, tratamento
   de divergências, correções, retirada de conteúdo e duplicatas. O pipeline tem revisão,
   mas não substitui toda essa política; CONTENT_GUIDELINES.md também é sugerido e não existe.
5. **Operação:** configuração completa por ambiente, backup/restauração, alertas, custos,
   segredos, diagnóstico e rollback reproduzível.
6. **QA e lançamento:** dispositivos/versões suportados, testes requeridos, evidências
   associadas ao commit, publicação nas lojas, suporte e feedback.
7. **Privacidade:** inventário dos dados e destinos (Firebase, provedores de IA/voz,
   caches, logs), retenção, exclusão/exportação e texto apresentado ao usuário. `no-store`
   da API não documenta por si só o tratamento de dados pelos provedores externos.
8. **Analytics:** semântica dos eventos, exploração significativa, retenção e limites
   sobre perguntas/notas sensíveis; sem instrumentação o MVP não mede sua hipótese central.

AI_RAG.md não existe com esse nome, mas BACKEND_RAG.md já cobre grande parte do tema.
Consolidar e corrigir o conteúdo existente é mais útil que criar outro documento duplicado.
O guia Android também precisa ficar explícito: PRODUCT.md ainda se apresenta como documento
para iOS, enquanto o produto efetivo tem dois clientes nativos.

## 5. Trabalho futuro que não precisa bloquear o MVP

Grupos, colaboração, mapas avançados, comparação completa de tradições, ferramentas
profundas de idiomas originais, rotas históricas, genealogias e funções sociais estão
explicitamente além do primeiro escopo. Áudio com destaque sincronizado também pode continuar
adiado. Streaming de Ask não é uma pendência obrigatória: o produto prioriza evidência antes
de apresentar a resposta. OpenTelemetry específico tampouco é obrigatório se a solução de
observabilidade escolhida fornecer as medidas e alertas necessários.

## 6. Ordem de fechamento sugerida

1. Consolidar estado atual e escopo de lançamento; corrigir os contratos/documentos contraditórios.
2. Fechar proteção de APIs pagas, decisões de fontes/licenças e operação recuperável.
3. Entregar um recorte editorial real com Contexto completo e validar o percurso central.
4. Completar salvos, notas, Jornada/Biblioteca e onboarding, ou revisar explicitamente o MVP.
5. Fechar cobertura/qualidade do Ask e português, além da validação específica de voz.
6. Implantar CI, instrumentação e aceite em dispositivos; registrar evidências de lançamento.

Não cabe um percentual único de conclusão: telas implementadas, qualidade editorial,
validação em dispositivos e prontidão operacional estão em estágios diferentes.
