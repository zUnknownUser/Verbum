# Deploy das correções de segurança — 16/09/2026

Código publicado: `2c83b4b48384dd3999ac3893a504821ac299cf2e` (`main`).
Railway: projeto `verbum`, ambiente `production`, serviço `api`.
Deploy: `49ec0850-2cd5-4d21-860a-861a8b9ffcea`, **SUCCESS**.
API: https://api.vendlydigital.com.br

O deploy compilou a imagem com Go 1.27.1; o módulo agora requer Go 1.26 ou superior. O runtime iniciou com `app attestation policy mode="monitor" configured=true` às 17:57:33 UTC. As variáveis de App Check foram configuradas com `--skip-deploys` antes do push, e o push disparou um único deploy do código.

## Verificações em produção

| Requisição | Resultado |
| --- | --- |
| GET /healthz | 200 |
| GET /readyz | 200 |
| GET /v1/entities?type=person | 200 |
| GET /v1/search?q=John 3:16, sem token | 200 |
| HEAD /v1/me/usage, sem token | 401 |
| GET /v1/me/usage, sem token | 401 |
| POST /v1/ask, sem token | 401 |

Não foram criados usuários nem executadas gerações pagas. A equivalência de limites HEAD/GET foi testada com httptest local; não foi feito teste de carga em produção.

## Validação anterior ao push

- Backend: `go test -race ./...` com PostgreSQL/pgvector descartável e `go vet ./...` passaram. A extensão vector foi inicializada antes da execução paralela. Containers locais de teste foram removidos ao terminar.
- Docker final: build e smoke de saúde/prontidão/conteúdo passaram.
- govulncheck de código: zero avisos em caminhos chamados ou pacotes importados; um aviso de módulo sem uso do pacote afetado. Scanner conservador do binário lista esse mesmo aviso, GO-2026-5932 (OpenPGP), que não integra `go list -deps ./cmd/api`. Detalhes na auditoria.
- iOS: builds para simulador e dispositivo sem assinatura passaram; 21 testes direcionados de conta/perfil/isolamento/autorização passaram.
- Android: assembleDebug e 28 testes direcionados de conta/perfil/isolamento/autorização passaram.
- Testes amplos de URLs: 6 falhas Android e 1 iOS preexistentes, reproduzidas no commit anterior. Preservados nesta alteração.
- OpenAPI parseado como YAML; `git diff --check` sem pendências.

## Distribuição e ativação restantes

As mudanças móveis estão no GitHub, mas não foram publicadas no TestFlight/App Store ou Google Play. O comportamento de migração de dados passa a valer ao instalar a nova versão.

App Check está **em monitoramento, sem enforcement**. Ainda é necessário registrar/configurar App Attest e Play Integrity, validar builds assinadas em aparelhos reais, distribuir as versões e então ativar enforcement. As proteções existentes de Firebase Auth, quotas e orçamento continuam ativas. Narração padrão gratuita e limites Free/Premium não foram alterados.

Referências: [auditoria e correções](SEGURANCA_2026-09-16.md), [ativação do App Check](APP_CHECK.md).
