# OlhoVivoSP atualizado

Projeto Android Java/XML reconstruído integralmente do código consolidado fornecido. Abra esta pasta no Android Studio. Consulte VALIDACAO.md para alterações, limitações e roteiro de testes.

Configure o SDK Android em local.properties. O token de desenvolvedor da API Olho Vivo é configurado dentro do app, pelo menu de configurações, e armazenado criptografado no dispositivo. O Gradle Wrapper binário não fazia parte do material fornecido e precisa ser restaurado ou gerado localmente.

As geometrias GTFS prontas estão em app/src/main/assets/gtfs, comprimidas por trajeto. Para atualizar a partir dos arquivos de origem, execute `python tools/build_gtfs.py /pasta/GTFS`. Os dados brutos não precisam ser copiados para res.
