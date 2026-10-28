# Alterações e validação

- Trajeto GTFS por shape, selecionado por linha completa e destino normalizado. O carregador aceita o `.json` usado no APK após o empacotamento Android e também o `.json.gz` original. Sem correspondência, omite a geometria; não conecta paradas artificialmente.
- Letreiro preserva lt + tl (8400-10). Cabeçalho apresenta origem / destino conforme o sentido, preservando o destino abaixo e o comportamento de destino já existente no projeto.
- Busca clara, campos contornados, cartões e paleta verde-petróleo.
- Menu hambúrguer na tela inicial com acesso às configurações e versão do app no rodapé; cabeçalho inicial com título e descrição.
- Token Olho Vivo configurável no app, salvo cifrado com chave do Android Keystore. O token não é mais lido de local.properties nem incluído em BuildConfig.
- Ícone do app atualizado para o tema verde-petróleo, com o ônibus centralizado.
- Marcadores de parada carregados do GTFS local por linha e shape. O app usa a sequência de `stop_times.txt` e as coordenadas/nome de `stops.txt`, em vez de depender da lista parcial retornada pela API.
- Nome do app atualizado para No Ponto SP, identificador para `com.janioofi.nopontosp`, com paleta teal/verde profundo, superfícies claras, texto grafite, avisos âmbar e erros vermelhos.
- Busca de paradas redesenhada; teclado fecha ao tocar em Buscar ou enviar a busca pelo teclado.
- Paginação local Anterior/Próxima de 20 itens em linhas, paradas e previsões. API retorna tudo; não existe paginação remota nesses endpoints.
- Previsões ao abrir ou tocar o marcador do ponto selecionado, com carregamento, vazio e erro. Não são horários programados GTFS nem garantia de chegada.
- Localização em ponto azul com halo, ônibus em selo verde com pictograma branco e atualização no balão.
- Leitura GTFS e conversão de respostas fora da thread principal, buscas antigas ignoradas, polling sem sobreposição, liberação do mapa e executor.

## Verificado neste ambiente
`:app:testDebugUnitTest` e `:app:assembleDebug` passaram, incluindo a compilação Java e o empacotamento dos recursos. O BuildConfig e o Manifest gerados usam `com.janioofi.nopontosp`. Os dois sentidos de 8400-10 têm shapes distintos (424 e 477 pontos); 9020-10 tem 18 paradas em cada sentido, e 848L-10 tem 62 e 69. As listas foram geradas dos arquivos GTFS `trips.txt`, `stop_times.txt` e `stops.txt`. Os arquivos shapes2/3 são continuações sem cabeçalho, tratados explicitamente pelo importador.

## Validar no Android Studio
Não houve teste de renderização em dispositivo/emulador nem teste real da API. Configure um token válido no menu do app e teste no aparelho a autenticação e os marcadores de parada.

Teste no aparelho: salvar/remover o token; buscar 8400 e abrir ambos os sentidos; confirmar os pontos de parada; abrir ponto com e sem previsão; conferir 20/21/40/41 resultados e navegação de páginas; negar/conceder GPS; sair/voltar ao mapa; simular rede lenta e erro HTTP.

Os dados GTFS representam o arquivo fornecido, não desvios em tempo real. Nomes sem correspondência exata normalizada ocultam o trajeto por segurança. O token salvo fica cifrado no armazenamento privado do dispositivo; como qualquer segredo usado por um app cliente, um dispositivo comprometido ainda pode expô-lo.
