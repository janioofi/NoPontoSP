# NoPontoSP atualizado

Aplicativo Android Java/XML para consultar linhas, paradas, previsões e posições de ônibus de São Paulo. O nome exibido é **No Ponto SP** e o identificador do app é `com.janioofi.nopontosp`. Abra esta pasta no Android Studio. Consulte VALIDACAO.md para alterações, limitações e roteiro de testes.

Configure o SDK Android em local.properties. O token de desenvolvedor da API Olho Vivo é configurado dentro do app, pelo menu de configurações, e armazenado criptografado no dispositivo. A aparência pode seguir o sistema ou ser definida como clara/escura nas configurações; o mapa usa os tiles do OpenStreetMap em ambos os modos e aplica um filtro escuro localmente, sem exigir chave de outro provedor.

Linhas e paradas podem ser salvas pelos botões de favorito; os itens ficam no dispositivo e podem ser abertos pelo menu Favoritos. A tela Pontos próximos usa sua localização e o GTFS local para ordenar os pontos mais próximos. As buscas recentes aparecem como atalhos nas telas de busca. No mapa, toque em um ônibus para ver seus detalhes. Para receber um alerta de aproximação, abra o mapa por uma parada de uma linha e ative o alerta: ele consulta as posições online a cada 30 segundos e avisa quando um ônibus da linha estiver a até 500 metros; requer token da API e permissão de notificações.

Para compartilhar a versão de release, use `app/build/outputs/apk/release/app-release.apk`. A chave `app/no-pontosp-release-key.jks` e as propriedades de assinatura em `local.properties` são privadas, ficam fora do controle de versão e devem ser mantidas em backup seguro: são necessárias para publicar atualizações que possam ser instaladas sobre esta versão.

## Dados da SPTrans (GTFS e API Olho Vivo)

A SPTrans disponibiliza o GTFS e a API Olho Vivo para desenvolvedores. A documentação consultada descreve os serviços e o cadastro de aplicativos, mas não estabelece, por si só, uma licença explícita para republicar o conjunto de dados. A licença deste código não concede direitos sobre dados da SPTrans. Antes de redistribuir o GTFS ou seus derivados (inclusive arquivos `.json.gz`) em um fork, APK ou outro serviço, consulte os termos atuais de download e uso da SPTrans e confirme que a redistribuição é permitida. Na ausência de autorização clara, obtenha os dados diretamente da SPTrans e não os adicione ao repositório.

Para preparar os recursos localmente, baixe e extraia o GTFS oficial em uma pasta fora de `app/src/main/assets`, com `trips.txt`, `stops.txt`, `stop_times.txt` e `shapes.txt` (ou `shapes1.txt`, `shapes2.txt` e `shapes3.txt`). Execute, na raiz do projeto:

```powershell
python tools\build_gtfs.py "C:\caminho\para\gtfs"
Copy-Item "C:\caminho\para\gtfs\stops.txt" "app\src\main\assets\gtfs\stops.txt" -Force
```

O gerador grava os recursos derivados em `app/src/main/assets/gtfs`: `index.json` relaciona linha, destino e geometria; `<shape_id>.json.gz` armazena as coordenadas da rota; e `stops/<shape_id>.json.gz` contém as paradas ordenadas. `stops.txt` também é necessário para a tela Pontos próximos. Os recursos podem ser mantidos localmente para compilar e testar; só os publique depois de verificar os termos aplicáveis. Não inclua seu token pessoal da API Olho Vivo no código ou no repositório: cada pessoa deve configurar o próprio token no app.
