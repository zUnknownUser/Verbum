# Pessoas e Lugares do recorte do Êxodo — 21/10/2026

Atualização em 24/10: API, migrações e este lote foram publicados em produção.
Veja [o registro de implantação e verificação](DEPLOY_PUBLICATION_2026-10-24.md).
O relato abaixo preserva o estado da entrega original.

## Entrega delimitada

30 fichas existentes aprofundadas em inglês e português brasileiro: primeiro 15
pessoas, depois 15 lugares ligados aos 21 capítulos dos estudos do Êxodo. Cada
ficha tem função, dois ou três parágrafos, passagens de apoio e fontes. São 94
referências de leitura nas fichas (com repetições entre fichas), 71 conexões
propostas no grafo e 24 links de capítulos bíblicos, além da atribuição editorial.
Conexões equivalentes já existentes são mantidas, sem criar uma aresta paralela.

Pessoas: Moisés, Arão, Miriã, Jetro, Zípora, Gérson e Eliézer filhos de Moisés,
Josué filho de Num, Calebe filho de Jefoné, Hur companheiro de Moisés, Balaão,
Balaque, faraó da infância/fuga de Moisés, faraó do êxodo e a filha do faraó que
resgatou Moisés.

Lugares: Egito, Midiã, Sinai/Horebe, mar Vermelho, Mara, Elim, deserto de Sim,
Refidim, deserto de Parã, Cades, Canaã, Moabe, monte Nebo, Hesbom e Basã.

É uma seleção principal para esse percurso, não uma enciclopédia de cada nome
incidental. Não foram abertas novas fichas de todos os exploradores, antepassados,
reis ou paradas mencionados nos capítulos. As contagens de temas, eventos,
timeline, capítulos estudados e identidades canônicas permanecem as anteriores.
Passagens auxiliares de identificação, como Números 26:59 e Êxodo 24:14, não
criam etapas novas na sequência.

## Conteúdo, fontes e identidade

- Textos originais em `pipeline/sources/entity-profiles/people-notes.json` e
  `places-notes.json`; cada parágrafo guarda suas referências. `links.json` guarda
  as referências específicas das relações. `exodus.json` é o Bundle v9 gerado.
- Referências verificadas no corpus WEB versionado. Os 24 links de capítulos da
  [World English Bible](https://ebible.org/engwebp/) responderam HTTP 200 a GET.
  A edição declara [domínio público](https://ebible.org/engwebp/copyright.htm).
  As leituras internas seguem a tradução escolhida no aplicativo; os links
  bibliográficos identificam a edição inglesa consultada.
- `identities.json` registra o vínculo com os registros já importados do
  [STEP Bible / Tyndale House](https://github.com/STEPBible/STEPBible-Data), TIPNR,
  revisão `a51237d7a5f2dd2e0f26ccc7156b92ad79703ba9`, CC BY 4.0.
  IDs, tipo, registro, identificador externo e nome individualizado são conferidos
  antes de publicar. Os registros, licenças e atribuições originais permanecem.
- Não se misturam os dois faraós, os homônimos de Josué/Calebe/Hur, o monte Nebo
  e as cidades chamadas Nebo. O vínculo já existente Sinai/Horebe e a variação
  Jetro/Reuel são explicados sem inventar uma solução histórica.
- Nenhuma cronologia absoluta, coordenada ou identificação arqueológica foi
  acrescentada. As fichas distinguem o cenário narrativo do que os textos não
  estabelecem sobre localização atual. A ficha editorial não apresenta antigas
  datas/geografias de demonstração como fatos atuais.
- Revisão delegada de IA registrada explicitamente em
  `pipeline/review/entity-profiles-2026-10-21.json`, hash
  `800e1c40dafbb8954c7882b7bcde118c01da18ba8ab2aeae078a67620943c9ba`.
  Não representa revisão humana, teológica ou acadêmica independente.

## Arquitetura e integração nativa

A migração `0015_entity_profiles.sql` acrescenta uma apresentação editorial às
identidades existentes, com tabelas próprias para suas passagens e fontes. A
publicação usa o pipeline já existente, validação/revisão por hash, transação,
bloqueio entre publicadores e recibos idempotentes. Atualizações de fichas exigem
`previousHash` correspondente ao conteúdo efetivamente armazenado, incluindo
passagens e fontes; alterações posteriores não são sobrescritas silenciosamente.
As novas relações são aditivas e não substituem relações ou evidências anteriores.

O backend apresenta a edição no idioma exato solicitado. Sem uma tradução dessa
edição, mantém o comportamento e conteúdo anteriores daquele idioma. Não empresta
o texto inglês à interface portuguesa. As passagens anteriores permanecem
acessíveis; duplicatas exatas são apresentadas uma vez. O Contexto encontra as
fichas pelas associações; detalhes e respostas de evidência incluem suas fontes.
Busca global e catálogo reconhecem os novos nomes e aliases, preservando a busca
pelos aliases anteriores.

**O contrato público de EntityDetail e as telas de produção do iOS/Android não
precisaram mudar.** Ambas já oferecem resumo, função, leituras, conexões e fontes.
A entrega reutiliza seus clientes, modelos e fluxo de navegação existentes, com
novos testes de contrato compartilhados em `api/examples/entities/aaron.*.json`
e `nebo.*.json`. Não há rede em Views, geração ao abrir a ficha, mudança de design
system nem nova dependência mobile.

## Verificação

- iOS: suíte no simulador passou com 309 testes em 68 suítes, incluindo os novos
  exemplos em ambos os idiomas.
- Android: 96 testes de Clients e 136 de Scripture, sem falhas; APK Debug compilado.
- Go: `go test -race ./...` e `go vet ./...` passaram, incluindo idioma ausente,
  busca e fontes. As migrações são aplicadas duas vezes nos schemas de teste.
- Pipeline: 90 testes passaram; um teste opcional do snapshot externo STEP ficou
  sem executar. A publicação em banco descartável foi exercitada via API Go para
  todas as 30 fichas nos dois idiomas: conteúdo, leituras, fontes, contexto e grafo.
- Verificadas idempotência, rollback integral, proteção de revisão, identidade
  ambígua, ausência de tradução, preservação de registros e relações anteriores,
  busca por aliases e compatibilidade dos hashes dos catálogos anteriores.
- Os exemplos mobile foram comparados com respostas HTTP reais no teste local;
  não são conteúdo mock inserido no aplicativo.
- Ruff dos arquivos Python novos, diff e links das fontes verificados.

## Publicação e pendências

Nenhuma alteração foi aplicada ao banco de produção nesta rodada. O trabalho foi
feito enquanto o usuário relatava o incidente de builds/deploys do Railway; esta
entrega não presume que o serviço já tenha se recuperado. Não houve novo backup,
geração de áudio paga ou mudança de loja/distribuição.

Quando a infraestrutura estiver disponível, implantar a API e suas migrações,
incluindo `0013`, `0014` e `0015`, e publicar explicitamente os dois lotes
editoriais pendentes. O push de código não publica os catálogos por si só.
De `pipeline/`, com a conexão de produção fornecida pelo ambiente seguro:

```sh
.venv/bin/verbum-pipeline validate sources/chapter-studies/exodus.json \
  --decisions review/chapter-studies-2026-10-21.json
.venv/bin/verbum-pipeline publish sources/chapter-studies/exodus.json \
  --decisions review/chapter-studies-2026-10-21.json
.venv/bin/verbum-pipeline validate sources/entity-profiles/exodus.json \
  --decisions review/entity-profiles-2026-10-21.json
.venv/bin/verbum-pipeline publish sources/entity-profiles/exodus.json \
  --decisions review/entity-profiles-2026-10-21.json
```

O lote de fichas requer os registros STEP e os eventos canônicos já existentes.
Se algum vínculo não corresponder, a publicação para antes de escrever; não se
cria um substituto por nome. Alterações editoriais posteriores exigem nova revisão.
Após a publicação, conferir conteúdo EN/PT-BR e fontes na API. O cache mobile
existente pode manter a resposta anterior por até uma hora online; offline,
preserva a última cópia disponível.

Pendências desta entrega: implantação/publicação e conferência em produção;
revisão humana independente de interpretações e traduções; homologação ampla em
aparelhos reais por último. A expansão para outros personagens, lugares e períodos
não faz parte deste lote e não é requisito para encerrá-lo.
