package com.janioofi.nopontosp.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.janioofi.nopontosp.domain.Linha;
import com.janioofi.nopontosp.domain.Parada;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Persistent local favorites and recent transit searches. */
public final class LocalTransitStore {
    private static final String PREFS = "local_transit";
    private static final String LINES = "favorite_lines";
    private static final String STOPS = "favorite_stops";
    private static final String HISTORY = "search_history";
    private static final int MAX_HISTORY = 8;
    public static final String TYPE_LINE = "line";
    public static final String TYPE_STOP = "stop";

    private LocalTransitStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static List<Linha> favoriteLines(Context context) {
        List<Linha> result = new ArrayList<>();
        JSONArray rows = readArray(context, LINES);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject item = rows.optJSONObject(i);
            if (item != null) {
                result.add(new Linha(item.optInt("code"), item.optString("route"),
                        item.optInt("direction"), item.optString("primary"),
                        item.optString("secondary"), item.optBoolean("circular")));
            }
        }
        return result;
    }

    public static List<Parada> favoriteStops(Context context) {
        List<Parada> result = new ArrayList<>();
        JSONArray rows = readArray(context, STOPS);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject item = rows.optJSONObject(i);
            if (item != null) {
                result.add(new Parada(item.optInt("code"), item.optString("name"),
                        item.optString("address"), item.optDouble("lat"), item.optDouble("lon")));
            }
        }
        return result;
    }

    public static boolean isLineFavorite(Context context, Linha line) {
        String key = lineKey(line.codigoLinha, line.sentido);
        JSONArray rows = readArray(context, LINES);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject item = rows.optJSONObject(i);
            if (item != null && key.equals(item.optString("key"))) return true;
        }
        return false;
    }

    public static boolean isStopFavorite(Context context, Parada stop) {
        String key = Integer.toString(stop.codigoParada);
        JSONArray rows = readArray(context, STOPS);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject item = rows.optJSONObject(i);
            if (item != null && key.equals(item.optString("key"))) return true;
        }
        return false;
    }

    public static void toggleLine(Context context, Linha line) {
        JSONArray rows = readArray(context, LINES);
        String key = lineKey(line.codigoLinha, line.sentido);
        JSONArray updated = new JSONArray();
        boolean found = false;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject item = rows.optJSONObject(i);
            if (item == null || key.equals(item.optString("key"))) {
                found = true;
            } else {
                updated.put(item);
            }
        }
        if (!found) {
            JSONObject item = new JSONObject();
            try {
                item.put("key", key);
                item.put("code", line.codigoLinha);
                item.put("route", line.letreiro);
                item.put("direction", line.sentido);
                item.put("primary", line.terminalPrincipal);
                item.put("secondary", line.terminalSecundario);
                item.put("circular", line.circular);
                updated.put(item);
            } catch (JSONException error) {
                throw new IllegalStateException("Não foi possível salvar a linha favorita.", error);
            }
        }
        prefs(context).edit().putString(LINES, updated.toString()).apply();
    }

    public static void toggleStop(Context context, Parada stop) {
        JSONArray rows = readArray(context, STOPS);
        String key = Integer.toString(stop.codigoParada);
        JSONArray updated = new JSONArray();
        boolean found = false;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject item = rows.optJSONObject(i);
            if (item == null || key.equals(item.optString("key"))) {
                found = true;
            } else {
                updated.put(item);
            }
        }
        if (!found) {
            JSONObject item = new JSONObject();
            try {
                item.put("key", key);
                item.put("code", stop.codigoParada);
                item.put("name", stop.nome);
                item.put("address", stop.endereco);
                item.put("lat", stop.latitude);
                item.put("lon", stop.longitude);
                updated.put(item);
            } catch (JSONException error) {
                throw new IllegalStateException("Não foi possível salvar o ponto favorito.", error);
            }
        }
        prefs(context).edit().putString(STOPS, updated.toString()).apply();
    }

    public static void recordSearch(Context context, String type, String query) {
        String value = query == null ? "" : query.trim();
        if (value.isEmpty()) return;
        JSONArray rows = readArray(context, HISTORY);
        JSONArray updated = new JSONArray();
        updated.put(historyItem(type, value));
        for (int i = 0; i < rows.length() && updated.length() < MAX_HISTORY; i++) {
            JSONObject item = rows.optJSONObject(i);
            if (item != null && !(type.equals(item.optString("type"))
                    && value.equalsIgnoreCase(item.optString("query")))) {
                updated.put(item);
            }
        }
        prefs(context).edit().putString(HISTORY, updated.toString()).apply();
    }

    public static List<String> recentSearches(Context context, String type) {
        List<String> result = new ArrayList<>();
        JSONArray rows = readArray(context, HISTORY);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject item = rows.optJSONObject(i);
            if (item != null && type.equals(item.optString("type"))) {
                result.add(item.optString("query"));
            }
        }
        return result;
    }

    private static JSONObject historyItem(String type, String query) {
        JSONObject item = new JSONObject();
        try {
            item.put("type", type);
            item.put("query", query);
        } catch (JSONException error) {
            throw new IllegalStateException("Não foi possível registrar a busca.", error);
        }
        return item;
    }

    private static JSONArray readArray(Context context, String key) {
        String raw = prefs(context).getString(key, "[]");
        try {
            return new JSONArray(raw);
        } catch (JSONException error) {
            throw new IllegalStateException("Os dados locais de favoritos ou buscas estão inválidos.", error);
        }
    }

    private static String lineKey(int code, int direction) {
        return code + ":" + direction;
    }
}
