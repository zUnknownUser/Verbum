# App Check — rollout de segurança

A integração está no backend, iOS e Android. **Monitoramento não equivale a bloqueio.** A proteção de orçamento, Firebase Auth e limites por UID/IP continua ativa em ambos os modos.

## Configuração do backend

- `VERBUM_APP_CHECK_MODE=monitor` inicialmente; valores inválidos impedem startup.
- `VERBUM_APP_CHECK_APP_IDS=1:254982854245:ios:aa0d2d699a4a082d6e188c,1:254982854245:android:91dd21ee4ae6e6626e188c`
- Projeto Firebase existente: `verbum-sw`. Os IDs acima são públicos, extraídos dos arquivos Firebase dos apps; não são credenciais.
- Em `enforce`, configuração incompleta impede startup. Firebase Admin verifica assinatura, expiração e audiência; somente os IDs explicitamente aprovados passam.
- Log `app attestation` contém rota e booleano `valid`, sem token/UID/nota.
- Falha de atestação em POST pago: 403 `app_attestation_required`, sem provider. Busca autenticada degrada para lexical; conteúdo público e consulta do próprio uso continuam disponíveis. Tickets de voz continuam emitidos na rota protegida e validados/consumidos uma única vez no relay.

## O que ainda precisa de ativação externa

1. Registrar o app iOS no App Check com App Attest, Team ID `5RS2AA677K`. O entitlement `com.apple.developer.devicecheck.appattest-environment=production` já está no projeto; atualizar provisionamento/capacidade no Apple Developer conforme necessário.
2. Registrar o Android no App Check com Play Integrity e a impressão SHA-256 da assinatura da versão distribuída no Google Play. O certificado da assinatura do Play pode ser diferente do certificado local.
3. Distribuir as novas versões e validar, em aparelhos reais, leitura pública, Ask, TTS, voz, token expirado/renovado e troca de conta. A compilação sem assinatura não valida os serviços Apple/Google.
4. Conferir requisições válidas em monitoramento e só então definir `VERBUM_APP_CHECK_MODE=enforce`. Nenhuma regra nas APIs Firebase externas foi alterada automaticamente nesta implementação.

Não foi instalado um bypass de depuração no app de produção. Simulador iOS não gera token App Attest; ambientes de desenvolvimento devem usar seu backend em monitoramento. Ativar enforcement em produção antes de distribuir os novos clientes bloquearia as versões antigas.

App Check atesta o app; não fornece identidade física única por dispositivo nem substitui autenticação, limites por UID/IP, reservas e teto global. `X-Verbum-Installation` continua não confiável e não deve ser usado isoladamente para liberar benefícios. Não foram alteradas as cotas Free/Premium ou a narração padrão gratuita.

Referências oficiais: [backend](https://firebase.google.com/docs/app-check/custom-resource-backend), [iOS](https://firebase.google.com/docs/app-check/ios/app-attest-provider), [Android](https://firebase.google.com/docs/app-check/android/play-integrity-provider).
