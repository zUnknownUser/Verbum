# Deploy — proteção de custos e perfis

Autorizado por Lucas em 16/09/2026. Código do backend publicado no commit
`ad7fab8f52e4b6e981340df1b142cb1252fff447` da `main` de `zUnknownUser/Verbum`.

- Railway: projeto `verbum`, ambiente `production`, serviço `api`.
- Deployment: `c1f8c067-fce2-459d-b662-b94a7ebafc2e`, estado **SUCCESS**.
- Processo iniciou em 16/09/2026 às 15:43:14 UTC (11:43:14 Manaus).
- Migrações aditivas executadas pelo pre-deploy `/app/migrate`; a inicialização exige a tabela da migração 0007, e a consulta autenticada de cotas confirmou acesso às novas tabelas.
- Volume original de áudio preservado. Banco e conteúdo editorial preservados.
- Política padrão ativa: orçamento global estimado US$ 5/dia. Detalhes, cotas e configuração em [COST_CONTROL.md](COST_CONTROL.md).

## Verificações em produção

| Verificação | Resultado |
|---|---|
| `/readyz`, `/healthz` | HTTP 200 |
| Versículo diário, busca pública e configuração de TTS | HTTP 200 |
| Saldo sem autenticação | HTTP 401 |
| Saldo com identidade Firebase anônima temporária | HTTP 200, guest, Ask 3, TTS 1, embedding 20, voz 0 |
| Tentativa de voz pelo visitante | HTTP 403, `plan_required` |
| TTS com livro canônico inválido | HTTP 400 |
| Ask com pergunta acima de 500 caracteres | HTTP 400 |
| Saldo após operações recusadas | Inalterado |
| Exclusão da identidade Firebase temporária | Confirmada |

Nenhuma geração paga foi executada nessas verificações. A integração real de voz/TTS com provedores após esta alteração não foi exercitada por esse smoke test; os testes locais usam provedores simulados.

## Validação local

- Backend: suíte completa `go test -race ./...` com PostgreSQL/pgvector descartável e `go vet ./...` passaram. Container local de testes removido ao terminar.
- iOS: build do app passou; 18 testes selecionados de autorização, cotas, Ask, perfil e voz passaram no simulador iOS 27. Os 3 testes de autorização foram repetidos após o ajuste de instalação nas buscas e passaram.
- Android: `assembleDebug` e 24 testes selecionados de autorização, cotas, Ask, perfil e voz passaram. Build e os 3 testes de autorização foram repetidos após o ajuste de instalação nas buscas e passaram.
- Exemplos TTS foram atualizados para capítulos canônicos completos e comparados às mesmas fixtures de leitura dos apps.
- OpenAPI validado como YAML; `git diff --check` sem erros.

## Distribuição e pendências externas

O deploy acima é do **backend**. O código de iOS e Android está no GitHub, mas não foi enviado à App Store/TestFlight ou Google Play. Apps antigos precisam ser atualizados: novas gerações de áudio exigem referência canônica, e voz passa a usar um ticket Verbum no relay.

A separação free/premium e expiração de acesso estão implementadas no servidor. A ativação atual é administrativa pelo CLI `usage-admin`; cobrança, validação de recibos e notificações de assinatura Apple/Google ainda não estão conectadas. Não foi criada uma assinatura comercial nem concedido premium a uma conta de usuário durante o deploy.

O relatório de código morto foi preservado em [CODIGO_MORTO_2026-09-16.md](CODIGO_MORTO_2026-09-16.md). O redesign dos perfis das duas plataformas também foi incluído no push.
