# No Ponto SP

Aplicativo Android Java/XML para consultar linhas, paradas, previsões e posições de ônibus de São Paulo. O nome exibido é **No Ponto SP** e o identificador do app é `com.janioofi.nopontosp`. Abra esta pasta no Android Studio. Consulte VALIDACAO.md para alterações, limitações e roteiro de testes.

Configure o SDK Android em local.properties. O token de desenvolvedor da API Olho Vivo é configurado dentro do app, pelo menu de configurações, e armazenado criptografado no dispositivo. A aparência pode seguir o sistema ou ser definida como clara/escura nas configurações; o mapa usa os tiles do OpenStreetMap em ambos os modos e aplica um filtro escuro localmente, sem exigir chave de outro provedor.

Para compartilhar a versão de release, use `app/build/outputs/apk/release/app-release.apk`. A chave `app/no-pontosp-release-key.jks` e as propriedades de assinatura em `local.properties` são privadas, ficam fora do controle de versão e devem ser mantidas em backup seguro: são necessárias para publicar atualizações que possam ser instaladas sobre esta versão.

Os dados GTFS usados no mapa ficam em `app/src/main/assets/gtfs`. O `index.json` é um índice pequeno que relaciona o identificador da linha (`route_id`) ao destino (`trip_headsign`) e ao identificador da geometria (`shape_id`); não contém os pontos da rota. Cada arquivo `<shape_id>.json.gz` guarda as coordenadas da rota, e `stops/<shape_id>.json.gz` guarda os pontos de ônibus ordenados daquela viagem. As tabelas `stops.txt`, `stop_times.txt` e `trips.txt` são usadas para gerar esses marcadores locais, sem depender de a API retornar todas as paradas.

Para atualizar os arquivos derivados a partir do GTFS completo (incluindo `shapes1.txt`, `shapes2.txt` e `shapes3.txt`), execute `python tools/build_gtfs.py /pasta/GTFS`.
