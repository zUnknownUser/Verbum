# Biblioteca, Jornada e recuperação de áudio — 2026-09-28

Entrega nas duas plataformas, com textos em português brasileiro e inglês.

- Biblioteca reúne passagens salvas, destaques e notas já persistidos pelo leitor, com filtros e busca local por referência/nome do livro/nota. Busca ignora caixa e acentos e aceita nomes de livros nos dois idiomas. Registros removidos não aparecem.
- Jornada mostra dias de leitura, capítulos abertos, continuidade e visitas recentes. A interface explicita que abrir um capítulo não significa concluí-lo.
- Cada aba mantém sua navegação; tocar um item abre o capítulo e o versículo selecionado. Voltar retorna à coleção.
- São dados existentes no aparelho, isolados por conta pelo armazenamento atual. Não foi acrescentada sincronização em nuvem.
- Player distingue preparação, carregamento, buffering, indisponibilidade, limite do serviço e falha de reprodução. Retry explícito preserva posição durante a sessão e só busca essa posição quando o player está pronto.
- Eventos e respostas são associados à sessão: uma resposta de capítulo cancelado não reabre o player nem troca o capítulo atual. Falha não avança o capítulo. Finalização normal continua a sequência canônica.
- Carregamento de mídia ou buffering parado por 45 segundos oferece recuperação. Preparação no backend mantém seus limites atuais. Não há regeneração automática, pré-geração do próximo capítulo ou chamadas pagas nas coleções.
- No iOS, interrupções respeitam a autorização do sistema para retomar; desconectar fones pausa. Ativação/desativação da sessão são serializadas fora da main thread. Android mantém áudio em MediaSession/Media3 com audio focus.

## Validação

- Build do app iOS com Xcode 27 / destino Simulator; compatibilidade mínima iOS 17 mantida.
- 61 testes Swift passaram no iOS Simulator: suíte de Models completa, AudioPlayerFeature, ListenIntegration e ReadingCollectionFeature. Como os esquemas do projeto ainda não têm TestAction, foi usado um projeto XCTest temporário ligado aos produtos locais VerbumKit, sem alterar configuração de distribuição.
- Android assembleDebug passou. 14 testes direcionados passaram (2 domínio de coleções, 3 coleções/navegação, 9 áudio/integração), usando o runtime de Store e relógio virtual para cancelamento/buffering.
- Nenhuma geração TTS paga foi disparada. Avaliação subjetiva da narração fica com o proprietário, conforme solicitado.

Não há retomada persistida de áudio após encerrar o processo; recuperação preserva o ponto dentro da sessão atual. Métricas de leitura representam aberturas, não leitura integral.

## iPhone Duo na matriz de dispositivos

Requisito permanente do proprietário: incluir iPhone Duo nas validações de layout e
navegação, junto aos demais iPhones, iPad e Android, em PT-BR e EN. A lista de livros
agora usa largura real da janela e size class: abaixo de 820 pontos abre em sheet;
com espaço suficiente usa coluna entre 280 e 340 pontos. O leitor permanece no mesmo
store e a sheet é dispensada ao passar para a coluna. Não se presume largura pela
identidade do aparelho ou por `UIScreen.main`.

Build desse ajuste passou no Xcode 27.0 / iOS Simulator 27.0. Este Mac não possui
Xcode 27.1 nem o simulador do Duo, exigidos para a validação específica das poses e
safe areas desse aparelho. Essa validação permanece pendente, sem confundir um build
de iPhone comum com um teste no Duo. Referências oficiais:
[iPhone Duo](https://developer.apple.com/iphone-duo/) e
[adaptação de apps](https://developer.apple.com/videos/play/tech-talks/111461/).
