# Leitura e investigação no Verbum

## Como testar na nova versão do app

1. Configure idioma português ou região Brasil. Abra **Explorar**: pessoas, lugares,
   termos originais e detalhes usam a camada PT-BR. Procure `Golias`, `Moisés` ou `H1732`.
   Hebraico/grego, Strong e identificadores da fonte continuam intactos.
2. Em **Bíblia**, abra **1 Samuel 17**. Toque no número ou texto de um versículo:
   a folha de estudo oferece **Destacar, Nota, Comparar, Contexto, Referências, Perguntar**.
3. Escolha uma cor em **Destacar**; escreva e salve uma **Nota**. Feche e reabra o app:
   a anotação continua no versículo. Notas e destaques são locais, sem sincronização de conta.
4. Toque num nome sublinhado, como **Golias**, quando a fonte reconhecer esse nome no capítulo.
   Leia a ficha e abra as fontes. Nomes ambíguos apresentam candidatos; não há identificação
   automática de qualquer palavra nem alinhamento palavra-a-palavra com Strong.
5. Em **Comparar**, leia a outra tradução. PT: Bíblia Livre / Bíblia Portuguesa Mundial;
   esta segunda é de domínio público, ainda sob revisão, identificado na interface.
   EN: Berean Standard Bible / World English Bible. A comparação PT precisa de conexão.
6. Em **Referências**, abra outra passagem e use **Voltar para…** para recuperar a leitura.
   iOS também reserva a borda esquerda para esse retorno; Android usa o gesto/botão Voltar.
7. Em **Perguntar**, escreva “O que essa expressão significa aqui?”. A pergunta inclui a
   referência selecionada; a resposta mostra evidências bíblicas e fontes. Sua nota não é enviada.
8. No menu **… → Configurações de leitura**, alterne **Páginas / Contínua**. Páginas usa
   swipe horizontal nativo; Contínua acrescenta capítulos durante o scroll. A preferência é salva.
   Use o ícone de leitura discreta para esconder controles, e toque no texto para mostrá-los novamente. Veja também [Áudio acompanhado](AUDIO_READING_SYNC.md).

## Arquitetura e limites de cobertura

- `ReaderStudy` fica em Models; anotação e comparação são Clients injetados. O reducer
  `VerseStudyFeature` é filho de `ChapterReaderFeature`; views não fazem rede/persistência.
- ContextClient, GraphClient, AskFeature e o corpus/RAG existentes são reutilizados.
  Não foi criada uma coleção paralela de entidades nem comentários gerados ao abrir o reader.
- Texto atual e capítulos adjacentes são pré-carregados. Listas contínuas são lazy; metadados
  de contexto/comparação e IA são carregados conforme necessário. Respostas de capítulos têm
  identidade para não substituir a página atual após navegação rápida.
- As referências combinam relações existentes e ocorrências TIPNR verificadas das entidades
  presentes no versículo. Máximo de quatro ocorrências externas por registro, 24 no total.
  São associações por pessoa/lugar; **não afirmam paralelos temáticos, citações ou dependência
  literária**. Cobertura varia; ausência de contexto não é preenchida por texto inventado.
- Perguntas ancoradas aceitam 1–6 versículos. Evidência real no corpus é obrigatória. O corpus
  de evidência é WEB; respostas PT são explicações/paráfrases, não uma tradução bíblica oficial.
- A camada PT-BR cobre os campos de apresentação existentes: 26.782 entidades, 16 eventos,
  cinco citações de fontes. Conteúdos originalmente ausentes continuam ausentes. Traduções
  assistidas por modelo receberam verificações automáticas e correções pontuais, não revisão
  linguística humana individual de todo o acervo. Veja [LOCALIZATION.md](LOCALIZATION.md).
- iOS usa materiais/glass nativos; Android usa a folha Material do app. Não há dependência nova
  de animação, novas tabelas de anotações no servidor nem chamada de IA ao tocar num nome.

## Validação e distribuição

Builds iOS e Android e testes de backend/PostgreSQL foram executados. Testes de parsing,
publicação, localização e contrato HTTP do pipeline também são verificados. Há novos testes
mobile de nomes/Unicode, homônimos, anotações, navegação e retorno; execução desses testes e
aceitação em aparelho permanecem com o proprietário conforme ROADMAP. Build bem-sucedido
não comprova fluidez, acessibilidade ou gestos no aparelho: valide os passos acima, especialmente
texto grande, VoiceOver/TalkBack, scroll/retorno, modo contínuo e rede indisponível.

O deploy Railway atualiza API e migrações, não instala uma versão do app nem publica nas lojas.
A folha de estudo e os gestos exigem instalar o novo build. O APK Debug fica em
`android/app/build/outputs/apk/debug/app-debug.apk`; no iOS, execute o projeto Verbum atualizado
pelo Xcode ou use o processo de distribuição assinado do proprietário.

## Próximo recorte do MVP

Contexto editorial localizado, com interlocutores, cenário histórico/literário, ligações
entre passagens e fontes revisadas, começando por um conjunto pequeno de capítulos.
Notas/destaques locais agora existem; Biblioteca/Jornada, sincronização pessoal e onboarding
continuam sendo etapas posteriores. A nova experiência não resolve esses módulos por si só.
