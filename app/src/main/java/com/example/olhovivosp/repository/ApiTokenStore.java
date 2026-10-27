package com.example.olhovivosp.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Guarda o token cifrado com uma chave vinculada ao Android Keystore do dispositivo. */
final class ApiTokenStore {

    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "olhovivosp_api_token";
    private static final String PREFS = "olhovivosp_secure_settings";
    private static final String TOKEN_KEY = "encrypted_api_token";

    private final SharedPreferences preferences;

    ApiTokenStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    String read() throws GeneralSecurityException, IOException {
        String encoded = preferences.getString(TOKEN_KEY, "");
        if (encoded == null || encoded.isEmpty()) return "";

        String[] parts = encoded.split("\\.", 2);
        if (parts.length != 2) throw new GeneralSecurityException("Formato de token salvo inválido.");
        byte[] iv;
        byte[] encrypted;
        try {
            iv = Base64.decode(parts[0], Base64.NO_WRAP);
            encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
        } catch (IllegalArgumentException error) {
            throw new GeneralSecurityException("Token salvo inválido.", error);
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    void write(String token) throws GeneralSecurityException, IOException {
        String value = token == null ? "" : token.trim();
        if (value.isEmpty()) {
            if (!preferences.edit().remove(TOKEN_KEY).commit()) {
                throw new IOException("Não foi possível remover o token salvo.");
            }
            return;
        }

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        String encoded = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
                + "." + Base64.encodeToString(encrypted, Base64.NO_WRAP);
        if (!preferences.edit().putString(TOKEN_KEY, encoded).commit()) {
            throw new IOException("Não foi possível salvar o token.");
        }
    }

    private SecretKey getOrCreateKey() throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        java.security.Key existing = keyStore.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
