# Auditoria do catálogo — 28/10/2026

## Decisão proposta

Lançar uma ferramenta de leitura e estudo com orientação em todos os 66 livros do cânon já suportado e aprofundamentos selecionados. Não prometer comentário de cada capítulo ou verbete enciclopédico para cada nome.

Esta auditoria não criou conteúdo editorial, alterou aplicativos, publicou dados ou fez deploy. O plano abaixo é uma proposta de escopo fechado, ainda não implementada.

## Evidências e limites

Censo das tabelas de catálogo em produção, somente leitura, mais inspeção dos modelos, API e telas de contexto/seleção de livros dos dois aplicativos. Data e commit estão em evidence.json; consultas exatas em queries.sql. coverage.csv lista os 66 livros, capítulos com conexões, estudos e lacunas. A amostra HTTP contém 20 consultas públicas em português e inglês, não uma homologação dos aplicativos.

O denominador é o cânone de 66 livros/1.189 capítulos do produto atual. Não se propõe ampliar o cânone nesta entrega. O banco consultado contém texto em todos esses capítulos; isso não comprova disponibilidade de todas as traduções dos provedores no leitor.

Conexão significa ao menos uma entidade associada ao capítulo ou vizinha direta do nó de passagem. Não significa explicação literária, histórica ou comentário. Resumo ausente não significa registro inútil: identificadores, fontes e ocorrências podem continuar presentes. Contagem de palavras não mede correção ou qualidade teológica.

## Situação encontrada

| Grupo | Capítulos | Com conexões | Com estudo guiado |
|---|---:|---:|---:|
| Lei | 187 | 185 | 21 |
| Históricos | 249 | 249 | 0 |
| Poesia e sabedoria | 243 | 187 | 0 |
| Profetas | 250 | 245 | 0 |
| Evangelhos e Atos | 117 | 117 | 0 |
| Cartas paulinas | 87 | 86 | 0 |
| Cartas gerais | 34 | 33 | 0 |
| Apocalipse | 22 | 17 | 0 |

- AT: 866/929 capítulos com conexões. NT: 253/260. Total: 1.119/1.189.
- Os 70 capítulos sem conexões concentram-se em poesia/sabedoria (56). Salmos 91, Isaías 53 e Hebreus 1 retornaram `content_unavailable` nos dois idiomas. É ausência de catálogo, não comprovação de indisponibilidade do serviço. As telas já tratam indisponibilidade; falta uma alternativa de estudo útil.
- 21 estudos de capítulo: 16 em Êxodo, quatro em Números e um em Deuteronômio; todos em EN/PT-BR. Nenhum estudo de capítulo no NT. Isso não significa ausência total de estudo do NT: os estudos temáticos já incluem passagens do NT.
- 3.041 pessoas e 1.003 lugares. Só 27 pessoas e 24 lugares têm resumo efetivo; 30 perfis aprofundados (15 de cada tipo), concentrados no recorte Moisés/êxodo/deserto. O restante funciona predominantemente como índice de identificação e ocorrências. Jesus, por exemplo, tem o resumo “Figura central do Novo Testamento”.
- 80 temas com resumo e referências, dos quais cinco têm estudo aprofundado: Fé, Aliança, Adoração, Obediência e Santidade. Não há razão para aumentar o número de temas nesta versão.
- 137 eventos em dez eras, incluindo 23 na era de Jesus e 12 na igreja; 98 entradas na linha do tempo. A cobertura histórica já ultrapassa Êxodo. Há uma sequência estruturada de 16 eventos, apenas no êxodo. Aprofundar os 137 eventos não é requisito proposto.
- 22.716 termos originais compõem grande parte do volume total. São dados lexicais, não 22.716 estudos de passagens. Nenhum aparece como associação de capítulo na consulta realizada; isto não implica ausência de busca por identificadores.
- Nos campos auditados, não houve nomes “Nome em tradução”; os estudos estruturados existem nos dois idiomas. Todos os registros não-passagem têm fonte associada ou registro de fonte, e todas as relações têm atribuição. A consulta da antiga fonte `fixture.source.editorial` encontrou zero associações de entidades. IDs com prefixo `fixture` ainda identificam registros reais: o prefixo sozinho não prova mock.
- Fonte cadastrada não comprova exatidão de toda afirmação, validade atual de cada URL ou qualidade integral da tradução. Não foi feita revisão editorial individual de 26 mil registros.
- Não foi encontrada introdução editorial por livro nos modelos e no fluxo inspecionado: a estante organiza livros e capítulos, mas não oferece essa orientação.

## Lista fechada proposta para lançamento

### 1. Orientação básica nos 66 livros

Uma ficha curta por livro, em PT-BR/EN: gênero, contexto com incertezas explícitas, propósito, estrutura em poucas partes, até três passagens de entrada e fontes. Sem comentário de todos os capítulos. Integrar na navegação atual, mantendo a arquitetura e identidade. É a única ampliação de modelo de conteúdo proposta.

Aceite: todos os 66 livros têm ficha acessível; referências abrem no leitor; nenhuma ficha depende de resposta gerada ao vivo. Os capítulos sem estudo próprio podem oferecer a orientação do livro, identificada claramente como tal.

### 2. Exatamente 12 estudos de capítulo adicionais

AT (4): Jó 1, Salmos 23, Provérbios 3 e Isaías 53.
NT (8): Mateus 5, Lucas 15, Marcos 15, Lucas 24, Atos 2, Romanos 8, Tiago 2 e Apocalipse 21.

Usar o modelo existente: contexto, observações do texto, questões e leituras relacionadas com explicação e fontes. Aproveitar conexões dos cinco temas existentes, sem copiar seus textos. Total final: 33 capítulos guiados, incluindo oito no NT. Seleção de produto para representar gêneros e assuntos; não é uma afirmação de que todos os outros capítulos são menos importantes.

### 3. Exatamente 12 perfis centrais aprofundados

Pessoas (6): Abraão, Davi, Jesus, Maria (mãe de Jesus), Pedro e Paulo.
Lugares (6): Jerusalém, Babilônia, Belém, Nazaré, Galileia e Roma.

Aprofundar registros existentes no modelo de perfil atual, preservando IDs. Contexto e papel nos textos, passagens comentadas, conexões justificadas e fontes. Total final: 42 perfis aprofundados. Os outros verbetes permanecem disponíveis como índice, sem exigir uma biografia para cada nome.

### 4. Tornar o nível de conteúdo visível e encontrável

Nos componentes existentes de iOS/Android, distinguir estudo guiado, verbete de consulta e referências relacionadas. Dar acesso direto ao conjunto fechado de estudos e perfis, sem criar novo módulo. Nos 70 capítulos sem contexto associado, apresentar orientação do livro e retorno à leitura, sem simular comentário de capítulo nem relacionar textos automaticamente só para preencher a tela.

Aceite: é possível encontrar os estudos completos sem percorrer milhares de verbetes; falta de comentário não vira uma jornada sem saída. Ajustar a promessa do app à cobertura real.

### 5. Fechar a qualidade do conjunto publicado

Para as fichas, novos estudos/perfis e os estudos estruturados existentes: PT-BR e EN completos, referências resolvíveis, atribuição por afirmação quando necessária, fontes acessíveis, ausência de duplicação editorial e distinção entre evidência textual, interpretação e hipótese histórica. Validar integridade de IDs, relações e idiomas no restante do catálogo por verificações automáticas.

Não transformar isto em revisão manual de 22.716 termos ou reescrita de 4.044 pessoas/lugares. Corrigir defeitos encontrados nos fluxos essenciais; expansão editorial fora da lista vai para depois.

### 6. Homologação por último

Depois de conteúdo e integração: iOS/Android, PT-BR/EN, leitura → contexto → entidade/tema → passagem → retorno; descoberta; erros/rede; referências e estados sem estudo; preservação de progresso. Defeitos que bloqueiem leitura ou navegação são impeditivos. Esta auditoria não certifica estabilidade em dispositivos.

## Limite de escopo e regra de término

Conteúdo novo proposto: 66 fichas curtas + 12 estudos de capítulo + 12 aprofundamentos de perfis. São 90 unidades editoriais em duas versões linguísticas, não 90 comentários extensos. Reaproveitar integralmente os 80 temas, 137 eventos e 98 entradas de linha do tempo, corrigindo erros sem ampliar quantidades. Não criar novas sequências como requisito desta versão.

A versão pode encerrar quando os seis itens passarem nos respectivos aceites. Toda nova sugestão de conteúdo vai para pós-lançamento ou substitui explicitamente um item da seleção; nunca é adicionada silenciosamente. Ter mais conteúdo possível não reabre a entrega.

Ficam fora: comentar os 1.189 capítulos; aprofundar todas as pessoas, lugares, temas e eventos; novos módulos; ampliar o cânone; novos idiomas além de PT-BR/EN; geração paga de áudio; requisitos de loja.

## Próxima tarefa recomendada

Fechar e implementar a ficha de orientação por livro, com os 66 livros como lote finito. Depois executar apenas as listas de 12 capítulos e 12 perfis acima, fazer os ajustes de descoberta/estados vazios e concluir a homologação por último. Não retomar os cinco eventos de Êxodo antes deste fechamento.
