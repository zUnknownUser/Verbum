# Busca bíblica e respostas com fontes — 2026-09-28

## Diagnóstico

A base de produção contém 31.098 versículos WEB, 66 livros e 31.098 embeddings.
Jó 38:1–4 já estava presente. Não foi necessário reimportar a Bíblia, trocar a
tradução ou alterar tabelas para resolver a recuperação de contexto.

A recuperação anterior aplicava uma consulta lexical inglesa com todas as palavras
da pergunta, misturava pontuação lexical e similaridade vetorial em escalas distintas,
e entregava seis versículos isolados ao modelo. Isso fragilizava perguntas em português,
especialmente sem embedding, e podia omitir a abertura ou o desfecho de uma cena.

A recusa original de “Önde Deus conversa com Jó?” **não foi reproduzida consistentemente**:
em testes novos, o modelo anterior também respondeu à pergunta. Portanto, o incidente
não prova ausência de Jó na base nem permite atribuir uma causa única. As correções
tratam fragilidades observadas da recuperação e evitam reutilizar respostas da versão antiga.

## Implementação

- Normalização de acentos/caixa e vocabulário PT-BR/EN determinístico. Termos desconhecidos
  continuam participando; não há lista fechada de perguntas ou respostas prontas por versículo.
- Referências explícitas dos 66 livros em português e inglês são verificadas na base e
  dispensam embedding. Jó e João não são confundidos.
- Ranking por reciprocal rank fusion entre canais lexical, semântico e editorial.
  Cobertura lexical considera frequência dos conceitos e normalização por comprimento;
  palavras raras podem recuperar um trecho mesmo sem corresponder a toda a pergunta.
- Ask busca até 24 candidatos no fluxo padrão, escolhe seis pontos de partida variados
  e carrega vizinhos no mesmo capítulo. Limites: 48 versículos e 20.000 bytes de texto;
  todos os trechos e índices citados precisam existir na base.
- Instruções de resposta consideram linguagem espontânea, locutor, sequência narrativa
  e a diferença entre início e continuação. Português usa paráfrase do corpus WEB;
  não se apresenta como citação literal de outra tradução.
- Sem resposta fundamentada, o texto continua vazio e pode oferecer passagens próximas,
  identificadas pelos apps como resultados de busca, sem apresentá-las como prova da pergunta.
- Cache privado de respostas inclui `rag-context-2`. O cache global de áudio permanece
  independente. Não há tradução dinâmica da pergunta nem segunda chamada de IA para reranking.
- Os limites, reservas e registro de custos existentes continuam ativos. Até uma geração
  de resposta e um embedding por pergunta; referências exatas não geram embedding.

## Avaliação reproduzível

`backend/internal/retrieval/testdata/questions.json` contém 20 perguntas com passagem
esperada e duas alegações sem suporte (“Jesus usava um smartphone?”), nos dois idiomas.
É um conjunto pequeno de regressão escolhido durante o desenvolvimento, não uma avaliação
cega nem uma estimativa de acurácia para toda pergunta possível.

Consultas executadas contra o corpus completo de produção, com conexões **somente leitura**,
mesmos vetores de pergunta e limite de seis resultados antes da expansão:

| Recuperação | Antes: trecho esperado nos 6 primeiros | Agora: nos 6 primeiros | Agora: no contexto entregue |
|---|---:|---:|---:|
| Lexical, sem embedding | 1/20 | 15/20 | 15/20 |
| Híbrida | 19/20 | 19/20 | 20/20 |

“Acerto” significa que pelo menos um versículo está dentro de uma das faixas esperadas.
Não significa que a resposta inteira foi julgada correta. A recuperação lexical ainda
falha em algumas paráfrases; o canal semântico continua relevante.

Sete sínteses reais revisadas: Jó (pergunta original PT-BR, início do discurso PT-BR,
pergunta EN), Lázaro PT-BR/EN e as duas alegações sem suporte. As cinco perguntas bíblicas
receberam respostas com fontes; as duas alegações foram recusadas sem resposta inventada.
A pergunta sobre o início do discurso retornou Jó 38:1–5, distinguindo Jó 40:6 como continuação.
Lázaro em português retornou João 11:43–44. Nenhum teste gerou áudio TTS.

Para repetir, a partir de `backend`, com DATABASE_URL configurada com segurança:

```sh
go run ./cmd/rag-eval                         # só SQL; nenhuma chamada paga
go run ./cmd/rag-eval -vectors /path/vectors.json
# Chamadas pagas apenas por opção explícita; até 50 casos, sem retries automáticos:
go run ./cmd/rag-eval -live-embeddings -answers -only 'Jó' -limit 2
```

O avaliador força `default_transaction_read_only=on`, limita tempo e volume de consultas
e registra top 6, candidatos e cobertura do contexto. Os vetores e credenciais não são
versionados. Os testes de integração usam esquemas isolados em banco local descartável,
nunca o banco de produção. `go test -race ./...` e `go vet ./...` passaram.

Referências de método: [OpenAI retrieval](https://developers.openai.com/api/docs/guides/retrieval)
e [avaliação da precisão](https://developers.openai.com/api/docs/guides/optimizing-llm-accuracy).
