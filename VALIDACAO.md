# Alterações e validação

- Trajeto GTFS por shape, selecionado por linha completa e destino normalizado. O carregador aceita o `.json` usado no APK após o empacotamento Android e também o `.json.gz` original. Sem correspondência, omite a geometria; não conecta paradas artificialmente.
- Letreiro preserva lt + tl (8400-10). Cabeçalho apresenta origem / destino conforme o sentido, preservando o destino abaixo e o comportamento de destino já existente no projeto.
- Busca clara, campos contornados, cartões e paleta verde-petróleo.
- Paginação local Anterior/Próxima de 20 itens em linhas, paradas e previsões. API retorna tudo; não existe paginação remota nesses endpoints.
- Previsões ao abrir ou tocar o marcador do ponto selecionado, com carregamento, vazio e erro. Não são horários programados GTFS nem garantia de chegada.
- Localização em ponto azul com halo, ônibus em selo verde com pictograma branco e atualização no balão.
- Leitura GTFS e conversão de respostas fora da thread principal, buscas antigas ignoradas, polling sem sobreposição, liberação do mapa e executor.

## Verificado neste ambiente
`:app:testDebugUnitTest` passou, incluindo a compilação Java. Os dois sentidos de 8400-10 têm shapes distintos (424 e 477 pontos); os JSON empacotados foram conferidos contra os arquivos GTFS comprimidos. Os arquivos shapes2/3 são continuações sem cabeçalho, tratados explicitamente pelo importador.

## Validar no Android Studio
Não houve teste de renderização em dispositivo/emulador nem teste real da API. Abra no Android Studio, configure o SDK e token em local.properties, restaure/gere o Wrapper se necessário e execute `assembleDebug`.

Teste no aparelho: buscar 8400, abrir ambos os sentidos, comparar desenho pelas ruas; abrir ponto com e sem previsão; conferir 20/21/40/41 resultados e navegação de páginas; negar/conceder GPS; sair/voltar ao mapa; simular rede lenta e erro HTTP.

Os dados GTFS representam o arquivo fornecido, não desvios em tempo real. Nomes sem correspondência exata normalizada ocultam o trajeto por segurança. O token embarcado em BuildConfig é recuperável de um APK; para distribuição pública, considerar um backend. Não foi incluído token.
