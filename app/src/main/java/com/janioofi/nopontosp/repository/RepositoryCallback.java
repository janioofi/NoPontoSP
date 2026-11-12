package com.janioofi.nopontosp.repository;

/**
 * Callback assíncrono simples que isola a UI dos tipos do Retrofit (Call/Response),
 * para que Activities só conheçam tipos de domínio.
 */
public interface RepositoryCallback<T> {
    void onSuccess(T result);
    void onError(String message);
}
