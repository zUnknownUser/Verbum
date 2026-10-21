# Preparação operacional — 21/10/2026

Rodada seguinte à limpeza de conteúdo, sem ampliar produto ou iniciar homologação.

## CI iOS

O run `39620111514` passou em Android e backend/conteúdo. O job iOS falhou
antes da compilação: Xcode 26.6 não abre o formato 110 do projeto atual.

O workflow passa a usar `xcode-27` e seleciona explicitamente
`/Applications/Xcode_27.0.app/Contents/Developer`. Mantém versões do app,
formato do projeto, SDKs e testes. YAML e diff foram validados localmente;
O run `39627865047` confirmou que Xcode 27 abre o projeto, mas revelou a falta
de autorização das macros no runner limpo. `.github/swift-macros.json` registra
somente os cinco targets e commits já autorizados localmente; o workflow confere
origem e revisão nos dois lockfiles antes de instalar essa lista no runner.
A validação global de macros permanece ativa e a resolução automática de versões
é desabilitada. A execução remota dessa correção ainda está pendente.

Referências do fornecedor: [runner Xcode 27](https://github.com/actions/runner-images/issues/14404)
e [ferramentas da imagem](https://github.com/actions/runner-images/blob/main/images/macos/xcode-27-arm64-Readme.md).

## Deploy e recuperação

- A pasta atual foi associada ao projeto Railway `verbum`, ambiente `production`,
  serviço `api` existente. Não foi criado serviço ou banco.
- Os pushes anteriores de CI/localização foram corretamente ignorados pelo deploy
  por não alterarem arquivos observados. A mudança de conteúdo altera `backend/`
  e pode executar a migração `0013` pelo pre-deploy.
- O volume `pgvector-volume` está pronto. A consulta de PITR informou `enabled=false`:
  a imagem atual `pgvector/pgvector:pg18` e o comando de inicialização personalizado
  não são compatíveis com essa função do Railway. Não trocar imagem ou startup
  automaticamente: isso exige um plano próprio de preservação do pgvector.
- PITR é diferente dos snapshots agendados do volume. Esta consulta não confirma
  a presença ou ausência desses snapshots; a pendência de backups automáticos do
  relatório anterior permanece.
- O backup local completo foi bloqueado pela revisão automática de permissões
  por poder conter dados pessoais. Lucas escolheu explicitamente enviar o código
  sem criar esse backup. Os commits `d6f704b` e `529c00a` foram enviados à main;
  nenhum backup novo foi criado e nenhuma credencial foi exibida.

## Distribuição e pendências

- A branch `main` permanece sem proteção. Não foram impostas regras que alterassem
  o fluxo de push direto solicitado pelo proprietário.
- Android Release aponta para a API de produção, mas não possui `signingConfig`
  de distribuição neste arquivo do projeto. Configurar upload key/Play App Signing
  com os dados reais da conta, sem colocar segredos no Git.
- iOS possui assinatura automática e equipe configurada; isso não comprova um
  archive distribuível nem a configuração do TestFlight. O CI usa simulador sem
  assinatura e não publica aplicativos.
- App Attest está declarado para produção no iOS. Enforcement de App Check deve
  aguardar validação dos builds distribuídos, como já previsto.
- Próximos fechamentos: push/deploy com decisão sobre backup, CI remoto verde,
  política de privacidade/metadados e assinatura das lojas; homologação por último.
