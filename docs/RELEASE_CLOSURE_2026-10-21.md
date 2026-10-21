# Fechamento da versão — 21/10/2026

## Escopo

Rodada sobre `0e2ee12`, preservando App → Features → Clients → Models,
design system e catálogos: 80 temas, 137 eventos, 98 entradas na linha do
tempo e a sequência de 16 eventos do Êxodo. Sem funcionalidades novas,
assinaturas, publicação de conteúdo ou geração paga de áudio.

## Alterações

- Privacidade PT-BR/EN em iOS e Android: excluir uma conta cadastrada apaga
  dados pessoais sincronizados e dados de leitura daquela conta no aparelho;
  sair não os apaga. Fluxo separado de visitante preservado.
- API exige PostgreSQL por padrão. Fixtures precisam de
  `VERBUM_ENV=development` e `VERBUM_CONTENT_MODE=fixtures`, sem URL de banco.
  Configuração ausente, conflitante ou desconhecida impede startup.
- Detalhes sem fontes associadas retornam lista vazia, sem atribuição editorial
  inventada. Associações explícitas e IDs foram preservados.
- Testes Go de configuração e proveniência; regra equivalente no store em memória.
- Testes iOS antigos atualizados para Biblioteca/Jornada, cache do leitor e
  parâmetros de idioma/versículo. Verificações exaustivas do TestStore mantidas.
- CI: Go/race/vet, PostgreSQL isolado, pipeline e contrato Python–Go, testes JVM
  e Android, build Android, build e testes iOS. Sem deploy ou credenciais reais.
- Documentação de execução local e privacidade atualizada.

## Validação local

- Build iOS Simulator passou no Xcode 27; 305 testes em 67 suítes passaram.
- Android: build Debug e 303 testes em 58 suítes, sem falhas ou skips.
- Go: suíte com `-race -count=1` e PostgreSQL descartável passou; `go vet ./...`
  passou. Testes que exigem FFmpeg não executaram no host macOS; o CI instala FFmpeg.
- Pipeline: 67 testes passaram, incluindo contrato HTTP Python–Go e publicação
  em schemas isolados. Um teste opcional do snapshot STEP completo não executou
  porque a cópia externa correspondente não está configurada.
- Binário Go: banco ausente, fixtures em produção e modo desconhecido recusados;
  fixtures explicitamente em desenvolvimento ficaram prontas.
- JSON/XML válidos e privacidade equivalente entre plataformas em cada idioma.
  Alteração prévia em `Localizable.xcstrings` preservada.

Builds e testes automatizados não equivalem ao aceite em aparelhos físicos.

## Pendências para publicação

1. Conferir primeira execução remota do CI e configurar verificações obrigatórias
   na branch. Confirmar os toolchains exigidos no runner, sem reduzir versões.
2. Validar duas contas em dois aparelhos: nota offline, reconexão, alterações
   concorrentes, troca de conta, saída e exclusão sem misturar dados pessoais.
3. Aceitar PT-BR/EN nos dois sistemas: acessibilidade, fonte grande, tela pequena,
   estados vazios/erro, leitura offline e áudio em segundo plano/interrupções.
   Usar áudio em cache; nenhuma pré-geração paga foi iniciada.
4. Confirmar backups automáticos e restauração; acompanhar crashes e falhas de
   sincronização sem registrar notas, transcrições, tokens ou credenciais.
5. Validar App Attest/Play Integrity em builds distribuídos antes de enforcement.
   A configuração de produção não foi alterada por esta rodada.
6. Implantar a limpeza de fontes de demonstração preparada na rodada de conteúdo.
   A migração `0013`, as proteções para conteúdo posterior e os testes estão em
   [CONTENT_CLOSURE_2026-10-21.md](CONTENT_CLOSURE_2026-10-21.md).
7. Confirmar política de privacidade publicada, declarações de dados, assinatura
   e metadados das lojas; revisar amostra delimitada do conteúdo com leitor humano.

Na implantação, `VERBUM_DATABASE_URL` deve estar configurada. O modo padrão
é `postgres`; `make run-fixtures` serve somente ao desenvolvimento explícito.

Compras Apple/Google, escala horizontal do áudio e novos catálogos ficam fora
deste beta. Exploração por sentimento continua editorial e identificada como
prévia; nenhuma personalização foi simulada.
