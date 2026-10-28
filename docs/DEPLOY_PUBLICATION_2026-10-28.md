# Deploy e publicação — 28/10/2026

## Resultado

66 orientações de livros publicadas em produção, com 132 apresentações (EN/PT-BR), pelo pipeline transacional existente. O conteúdo gravado foi comparado integralmente com o catálogo aprovado; 144 respostas HTTP confirmaram textos, referências e fontes nos dois idiomas. A API respondeu HTTP 200 / `ready` depois da publicação.

## Commits e implantação

- `b8a4e96` — `docs: audit biblical catalog and bound release scope`.
- `8c938fd` — `feat: add bilingual orientations for all 66 books`.
- `2975a71e8cf58332536e7bc86cbafede1893a0f4` — `docs: normalize catalog audit line endings`, ponta da main enviada com a implementação.
- Railway: projeto `verbum`, ambiente `production`, serviço existente `api`.
- Deploy `1b315bee-bf17-47eb-97a4-c38edce0ac4f`: `SUCCESS`.
- O pre-deploy `/app/migrate` confirmou a aplicação das migrações `0001` a `0017` em 28/10/2026 às 20:08:58 UTC; `0017_book_orientations.sql` é aditiva.
- Mantidos serviço, banco, imagem do banco e volumes existentes.
- [Release checks do commit implantado](https://github.com/zUnknownUser/Verbum/actions/runs/40760276991): concluídos com sucesso em iOS, Android e backend/conteúdo.

## Autorização e lote

Lucas autorizou explicitamente nesta conversa o commit, push, deploy e a publicação destas 66 orientações. A autorização do lote foi registrada no arquivo `pipeline/review/book-orientations-2026-10-28.json`, vinculada ao hash exato. O registro não afirma que houve revisão editorial humana independente de cada texto. Os testes anteriores e os checks desta publicação não foram apresentados como aprovação humana.

Hash: `e75c823b9dddf85481c0da3efe02594ff55ea638047f3c6600ecb54f09bf1756`.

Recibo: `pipeline/sources/book-orientations/publication-2026-10-28.json`. A conexão com o banco foi obtida do Railway e usada apenas em memória; o recibo contém contagens e metadados de catálogo, sem credenciais ou dados pessoais.

## Verificações

- 66 livros e 132 apresentações armazenados exatamente como aprovados.
- 132 associações de fontes (fonte editorial e livro bíblico para cada orientação).
- 144 respostas de contexto conferidas, cobrindo todos os livros em inglês e português, além de capítulos como Salmos 91, Isaías 53 e finais de livros.
- Estudo de Êxodo 14 continua presente junto da orientação do livro, nos dois idiomas.
- Contagens do catálogo anterior preservadas:

| Tabela | Antes | Depois |
|---|---:|---:|
| entities | 26,985 | 26,985 |
| entity_source_records | 26,760 | 26,760 |
| entity_occurrences | 29,763 | 29,763 |
| timeline_events | 98 | 98 |
| scripture_verses | 31,098 | 31,098 |
| daily_verse_pool | 101 | 101 |
| chapter_studies | 21 | 21 |
| entity_profiles | 30 | 30 |
| theme_studies | 5 | 5 |
| relationships | 4,933 | 4,933 |
| book_orientations | 0 | 66 |
| book_orientation_sources | 0 | 132 |

## Pendências e disponibilidade nos apps

Deploy da API, migração e publicação concluídos. A exibição das orientações requer o build atualizado de iOS/Android enviado neste push; não foram distribuídos binários móveis nem realizadas ações de loja. Respostas já guardadas podem permanecer no cache online por até uma hora ou na cópia offline.

Permanecem a revisão editorial humana independente e a homologação em dispositivos, esta por último conforme combinado. Os 12 estudos de capítulos e os 12 perfis do escopo fechado são etapas posteriores, não fazem parte desta publicação. Não foi gerado áudio pago nem criado backup completo dos usuários.
