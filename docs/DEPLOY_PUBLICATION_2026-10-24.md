# Deploy e publicação — 24/10/2026

Concluído após o usuário restabelecer o login do Railway e autorizar a implantação
e publicação dos conteúdos. Data local: America/Manaus; os registros do provedor
e dos recibos usam UTC em 25/10/2026.

## Implantação

- Projeto `verbum`, ambiente `production`, serviço existente `api`.
- Commit implantado: `dd7c754f78ea2f88a12cb8b75a5e606f41d778d9`.
- Checks remotos desse commit concluíram com sucesso:
  [Release checks 39654330009](https://github.com/zUnknownUser/Verbum/actions/runs/39654330009).
- Deploy Railway `985bd30a-2770-41ee-b164-6b98a278674a`: `SUCCESS`.
- O pre-deploy existente executou `0001` a `0016`; os logs confirmaram a aplicação
  das migrações pendentes `0013` a `0016` às 00:49:28 UTC de 25/10.
- A API pública respondeu HTTP 200 e `ready` em `/readyz` após a implantação e
  novamente após a publicação. Nenhum serviço, imagem de banco ou volume foi trocado.

## Conteúdo publicado

Publicação pelo pipeline existente, em ordem, com os arquivos de revisão por hash
já registrados. Cada lote concluiu sua transação com status `published` e recibo
em `content_publications`. Nenhuma aprovação editorial nova foi fabricada.

| Lote | Quantidade | Hash |
|---|---:|---|
| Estudos de capítulos do Êxodo | 21 | `23c3928e4f62364f677ad64165216e79c2245d7ecb96dbcbcd866b418ff618f0` |
| Perfis de Pessoas/Lugares | 30 | `800e1c40dafbb8954c7882b7bcde118c01da18ba8ab2aeae078a67620943c9ba` |
| Estudos de Temas | 5 | `9ac5ff9087a1da6532122d9f226b046a282759838a84e368c9d0a041746f0ca2` |

Recibos compactos: `pipeline/sources/chapter-studies/publication-2026-10-24.json`,
`pipeline/sources/entity-profiles/publication-2026-10-24.json` e
`pipeline/sources/theme-studies/publication-2026-10-24.json`. Não contêm credenciais
nem dados pessoais. A conexão foi obtida do Railway e usada somente em memória.

## Verificação de produção

Comparadas 112 respostas HTTP com os catálogos revisados: 42 de capítulos, 60 de
perfis e dez de Temas, cobrindo cada item em inglês e português brasileiro.
Confirmados textos, referências, nomes das conexões e fontes; nenhuma resposta
verificada atribui conteúdo à fonte de demonstração `fixture.source.editorial`.
A consulta ao banco confirmou zero associações ativas dessa fonte em entidades,
detalhes, relações e timeline.

As contagens abaixo permaneceram iguais antes e depois da publicação editorial
(a medição inicial já ocorreu após as migrações):

| Conteúdo | Antes / depois |
|---|---:|
| Entidades | 26.985 |
| Registros STEP | 26.760 |
| Ocorrências | 29.763 |
| Entradas da timeline | 98 |
| Versículos | 31.098 |
| Seleções do versículo diário | 101 |

Os guias passaram de zero para 21 capítulos, 30 perfis e cinco Temas. As relações
passaram de 4.848 para 4.933, com conexões editoriais aditivas e reaproveitamento de
conexão equivalente já existente. Não houve exportação de dados pessoais,
geração de áudio paga ou criação do backup local anteriormente dispensado.

## Pendências

A implantação da API, as migrações e a publicação dos três lotes deixam de ser
pendências dos relatórios de 21/10. Permanecem revisão editorial humana
independente e homologação visual, de acessibilidade e navegação nos aparelhos,
por último. Esta rodada não distribuiu binários iOS/Android nem trabalhou em lojas.
As novas interfaces precisam do build atual dos apps; respostas anteriores podem
permanecer no cache mobile por até uma hora online, ou na cópia offline.

O Railway informou depreciação do formato `railway.json`, com funcionamento até
01/12/2026. A migração de configuração deve ser tratada separadamente, preservando
Dockerfile, pre-deploy, volumes e healthcheck; nenhuma mudança de infraestrutura
adicional foi incluída nesta publicação.
