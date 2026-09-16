# Revisão de segurança — 16/09/2026

Base do diagnóstico: `9681913`. Correções autorizadas e implementadas posteriormente nesta sessão. O diagnóstico abaixo permanece como histórico. Sem testes ofensivos em produção e sem chamadas pagas.

## Resultado das correções

- **S-01 corrigido:** HEAD e GET compartilham limites e autenticação. Rotas públicas sob `/v1/` também têm limite agregado por IP/processo/concorrência; health checks ficam fora desse grupo.
- **S-02 corrigido no armazenamento e na composição das telas:** notas, destaques, favoritos e histórico são separados por conta. A troca de conta descarta os stores/telas anteriores e encerra mídia. Clientes de persistência mantêm o proprietário original, impedindo uma gravação atrasada de atingir outra conta. Exclusão limpa dados da conta e bloqueia novas gravações de anotações; iOS mantém diretórios vazios sem permissão de escrita para impedir que gravações atrasadas do histórico recriem dados.
- Migração inicia após a primeira notificação de autenticação, atribui os dados antigos uma vez à identidade inicial e registra essa identidade antes de mover arquivos, para retomar corretamente após falhas. Visitante local e Firebase anônimo usam o mesmo espaço, evitando perder notas ou interromper leitura ao iniciar TTS. Registrar uma conta transfere as notas de visitante e renova o espaço de visitante; entrar em uma conta existente não importa dados de outra identidade. Falha ao abrir/migrar apresenta opção de tentar novamente, sem apagar o conteúdo original.
- **S-03 parcialmente tratado, com ativação pendente:** App Check integrado em iOS (App Attest), Android (Play Integrity) e backend (Admin SDK, allowlist de apps). Modo `enforce` recusa operações pagas sem atestação válida; busca mantém fallback lexical. O deploy inicia em `monitor`, para não bloquear versões instaladas sem essa integração. Configuração de provedores, distribuição e teste em aparelhos reais são necessários antes de ativar a exigência. O cabeçalho de instalação continua um sinal auxiliar, não identidade física confiável. App Check não fornece um identificador físico único e não torna a cota por dispositivo impossível de contornar.
- Dependências atualizadas; imagem de build passa de Go 1.24 para **1.27.1**. Novo govulncheck: **0 alertas em símbolos chamados**, com 0 avisos em pacotes importados e 1 em módulo sem chamada identificada. Não significa ausência absoluta de CVEs no container ou nas dependências móveis.
- Android desabilita backup e exclui dados de nuvem/transferência; iOS exclui diretórios pessoais do backup.

Validação: backend `go test ./...`, `go vet ./...` e testes com `-race` de HTTP, identidade, uso e relay passaram; a suíte completa também passou com `-race` e PostgreSQL/pgvector descartável (extensão vector pré-criada para evitar disputa de inicialização entre pacotes de teste). Imagem Docker construída e smoke local de health/readiness/conteúdo passou; inicialização real do verificador App Check e recusa de token inválido também foram verificadas sem credenciais de usuário. Android: assembleDebug e 28 testes direcionados passaram. iOS: build de simulador e build para dispositivo sem assinatura; 21 testes direcionados de conta, perfil, isolamento e cabeçalhos passaram. Os testes de URL mais amplos ainda têm 6 falhas Android e 1 iOS, reproduzidas em checkout de `9681913`, com os mesmos parâmetros de idioma/versículo ausentes nas expectativas; não foram alterados nesta correção. O runner iOS da comparação ficou preso após relatar o resultado e foi encerrado.

O aviso residual [GO-2026-5932](https://pkg.go.dev/vuln/GO-2026-5932) é sobre `golang.org/x/crypto/openpgp`, sem versão corrigida. `go mod why golang.org/x/crypto/openpgp` confirma que o módulo principal não utiliza esse pacote. A análise conservadora do binário final listou somente esse aviso pela presença do módulo `x/crypto`; `go list -deps ./cmd/api` também confirma ausência de pacotes OpenPGP. Isso não demonstra uso de OpenPGP pelo Verbum. As atualizações adicionais de gRPC 1.83.2, x/crypto 0.56.0 e x/net 0.58.0 cobrem os demais avisos encontrados na primeira análise da imagem.

Rollout detalhado: [APP_CHECK.md](APP_CHECK.md).

## Diagnóstico original

## Achados no código

### S-01 — Média: HEAD contorna os limites da busca (reproduzido localmente)

`backend/internal/httpapi/access.go:57` identifica a rota pelo método literal. O mapa contém `GET /v1/search`, mas não `HEAD /v1/search`. O ServeMux em `server.go` aceita HEAD para handlers GET. Assim, HEAD executa a busca sem os limites por IP, globais ou de concorrência da camada Protect.

Prova com `httptest`, Protect real e ServeMux real, mesmo IP e 130 chamadas sequenciais: GET executou o handler 120 vezes e rejeitou 10 com 429; HEAD executou 130 e rejeitou zero. O handler usado era uma sonda local, sem banco ou provider. O handler real em `handlers.go:151` consulta o armazenamento e faz recuperação de passagens, portanto o desvio permite trabalho de banco sem esses limites. Não houve teste de carga nem comprovação de indisponibilidade.

A proteção econômica de embeddings continua falhando de forma fechada sem UID: este achado não demonstra geração paga anônima. Outras rotas públicas também ficam fora do mapa de proteção; limites externos do ingress não foram auditados.

Correção proposta: alinhar métodos aceitos pelo roteador e middleware, aplicar a mesma política a HEAD/GET ou rejeitar HEAD explicitamente; cobrir rotas públicas com limites apropriados e testar a equivalência.

### S-02 — Média: notas e destaques não são isolados por conta (confirmado por leitura)

iOS: `VerbumKit/Sources/Clients/ReaderAnnotationsClient.swift:27` usa singleton, cache e arquivo único `reader-annotations.json`. Android: `android/core/clients/src/main/kotlin/com/nexussoft/verbum/clients/ReaderAnnotationsClient.kt:18` usa a chave global `readerAnnotations`. Nenhum desses clientes recebe UID.

O logout/exclusão em `AccountFeature` e nos clientes Firebase altera a autenticação, mas não troca ou limpa esse armazenamento. Uma segunda pessoa usando outra conta na mesma instalação continua acessando as anotações anteriores; excluir a conta também não remove essas notas locais. Não se trata de leitura remota de dados de outros aparelhos. Como as notas foram concebidas como locais ao dispositivo, a correção exige definir explicitamente a propriedade dos dados e a migração dos dados de visitante.

Correção proposta: separar armazenamento/cache por identidade, tratar transições de sessão e exclusão, preservando dados de visitante mediante regra de migração explícita.

### S-03 — Média / defesa contra abuso: identificação de dispositivo não é confiável

`backend/internal/httpapi/access.go:134` aceita `X-Verbum-Installation` fornecido pelo cliente, verificando somente comprimento. Se ausente, `usage/postgres.go` não adiciona contadores de dispositivo; um script pode omitir ou trocar esse valor. Não foi encontrada validação de App Check/App Attest/Play Integrity no backend examinado.

Isso contorna apenas a camada por dispositivo. A autenticação Firebase, os limites por UID/IP e a reserva global continuam existindo. Não demonstra acesso premium indevido nem gastos ilimitados. Correção proposta: tratar o cabeçalho como sinal auxiliar, acrescentar atestação validada no servidor e política de abuso que não dependa de um identificador livre; apenas exigir o cabeçalho não resolve falsificação.

## Dependências — atualização prioritária, exploração não confirmada

Executados com Go local 1.27.1 e govulncheck 1.8.0:

```sh
go run golang.org/x/vuln/cmd/govulncheck@v1.8.0 ./...
go run golang.org/x/vuln/cmd/govulncheck@v1.8.0 ./cmd/api
```

Ambos apontaram **8 alertas em 6 módulos no nível de símbolos**, além de 5 em pacotes importados e 30 em módulos requeridos sem chamada identificada. Código de saída 3 do scanner, apresentado como 1 por `go run`. Isso é evidência de dependências afetadas e possíveis caminhos de chamada, não prova de oito ataques exploráveis pela API. Rastros via inicializadores/interfaces precisam de triagem adicional.

| Módulo | Instalado | Versão corrigida indicada | Aviso |
| --- | --- | --- | --- |
| google.golang.org/grpc | 1.72.0 | 1.83.1 | [GO-2026-6348](https://pkg.go.dev/vuln/GO-2026-6348), esgotamento de memória HTTP/2 |
| google.golang.org/grpc | 1.72.0 | 1.82.1 | [GO-2026-6061](https://pkg.go.dev/vuln/GO-2026-6061), transporte HTTP/2 / xDS RBAC |
| golang.org/x/text | 0.27.0 | 0.39.0 | [GO-2026-5970](https://pkg.go.dev/vuln/GO-2026-5970), loop infinito |
| golang.org/x/net | 0.42.0 | 0.55.0 | [GO-2026-5026](https://pkg.go.dev/vuln/GO-2026-5026), validação IDNA |
| github.com/jackc/pgx/v5 | 5.7.6 | 5.9.2 | [GO-2026-5004](https://pkg.go.dev/vuln/GO-2026-5004), injeção SQL condicional |
| github.com/go-jose/go-jose/v4 | 4.0.5 | 4.1.4 | [GO-2026-4945](https://pkg.go.dev/vuln/GO-2026-4945), panic em JWE |
| golang.org/x/net | 0.42.0 | 0.53.0 | [GO-2026-4918](https://pkg.go.dev/vuln/GO-2026-4918), loop HTTP/2 |
| go.opentelemetry.io/otel/sdk | 1.35.0 | 1.40.0 | [GO-2026-4394](https://pkg.go.dev/vuln/GO-2026-4394), PATH hijacking |

O aviso de pgx exige protocolo simples não padrão, SQL com literais dollar-quoted e conteúdo controlado por atacante em condições específicas. Não foi encontrado uso explícito de QueryExecModeSimpleProtocol; as consultas examinadas usam parâmetros. Não foi demonstrada SQL injection no Verbum. O aviso de OpenTelemetry não é evidência de execução remota de código pela API.

O Dockerfile usa `golang:1.24`, diferente do Go local. Não foram extraídos o binário de produção nem a versão patch efetiva da imagem: o resultado local não certifica a biblioteca padrão nem o sistema operacional do container. Atualizar a toolchain e dependências de forma compatível e repetir testes e scanner na imagem final.

## Observação sobre backup

Android habilita `allowBackup=true` em `android/app/src/main/AndroidManifest.xml:17`, sem regras explícitas de exclusão no manifesto fonte. As notas ficam nas preferências, elegíveis ao backup padrão. É uma decisão de privacidade/retenção que precisa ser compatível com a promessa de dados locais; não é, por si só, exposição pública. Não foi validada restauração em dispositivo. Referência: [Android Auto Backup](https://developer.android.com/identity/data/autobackup).

## Proteções observadas e limites

- Firebase verifica assinatura, emissor, audiência, expiração e revogação; configuração de emulador é rejeitada.
- Plano premium é decidido no armazenamento do backend, não por campo livre do cliente.
- TTS verifica capítulo e texto canônicos, restringe voz/velocidade e aplica cache e reservas persistentes de orçamento.
- Busca simples de padrões de chaves privadas, tokens OpenAI/GitHub e AWS nos arquivos rastreados de até 2 MB não encontrou correspondências. Não é auditoria completa do histórico git ou dos segredos de produção; a configuração Firebase pública não equivale a chave privada.
- Revisão direcionada, não pentest completo. Não foram auditadas todas as dependências móveis, permissões IAM, regras Firebase remotas, configurações do ingress ou a imagem implantada.

Próximos passos: corrigir S-01, atualizar dependências/toolchain e validar a imagem, definir isolamento e exclusão de dados locais, reforçar defesa contra abuso por dispositivo. Nenhuma dessas correções foi aplicada nesta revisão.
