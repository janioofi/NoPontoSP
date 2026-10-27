package com.example.olhovivosp.repository;

import android.content.res.AssetManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

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

    private static String readGeometry(AssetManager assets, String shapeId) throws IOException {
        String assetBase = "gtfs/" + shapeId + ".json";
        InputStream raw;
        try {
            raw = assets.open(assetBase);
        } catch (FileNotFoundException missingUncompressedAsset) {
            raw = assets.open(assetBase + ".gz");
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
        JSONArray entries = index.optJSONArray(route.trim());
        if (entries == null || entries.length() == 0) return Collections.emptyList();

        String selectedShapeId;

        if (entries.length() == 1) {
            selectedShapeId = entries.getJSONArray(0).getString(1);
        } else {
            String normDest = normalize(destination);
            int bestMatchIndex = -1;
            int bestScore = -1;

            if (!normDest.isEmpty()) {
                for (int i = 0; i < entries.length(); i++) {
                    JSONArray entry = entries.getJSONArray(i);
                    String headsign = entry.getString(0);
                    String normHead = normalize(headsign);

                    if (normDest.equals(normHead)) {
                        bestMatchIndex = i;
                        break;
                    } else if (normHead.contains(normDest) || normDest.contains(normHead)) {
                        if (bestScore < 2) {
                            bestMatchIndex = i;
                            bestScore = 2;
                        }
                    }
                }
            }

            if (bestMatchIndex != -1) {
                selectedShapeId = entries.getJSONArray(bestMatchIndex).getString(1);
            } else {
                // Sentido SPTrans: 1 -> index 0 (direction_id 0), 2 -> index 1 (direction_id 1)
                int targetIndex = (sentido == 2) ? 1 : 0;
                if (targetIndex >= entries.length()) targetIndex = 0;
                selectedShapeId = entries.getJSONArray(targetIndex).getString(1);
            }
        }

        if (selectedShapeId == null || selectedShapeId.isEmpty()) {
            return Collections.emptyList();
        }

        JSONArray points;
        try {
            points = new JSONArray(readGeometry(assets, selectedShapeId));
        } catch (IOException e) {
            return Collections.emptyList();
        }

        List<GeoPoint> result = new ArrayList<>(points.length());
        for (int i = 0; i < points.length(); i++) {
            JSONArray pair = points.getJSONArray(i);
            result.add(new GeoPoint(pair.getDouble(0), pair.getDouble(1)));
        }
        return result;
    }
}
