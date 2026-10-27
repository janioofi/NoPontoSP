# Alterações e validação

- Trajeto GTFS por shape, selecionado por linha completa e destino normalizado. O carregador aceita o `.json` usado no APK após o empacotamento Android e também o `.json.gz` original. Sem correspondência, omite a geometria; não conecta paradas artificialmente.
- Letreiro preserva lt + tl (8400-10). Cabeçalho apresenta origem / destino conforme o sentido, preservando o destino abaixo e o comportamento de destino já existente no projeto.
- Busca clara, campos contornados, cartões e paleta verde-petróleo.
- Menu hambúrguer na tela inicial com acesso às configurações e versão do app no rodapé; cabeçalho inicial com título e descrição.
- Token Olho Vivo configurável no app, salvo cifrado com chave do Android Keystore. O token não é mais lido de local.properties nem incluído em BuildConfig.
- Ícone do app atualizado para o tema verde-petróleo, com o ônibus centralizado.
- Marcadores pequenos de ponto de ônibus obtidos da API de paradas da linha; só são mostrados quando ficam a até 100 m do shape selecionado.
- Paginação local Anterior/Próxima de 20 itens em linhas, paradas e previsões. API retorna tudo; não existe paginação remota nesses endpoints.
- Previsões ao abrir ou tocar o marcador do ponto selecionado, com carregamento, vazio e erro. Não são horários programados GTFS nem garantia de chegada.
- Localização em ponto azul com halo, ônibus em selo verde com pictograma branco e atualização no balão.
- Leitura GTFS e conversão de respostas fora da thread principal, buscas antigas ignoradas, polling sem sobreposição, liberação do mapa e executor.

## Verificado neste ambiente
`:app:testDebugUnitTest` e `:app:assembleDebug` passaram, incluindo a compilação Java e o empacotamento dos recursos. Os dois sentidos de 8400-10 têm shapes distintos (424 e 477 pontos); os JSON empacotados foram conferidos contra os arquivos GTFS comprimidos. Os arquivos shapes2/3 são continuações sem cabeçalho, tratados explicitamente pelo importador.

## Validar no Android Studio
Não houve teste de renderização em dispositivo/emulador nem teste real da API. Configure um token válido no menu do app e teste no aparelho a autenticação e os marcadores de parada.

Teste no aparelho: salvar/remover o token; buscar 8400 e abrir ambos os sentidos; confirmar os pontos de parada; abrir ponto com e sem previsão; conferir 20/21/40/41 resultados e navegação de páginas; negar/conceder GPS; sair/voltar ao mapa; simular rede lenta e erro HTTP.

Os dados GTFS representam o arquivo fornecido, não desvios em tempo real. Nomes sem correspondência exata normalizada ocultam o trajeto por segurança. O token salvo fica cifrado no armazenamento privado do dispositivo; como qualquer segredo usado por um app cliente, um dispositivo comprometido ainda pode expô-lo.
