# Consistência de português brasileiro — 16/09/2026

## Regra

Região Brasil **ou** idioma português resolve para PT-BR no iOS e Android. Isso usa a configuração do aparelho; não comprova residência nem utiliza GPS. Aparelho configurado inteiramente para outro país/idioma não permite deduzir que o usuário mora no Brasil. O backend também normaliza tags como `en-BR` e `es-BR` para PT-BR. O idioma explícito enviado pelo app continua sendo a referência das requisições.

## Correções

- A abertura do iOS usa a mesma decisão central dos demais recursos, inclusive quando o aparelho está em inglês com região Brasil.
- Controles SwiftUI recebem locale de apresentação; datas do perfil, histórico e limites usam explicitamente o locale resolvido, evitando nomes de meses em inglês no Brasil.
- Clientes de gravações iOS/Android deixam de tentar BSB quando a tradução solicitada é portuguesa. Respostas de áudio com ID de tradução divergente são rejeitadas.
- Clientes de leitura validam a tradução recebida antes de exibir ou salvar o capítulo. Também ignoram capítulos em cache cujo ID de tradução diverge do solicitado.
- PostgreSQL deixa de completar nomes, resumos, aliases e detalhes editoriais portugueses com inglês. Nome sem tradução recebe “Nome em tradução”; campos opcionais ficam ausentes. Eventos sem título traduzido não aparecem na linha do tempo portuguesa. Os dados ingleses continuam disponíveis para inglês.
- Hebraico/grego de estudo e referências bibliográficas preservam os originais e a atribuição das fontes. A política não inventa traduções nem gera custos de tradução automática.
- Cache de respostas editoriais dos apps passa de `localized-v2` para `localized-v3`, invalidando respostas antigas que poderiam conter fallback inglês. Cache de áudio não é apagado.

## Verificações

- Catálogos Android: todas as chaves traduzíveis dos módulos app/audio/scripture possuem equivalente PT-BR.
- Catálogos iOS Account e Localizable: textos funcionais possuem PT-BR; exceções encontradas são pontuação, formatos sem prosa, marca e títulos de edições bíblicas.
- Mapeamento dos 66 livros e seleção Bíblia Livre para português já existentes.
- Ask já exige resposta e resumo em PT-BR independentemente da língua da pergunta/evidência; companheiro de voz já recebe idioma resolvido. Instrução de modelo não equivale a garantia matemática sobre toda saída futura.
- Testes Android de modelos e clientes HelloAO/áudio passaram, incluindo região Brasil com inglês/espanhol e rejeição de resposta inglesa para solicitação portuguesa. APK debug compilou.
- Build iOS Simulator passou. Testes Swift de regressão adicionados; não executados nesta rodada, pois os schemes do projeto não possuem ação de testes configurada.
- `go test -race ./internal/store/... ./internal/httpapi ./internal/ask` passou com PostgreSQL descartável/pgvector. Inclui ausência de traduções e preservação do conteúdo inglês para consumidores ingleses. `go vet` desses pacotes passou.
- Nenhuma síntese de áudio real foi feita. A escuta fica com Lucas.

## Entrega e limites

As correções móveis exigem instalar uma nova versão; compilar e enviar ao Git não publica nas lojas. Conteúdo editorial sem tradução ainda precisa de tradução revisada; agora não será silenciosamente substituído por inglês. Requisições antigas podem conservar o cache editorial anterior até sua atualização; os novos apps usam namespace novo.

O narrador de produção permanece Chirp 3 em PT-BR. Esta alteração não reativa Gemini nem altera orçamento ou credenciais.

### Recibo de produção

- Commit de implementação: `1afbbb479b8fb2d7ea6b2b2017687cd6ba31b4d6`, enviado ao `main`.
- Railway: deployment `f7743ddb-1e64-469a-a79a-b4194110fb30`, status `SUCCESS`.
- `/readyz` retornou 200; `/v1/entities?type=person&lang=en-BR` retornou 200 e `Content-Language: pt-BR`.
- Testes Android selecionados: 5 de localização dos livros, 10 do cliente de texto, 2 de áudio, sem falhas. Build de ambos os apps aprovado.
