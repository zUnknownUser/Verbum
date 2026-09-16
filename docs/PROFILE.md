# Perfil — iOS e Android

> Atualização de 16/09/2026: planos, cotas persistentes, cache e voz via relay seguem [COST_CONTROL.md](COST_CONTROL.md). Essa política substitui descrições anteriores de acesso pago sem cotas ou voz diretamente no provedor.

Implementação de 16/09/2026. A entrada de perfil fica no ícone de pessoa na Home, inclusive sem cadastro.

## Organização

- Identidade: iniciais, nome e email reais; selo apenas quando o email está verificado. Sem nome, mostra “Seu espaço de leitura”. Visitantes podem criar conta ou entrar.
- Sua jornada: dias com capítulos abertos, capítulos distintos abertos, passagens salvas e continuar leitura.
- Leitura: tradução utilizada e idioma, preferências existentes de tamanho/modo/foco, orientações de áudio e voz.
- Seu Verbum: passagens salvas, destaques, notas e histórico; tocar em uma referência abre o leitor.
- Preferências: aparência automática/clara/escura persistida, lembrete matinal existente e ajustes de idioma do sistema.
- Verbum: ajuda, informações de privacidade e versão do app.
- Conta: detalhes e sair. Exclusão fica dentro dos detalhes, em uma tela de confirmação própria.

## Conta

Os detalhes usam nome, email, verificação, provedores vinculados e data de criação do Firebase. Não existem nomes, contadores, datas ou provedores fictícios. Alterar nome salva no perfil existente. Alterar email exige autenticação recente com a senha e verificação do novo endereço; o email exibido não muda antecipadamente. Alterar senha envia o fluxo de recuperação para o endereço da sessão. Essas duas ações aparecem apenas para contas com provedor de senha. A interface reconhece metadados de Apple/Google, mas esta alteração não implementa esses métodos de login.

## Dados locais

Favoritos usam o novo marcador no painel de estudo do versículo. O campo é compatível com as notas e destaques já gravados. Um versículo pode aparecer nas três coleções.

O histórico começa nesta versão: só registra capítulos carregados e apresentados pelo leitor, não capítulos vizinhos pré-carregados. Conta capítulos distintos e dias no calendário local, sem afirmar conclusão de leitura. Reabrir um capítulo atualiza sua posição e data no histórico. As preferências e coleções continuam locais ao dispositivo, inclusive após sair ou excluir a conta; não foi acrescentada sincronização entre contas/dispositivos.

A tradução segue a política de idioma existente (português/Bíblia Livre; inglês/BSB com alternativa WEB offline). Versão da Bíblia e áudio/voz levam a explicações dos recursos existentes, não a seletores fictícios. O idioma da Bíblia não é configurado separadamente do idioma do app. As telas de privacidade descrevem o comportamento atual; não substituem uma política jurídica publicada.

## Verificação

- Builds Debug de iOS Simulator e Android.
- Testes de conta em ambas as plataformas: validação, nome, troca de email, destino do email de recuperação, provedores e limpeza de senha.
- Testes de modelos/persistência: dias locais, capítulos distintos, referências inválidas, histórico persistido, marcadores e compatibilidade de notas antigas.
- Testes do perfil: coleções, falha de carregamento, preservação de dados corrompidos, aparência e exclusão de capítulos pré-carregados da contagem.
- Conferência visual e navegação em emulador Android.

A suíte geral Android tem 7 falhas anteriores a esta alteração: 4 em `ContractTest`, 2 em `VerbumApiTest` e 1 em `ScriptureFeatureTest`. Foram reproduzidas numa cópia isolada do commit `5fc4f66`. Não foram modificadas nesta tarefa. Os novos fluxos de conta são verificados com clientes simulados; nenhum email real foi alterado ou conta real excluída para testar.
