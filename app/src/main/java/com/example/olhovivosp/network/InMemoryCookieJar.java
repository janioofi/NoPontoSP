package com.example.olhovivosp.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

/**
 * A API Olho Vivo autentica por sessão (cookie ASP.NET), não por header Bearer.
 * O OkHttp não guarda cookies entre chamadas por padrão, então sem este CookieJar
 * cada requisição chegaria "deslogada" ao servidor, mesmo depois de autenticar com
 * sucesso em /Login/Autenticar. Implementação simples em memória, por host.
 */
public class InMemoryCookieJar implements CookieJar {

    private final Map<String, List<Cookie>> cookieStore = new HashMap<>();

    @Override
    public synchronized void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        cookieStore.put(url.host(), new ArrayList<>(cookies));
    }

    @Override
    public synchronized List<Cookie> loadForRequest(HttpUrl url) {
        List<Cookie> cookies = cookieStore.get(url.host());
        return cookies != null ? cookies : new ArrayList<>();
    }

    /** Limpa a sessão atual, forçando uma nova autenticação na próxima chamada. */
    public synchronized void clear() {
        cookieStore.clear();
    }
}
