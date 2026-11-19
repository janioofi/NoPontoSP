package com.janioofi.nopontosp.repository;

import android.content.res.AssetManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;
import com.janioofi.nopontosp.domain.Parada;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * Geometrias GTFS com suporte a correspondência por sentido e destino normalizado.
 * Retorna os pontos reais do trajeto (shape) ao longo das vias.
 */
public final class GtfsGeometry {

    private GtfsGeometry() {}

    private static final class EntrySelection {
        final JSONArray entry;
        final boolean exactDirection;

        EntrySelection(JSONArray entry, boolean exactDirection) {
            this.entry = entry;
            this.exactDirection = exactDirection;
        }
    }

    private static String read(InputStream input) throws IOException {
        try (InputStream in = input; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, n);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String readJsonAsset(AssetManager assets, String assetBase) throws IOException {
        InputStream raw;
        try {
            raw = assets.open(assetBase + ".json");
        } catch (FileNotFoundException missingUncompressedAsset) {
            raw = assets.open(assetBase + ".json.gz");
        }

        BufferedInputStream input = new BufferedInputStream(raw);
        input.mark(2);
        int firstByte = input.read();
        int secondByte = input.read();
        input.reset();
        if (firstByte == 0x1f && secondByte == 0x8b) {
            return read(new GZIPInputStream(input));
        }
        return read(input);
    }

    private static String normalize(String text) {
        if (text == null) return "";
        String norm = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        norm = norm.replaceAll("\\b(terminal|term)\\b", "term")
                .replaceAll("\\b(praca|pca)\\b", "pca")
                .replaceAll("\\b(jardim|jd)\\b", "jd")
                .replaceAll("\\b(vila|vl)\\b", "vl")
                .replaceAll("\\b(parque|pq)\\b", "pq")
                .replaceAll("\\b(chacara|chac)\\b", "chac")
                .replaceAll("\\b(cemiterio|cem)\\b", "cem")
                .replaceAll("\\b(hospital|hosp)\\b", "hosp")
                .replaceAll("\\b(shopping|shop)\\b", "shop")
                .replaceAll("[^a-z0-9]", "");
        return norm;
    }

    public static List<GeoPoint> load(AssetManager assets, String route, String destination) throws Exception {
        return load(assets, route, -1, destination);
    }

    public static List<GeoPoint> load(AssetManager assets, String route, int sentido, String destination) throws Exception {
        if (route == null || route.trim().isEmpty()) return Collections.emptyList();

        JSONObject index = new JSONObject(read(assets.open("gtfs/index.json")));
        EntrySelection selection = selectEntry(index, route, sentido, destination, true);
        if (selection == null) return Collections.emptyList();
        JSONArray selectedEntry = selection.entry;
        String selectedShapeId = selectedEntry.getString(1);

        JSONArray points;
        try {
            points = new JSONArray(readJsonAsset(assets, "gtfs/" + selectedShapeId));
        } catch (IOException e) {
            return Collections.emptyList();
        }

        List<GeoPoint> result = new ArrayList<>(points.length());
        for (int i = 0; i < points.length(); i++) {
            JSONArray pair = points.getJSONArray(i);
            result.add(new GeoPoint(pair.getDouble(0), pair.getDouble(1)));
        }
        if (!selection.exactDirection && isReverseDirectionFallback(selectedEntry, sentido)) {
            Collections.reverse(result);
        }
        return result;
    }

    public static boolean hasExactDirection(AssetManager assets, String route, int sentido,
                                            String destination) throws Exception {
        if (route == null || route.trim().isEmpty()) return false;
        JSONObject index = new JSONObject(read(assets.open("gtfs/index.json")));
        return selectEntry(index, route, sentido, destination, false) != null;
    }

    public static List<Parada> loadStops(AssetManager assets, String route, int sentido,
                                         String destination) throws Exception {
        if (route == null || route.trim().isEmpty()) return Collections.emptyList();

        JSONObject index = new JSONObject(read(assets.open("gtfs/index.json")));
        EntrySelection selection = selectEntry(index, route, sentido, destination, false);
        if (selection == null) return Collections.emptyList();
        JSONArray selectedEntry = selection.entry;

        JSONArray stops;
        try {
            String tripId = selectedEntry.optString(3, "");
            if (!tripId.isEmpty()) {
                stops = new JSONArray(readJsonAsset(assets, "gtfs/stops_by_trip/" + tripId));
            } else {
                stops = new JSONArray(readJsonAsset(
                        assets, "gtfs/stops/" + selectedEntry.getString(1)));
            }
        } catch (FileNotFoundException missingStops) {
            return Collections.emptyList();
        }

        List<Parada> result = new ArrayList<>(stops.length());
        for (int i = 0; i < stops.length(); i++) {
            JSONArray stop = stops.getJSONArray(i);
            String stopId = stop.getString(0);
            int code;
            try {
                code = Integer.parseInt(stopId);
            } catch (NumberFormatException ignored) {
                code = 0;
            }
            String name = stop.getString(1);
            double latitude = stop.getDouble(2);
            double longitude = stop.getDouble(3);
            result.add(new Parada(code, name, "", latitude, longitude));
        }
        return result;
    }

    private static EntrySelection selectEntry(JSONObject index, String route, int sentido,
                                               String destination, boolean allowSingleFallback)
            throws Exception {
        JSONArray entries = index.optJSONArray(route.trim());
        if (entries == null || entries.length() == 0) return null;

        String normalizedDestination = normalize(destination);
        int bestMatchIndex = -1;
        int bestScore = 0;
        if (!normalizedDestination.isEmpty()) {
            for (int i = 0; i < entries.length(); i++) {
                JSONArray entry = entries.getJSONArray(i);
                String normalizedHeadsign = normalize(entry.getString(0));
                if (normalizedDestination.equals(normalizedHeadsign)) {
                    bestMatchIndex = i;
                    bestScore = Integer.MAX_VALUE;
                    break;
                }
                if (!normalizedHeadsign.isEmpty()
                        && (normalizedHeadsign.contains(normalizedDestination)
                        || normalizedDestination.contains(normalizedHeadsign))
                        && normalizedHeadsign.length() > bestScore) {
                    bestMatchIndex = i;
                    bestScore = normalizedHeadsign.length();
                }
            }
        }

        if (bestMatchIndex >= 0) {
            return new EntrySelection(entries.getJSONArray(bestMatchIndex), true);
        }

        String expectedDirectionId = sentido == 1 ? "0" : sentido == 2 ? "1" : "";
        if (!expectedDirectionId.isEmpty()) {
            for (int i = 0; i < entries.length(); i++) {
                JSONArray entry = entries.getJSONArray(i);
                if (entry.length() > 2 && expectedDirectionId.equals(entry.optString(2))) {
                    return new EntrySelection(entry, true);
                }
            }
            boolean usesLegacyIndex = true;
            for (int i = 0; i < entries.length(); i++) {
                if (entries.getJSONArray(i).length() > 2) {
                    usesLegacyIndex = false;
                    break;
                }
            }
            // Legacy indexes have no direction_id; use their order only when both exist.
            if (usesLegacyIndex && entries.length() >= 2 && sentido >= 1 && sentido <= 2) {
                int legacyIndex = sentido == 2 ? 1 : 0;
                return new EntrySelection(entries.getJSONArray(legacyIndex), true);
            }
        }
        if (allowSingleFallback && entries.length() == 1) {
            return new EntrySelection(entries.getJSONArray(0), false);
        }
        return null;
    }

    private static boolean isReverseDirectionFallback(JSONArray entry, int sentido)
            throws Exception {
        if (sentido != 1 && sentido != 2) return false;
        String expectedDirectionId = sentido == 1 ? "0" : "1";
        String entryDirectionId = entry.optString(2, "");
        if (!entryDirectionId.isEmpty()) {
            return !expectedDirectionId.equals(entryDirectionId);
        }
        return sentido == 2;
    }
}
