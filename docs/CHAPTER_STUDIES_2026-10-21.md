# Contexto editorial do Êxodo — 21/10/2026

## Recorte entregue

21 estudos de capítulos, em inglês e português brasileiro, sobre as leituras da
sequência existente do Êxodo: Êxodo 2, 3, 7–10, 12, 14–20, 32 e 40;
Números 13, 14, 21 e 22; Deuteronômio 34. João 3 é uma conexão posterior,
não uma etapa adicional. Permanecem 80 temas, 137 eventos, 98 entradas da timeline
e 16 eventos da sequência. Nenhuma ficha de pessoa/lugar foi reescrita nesta rodada.

Cada capítulo tem título, resumo, participantes/funções, cenário narrativo,
contexto literário, estrutura e duas conexões explicadas. São 70 seções que cobrem
todos os versículos dos capítulos, sem sobreposição, e 42 conexões. Cada parágrafo
editorial indica as passagens em que se baseia. As conexões distinguem continuação,
releitura posterior e comparação; não são todas apresentadas como relatos paralelos.

## Fontes e idiomas

As notas são paráfrases editoriais originais. A evidência bíblica foi conferida
contra o corpus WEB versionado e é identificada em 48 links de capítulos da
[World English Bible](https://ebible.org/engwebp/), cuja edição declara
[domínio público](https://ebible.org/engwebp/copyright.htm). Os 48 links responderam
HTTP 200 a GET. As referências internas abrem na tradução escolhida pelo leitor;
os links bibliográficos identificam a edição usada como base, em inglês.

O cenário descreve o relato bíblico. Não acrescenta uma cronologia absoluta,
identificação arqueológica ou consenso histórico sem fonte específica. Nomes
ambíguos e tensões narrativas não são resolvidos por inferência silenciosa.

Autoria em `pipeline/sources/chapter-studies/exodus-notes.json`; catálogo gerado
em `exodus.json`. `pipeline/review/chapter-studies-2026-10-21.json` vincula a revisão
ao hash `23c3928e4f62364f677ad64165216e79c2245d7ecb96dbcbcd866b418ff618f0`.
O registro identifica Codex como revisor editorial de IA sob delegação do usuário;
não afirma revisão humana, teológica ou acadêmica independente.

## Arquitetura e experiência

- Bundle v8 reutiliza validação, revisão por hash, publicação transacional,
  bloqueio entre publicadores e recibos existentes. Versões/hashes anteriores
  permanecem válidos. Revisões de estudos existentes exigem `previousHash`;
  um conteúdo alterado depois da revisão não é sobrescrito silenciosamente.
- Migração `0014_chapter_studies.sql` cria duas tabelas de estudos/fontes;
  não altera entidades, relações, timeline ou dados pessoais.
- `GET /v1/passages/{Book.Chapter}/context` acrescenta `study` opcional. O Store
  escolhe exatamente o idioma solicitado e agrega apenas fontes usadas. Estudos
  funcionam mesmo sem entidades; ausência de estudo preserva o contexto existente.
- iOS mantém App → Features → Clients → Models e TCA. Android mantém modelos
  puros, conversão de wire nos Clients e fluxo UDF. Views não buscam nem publicam dados.
- O mesmo componente de apresentação é reutilizado no Contexto e na folha de
  estudo de cada plataforma. Resumo visível; participantes, cenário, contexto
  literário, estrutura, conexões e fontes em seções expansíveis. Tokens, cores,
  tipografia e navegação existentes foram preservados.
- Na folha de estudo, referências abrem na prévia existente e o retorno preserva
  estudo, seleção e anotações. A aba Referências inclui as conexões editoriais antes
  das associações existentes, sem duplicatas. O Contexto completo usa sua navegação
  atual para o leitor. Respostas antigas da API continuam decodificando.
- Não há chamada de geração ao abrir o estudo, pré-geração de áudio, nova
  dependência mobile ou alteração das configurações de produção.

## Verificação

- iOS: build do app e 308 testes em 68 suítes passaram no simulador.
- Android: 95 testes de Clients e 136 de Scripture passaram; APK Debug compilado.
  Um cache de build ainda apontava para a antiga pasta do projeto; recompilação
  limpa resolveu o empacotamento, sem alterar SDKs ou dependências.
- Go: Store/PostgreSQL e HTTP API com `-race`, teste adicional de idioma ausente
  e `go vet` passaram. Migrações aplicadas duas vezes em schemas descartáveis.
- Pipeline: 80 testes passaram; um teste opcional do snapshot STEP externo não
  executou. Inclui todos os 21 estudos publicados em banco descartável e servidos
  pela API Go nos dois idiomas, proteção de revisão, rollback e idempotência.
- Exemplos de contrato compartilhados são consumidos pelos testes iOS e Android.
  Referências, cobertura das estruturas e igualdade de navegação entre idiomas
  são verificadas automaticamente; isso não substitui revisão interpretativa humana.
- OpenAPI validado com Swagger Parser 12.0.0; JSON/XML, novos arquivos Python e
  diff verificados. Homologação ampla em aparelhos
  reais permanece para a última etapa.

## Publicação e pendências

O conteúdo foi publicado apenas em bancos descartáveis de teste. Em 21/10, a
[página do Railway](https://status.railway.com/) ainda informava suspensão de builds
e deploys, inclusive na atualização das 16:29 UTC. Não houve tentativa de contornar
essa suspensão ou de criar um novo backup de produção.

Quando a implantação estiver disponível:

1. Implantar a API com as migrações existentes, incluindo `0014`.
2. Publicar explicitamente o catálogo pelo pipeline existente; o push do código
   não publica automaticamente o lote editorial.
3. Conferir os 21 capítulos em EN/PT-BR na API e usar builds mobile atualizados.
   O cache existente pode manter uma resposta anterior por até uma hora online;
   offline, continua valendo a cópia já disponível.

Após a migração, de `pipeline/`, com a conexão fornecida pelo ambiente seguro:

```sh
.venv/bin/python -m verbum_pipeline.chapter_studies
.venv/bin/verbum-pipeline validate sources/chapter-studies/exodus.json \
  --decisions review/chapter-studies-2026-10-21.json
.venv/bin/verbum-pipeline publish sources/chapter-studies/exodus.json \
  --decisions review/chapter-studies-2026-10-21.json
```

Permanecem revisão humana independente de conteúdo/tradução, homologação final
e a próxima rodada delimitada de fichas de pessoas/lugares desse mesmo recorte.
Outros capítulos, períodos e ampliação de catálogo não foram iniciados. Lojas e
distribuição comercial ficam fora desta prioridade, conforme orientação do usuário.

## Complemento posterior nesta data

As 30 fichas principais de pessoas e lugares desse percurso foram aprofundadas
na [rodada de fichas](ENTITY_PROFILES_2026-10-21.md), sem alterar os 21 estudos
deste documento. Ambos os lotes aguardam publicação explícita em produção.
