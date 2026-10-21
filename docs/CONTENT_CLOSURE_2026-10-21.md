# Fechamento de conteúdo e fontes — 21/10/2026

## Recorte

Correções sobre `1e06fd6`, sem aumentar o catálogo, mudar a identidade visual
ou criar módulos. Preservados App → Features → Clients → Models, os IDs existentes,
80 temas, 137 eventos, 98 entradas na linha do tempo e 16 eventos na sequência do Êxodo.
Homologação em aparelhos permanece para a última etapa.

## Achados e correções

- Consultas públicas somente de leitura confirmaram `fixture.source.editorial`
  em detalhes de Pessoas/Lugares em produção. Essa fonte se declara provisória;
  não foi renomeada para aparentar revisão. A migração `0013` retira suas
  associações em entidades, detalhes, relações e timeline. Mantém o registro da
  fonte e os recibos de publicação para rastreabilidade.
- No seed legado, 13 relações dependem exclusivamente dessa fonte. A migração
  retira essas relações; conexões com evidência adicional mantêm a fonte válida.
  O total efetivo em produção deve ser conferido na implantação.
- Cinco datas de pessoas e cinco identificações geográficas modernas do seed
  não possuem fonte histórica específica no detalhe. A retirada compara ID,
  valor original e associação de demonstração, preservando campos alterados
  posteriormente ou detalhes com uma fonte adicional. A tradução PT-BR antiga
  correspondente também é retirada. Não são criadas datas ou geografias substitutas.
- Timeline legada ainda sustentada apenas pela demonstração perde resumo e
  data sem evidência; o título/ID permanece. A timeline atual, com referências
  bíblicas e ordem narrativa explícita, permanece integralmente preservada.
- A API atribui a apresentação à localização selecionada no idioma solicitado.
  Uma tradução antiga de um evento/tema já substituído deixa de aparecer como
  autora do texto atual. Dados STEP e evidências bíblicas continuam atribuídos.
- Os três rótulos existentes de autoria editorial recebem apresentações EN/PT-BR
  próprias, sem atribuir as notas a estudiosos externos. Traduções editoriais já
  cadastradas são preservadas.
- iOS e Android permitem abrir as fontes no detalhe da entidade usando os tokens
  existentes. A ausência de fonte passa a ter mensagem explícita nos dois idiomas.
  Detalhe, Contexto, estudo do versículo e Ask compartilham a validação de endereço
  HTTPS completo do modelo; uma URL inválida não vira um botão de abertura.
- Teste offline verifica fontes, passagens, referências entre módulos, vínculos da
  sequência e tamanho do catálogo. Todas as 525 seleções bíblicas do catálogo atual
  existem no corpus WEB versionado; os caminhos da timeline são iguais em EN/PT-BR.
  Essa verificação prova integridade referencial, não exatidão de cada interpretação.

## Mocks e limites

`backend/db/seed/fixtures.json`, fixtures dos clientes e exemplos de contrato continuam
existindo para desenvolvimento/testes. Não devem ser tratados como catálogo editorial
de produção. O prefixo `fixture` em IDs de entidades reais é mantido para preservar
identidade e navegação; `fixture.source.web` é uma atribuição real à tradução WEB,
distinta da fonte editorial de demonstração retirada.

A seleção por sentimento continua sendo uma prévia editorial identificada. Nenhuma
personalização ou produção de áudio foi simulada. Catálogos e aprovações anteriores
não foram reescritos, nem foi fabricada uma nova aprovação editorial humana.

## Verificação técnica local

- iOS: compilação e 306 testes em 68 suítes passaram no simulador.
- Android: APK Debug compilado; 177 testes de Models e Scripture passaram.
- Go: testes de Store PostgreSQL e HTTP API passaram com `-race`, incluindo
  atribuição por idioma em detalhe e contexto.
- Pipeline: 70 testes passaram, incluindo publicação/retirada em schemas descartáveis,
  repetição da migração, preservação de alterações editoriais posteriores e contrato
  Python–Go. Um teste opcional do snapshot STEP completo ficou sem execução.
- Novos testes Python passaram no Ruff; JSON/XML e `git diff --check` válidos.

## Pendências delimitadas

1. Implantar API/migração `0013` e distribuir os builds mobile. Esta rodada foi
   validada localmente; não executou SQL de escrita na produção. O deploy da main
   pode executar a migração pelo pre-deploy existente: guardar backup antes de
   atualizar e conferir contagens/fontes após a implantação.
2. Cache de conteúdo tem validade de uma hora; offline pode continuar exibindo a
   cópia anterior até uma atualização online. Não foram apagados dados do leitor.
3. Revisão humana final de uma amostra delimitada das interpretações e traduções
   existentes. Não houve revisão teológica integral dos 26 mil registros STEP.
   Retomar datas históricas ou geografia moderna exige fonte específica revisada,
   se isso for desejado futuramente. A versão pode seguir sem esses campos.
4. O snapshot STEP externo completo não está configurado nesta execução. Testes
   de parsing, publicação e contrato disponíveis no repositório passaram.
5. Homologação PT-BR/EN, acessibilidade e fluxos em aparelhos reais fica por último.

Pendências operacionais de backup automático, App Check e distribuição continuam
listadas em `RELEASE_CLOSURE_2026-10-21.md`; não foram ampliadas por esta rodada.
