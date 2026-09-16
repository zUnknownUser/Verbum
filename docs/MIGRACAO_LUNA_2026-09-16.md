# Ask — GPT-5.6 Luna

Solicitação: substituir a chave OpenAI do backend e migrar Ask de `gpt-4o-mini` para `gpt-5.6-luna`. A nova chave foi escrita diretamente na variável `OPENAI_API_KEY` do serviço API no Railway, sem arquivo no repositório. A chave anterior não foi revogada na OpenAI; revogação/rotação futura permanece com o proprietário.

`VERBUM_ASK_MODEL=gpt-5.6-luna`. Mantidos Chat Completions, contrato JSON, validação de evidências/citações, fallback indexado, 32.000 bytes de prompt, 1.024 tokens de saída, timeout de 30 segundos e reserva de US$ 0,01 por Ask. `reasoning_effort=none` é explícito para preservar o comportamento de latência e orçamento da integração anterior. Sem parâmetro temperature para Luna. Identificador de segurança derivado por hash do UID. A mudança de modelo já integra a chave do cache de Ask.

A contabilização usa os preços de Luna e os detalhes de cache quando presentes; sem detalhes, considera o preço superior de escrita de cache para toda a entrada. `gpt-4o-mini` continua como rollback explícito e tarifado; nenhum modelo arbitrário é liberado sem custo conhecido.

Embeddings `text-embedding-3-large`, realtime `gpt-realtime` e pipeline de extração não mudaram de modelo. O TTS usa credencial e provedor Google independentes. A substituição da variável OpenAI se aplica aos clientes OpenAI do servidor, inclusive embeddings e realtime.

Validação: GET de metadados dos três modelos retornou 200 com a nova chave. Uma chamada curta real de texto a Luna, limitada a 128 tokens de saída, retornou 200, JSON com answer/citations e finish_reason=stop: 52 tokens de entrada e 25 de saída (US$ 0,0000404 pelas tarifas nominais). Nenhuma chamada real de áudio. Esse smoke test verifica acesso e contrato, não substitui avaliação ampla da qualidade das respostas. Testes Go com race detector, integração PostgreSQL/pgvector descartável e go vet passaram.

Documentação oficial consultada via skill OpenAI Docs:
- https://developers.openai.com/api/docs/models/gpt-5.6-luna
- https://developers.openai.com/api/docs/guides/upgrading-to-gpt-5p6-sol
