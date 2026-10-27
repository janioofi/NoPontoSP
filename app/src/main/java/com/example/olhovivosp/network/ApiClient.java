package com.example.olhovivosp.network;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Singleton simples do Retrofit/OkHttp para a API Olho Vivo. Mantém um único
 * CookieJar (e portanto uma única sessão) durante toda a vida do processo.
 */
public final class ApiClient {

    private static final String BASE_URL = "https://api.olhovivo.sptrans.com.br/v2.1/";

    private static volatile ApiClient instance;

    private final OlhoVivoApi api;
    private final InMemoryCookieJar cookieJar;

    private ApiClient() {
        cookieJar = new InMemoryCookieJar();

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        api = retrofit.create(OlhoVivoApi.class);
    }

    public static ApiClient getInstance() {
        if (instance == null) {
            synchronized (ApiClient.class) {
                if (instance == null) {
                    instance = new ApiClient();
                }
            }
        }
        return instance;
    }

    public OlhoVivoApi getApi() {
        return api;
    }

    /** Descarta a sessão atual (cookie). Usado pelo repositório ao reautenticar. */
    public void resetSession() {
        cookieJar.clear();
    }
}
