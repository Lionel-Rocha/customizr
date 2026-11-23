package com.vttcaseiro.importer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vttcaseiro.model.MapLight;
import com.vttcaseiro.model.Wall;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Le mapas no formato Universal VTT (.dd2vtt do Dungeondraft, .uvtt, .df2vtt do DungeonFog): imagem do mapa
 * (base64) + tamanho da grade + paredes (line_of_sight), portas (portals) e luzes. As coordenadas do arquivo vem
 * em casas; aqui viram pixels da imagem (ja considerando a imagem reduzida pelo {@link MapImageFit}).
 */
public final class UniversalVtt {
    /** Teto de paredes/luzes lidas (arquivo malfeito ou gigante nao trava o programa). */
    public static final int MAX_WALLS = 20000;
    public static final int MAX_LIGHTS = 500;

    public record Result(byte[] image, String extension, double cellPx, int widthCells, int heightCells,
                         List<Wall> walls, List<MapLight> lights, int doors) {
    }

    private UniversalVtt() {
    }

    public static Result parse(String json) throws IOException {
        JsonObject root;
        try {
            JsonElement e = JsonParser.parseString(json.startsWith("﻿") ? json.substring(1) : json);
            if (!e.isJsonObject()) throw new IOException("Não é um arquivo Universal VTT (.dd2vtt).");
            root = e.getAsJsonObject();
        } catch (RuntimeException ex) {
            throw new IOException("Não é um arquivo Universal VTT (.dd2vtt) válido: " + ex.getMessage());
        }
        JsonObject res = obj(root, "resolution");
        if (res == null) throw new IOException("O arquivo não tem a seção \"resolution\" (tamanho da grade).");
        double ppg = num(res, "pixels_per_grid", 0);
        if (!(ppg > 0) || ppg > 2000) throw new IOException("Tamanho da casa inválido no arquivo: " + ppg);
        JsonObject size = obj(res, "map_size");
        JsonObject origin = obj(res, "map_origin");
        double ox = origin == null ? 0 : num(origin, "x", 0);
        double oy = origin == null ? 0 : num(origin, "y", 0);
        int wCells = size == null ? 0 : (int) Math.round(num(size, "x", 0));
        int hCells = size == null ? 0 : (int) Math.round(num(size, "y", 0));

        String b64 = str(root, "image");
        if (b64 == null || b64.isBlank()) throw new IOException("O arquivo não tem a imagem do mapa.");
        byte[] raw;
        try {
            raw = Base64.getMimeDecoder().decode(b64.contains(",") && b64.startsWith("data:") ? b64.substring(b64.indexOf(',') + 1) : b64);
        } catch (IllegalArgumentException ex) {
            throw new IOException("A imagem dentro do arquivo está corrompida.");
        }
        if (isWebp(raw)) {
            throw new IOException("A imagem dentro do .dd2vtt está em WebP, que o programa não abre. No Dungeondraft, "
                    + "exporte de novo em Universal VTT escolhendo o formato de imagem PNG.");
        }
        MapImageFit.Fitted fit = MapImageFit.fit(raw, "mapa.png");
        // a imagem pode ter sido reduzida pra caber: as coordenadas (em casas) acompanham
        double scale = 1.0;
        if (wCells > 0 && fit.width() > 0) {
            double originalPx = wCells * ppg;
            scale = fit.width() / originalPx;
        }
        if (!(scale > 0) || !Double.isFinite(scale)) scale = 1.0;
        double cell = ppg * scale;

        List<Wall> walls = new ArrayList<>();
        addPolylines(walls, root.get("line_of_sight"), ox, oy, cell, false);
        addPolylines(walls, root.get("objects_line_of_sight"), ox, oy, cell, false);
        int doors = 0;
        JsonArray portals = arr(root, "portals");
        if (portals != null) {
            for (JsonElement pe : portals) {
                if (walls.size() >= MAX_WALLS) break;
                if (!pe.isJsonObject()) continue;
                JsonObject p = pe.getAsJsonObject();
                JsonArray bounds = arr(p, "bounds");
                if (bounds == null || bounds.size() < 2) continue;
                double[] a = point(bounds.get(0), ox, oy, cell);
                double[] b = point(bounds.get(1), ox, oy, cell);
                if (a == null || b == null) continue;
                Wall door = new Wall(a[0], a[1], b[0], b[1], true);
                JsonElement closed = p.get("closed");
                door.setOpen(closed != null && closed.isJsonPrimitive() && !closed.getAsBoolean());
                walls.add(door);
                doors++;
            }
        }

        List<MapLight> lights = new ArrayList<>();
        JsonArray ls = arr(root, "lights");
        if (ls != null) {
            int n = 1;
            for (JsonElement le : ls) {
                if (lights.size() >= MAX_LIGHTS) break;
                if (!le.isJsonObject()) continue;
                JsonObject l = le.getAsJsonObject();
                double[] pos = point(l.get("position"), ox, oy, cell);
                if (pos == null) continue;
                double range = num(l, "range", 0); // em casas
                if (!(range > 0)) continue;
                int dim = (int) Math.round(Math.min(MapLight.MAX_FEET, range * 5));
                lights.add(new MapLight("Luz " + n++, pos[0], pos[1], dim / 2, dim));
            }
        }
        return new Result(fit.bytes(), fit.extension(), cell, wCells, hCells, walls, lights, doors);
    }

    private static void addPolylines(List<Wall> out, JsonElement el, double ox, double oy, double cell, boolean door) {
        if (el == null || !el.isJsonArray()) return;
        for (JsonElement lineEl : el.getAsJsonArray()) {
            if (!lineEl.isJsonArray()) continue;
            double[] prev = null;
            for (JsonElement pe : lineEl.getAsJsonArray()) {
                if (out.size() >= MAX_WALLS) return;
                double[] p = point(pe, ox, oy, cell);
                if (p == null) continue;
                if (prev != null && Math.hypot(p[0] - prev[0], p[1] - prev[1]) > 0.5) {
                    out.add(new Wall(prev[0], prev[1], p[0], p[1], door));
                }
                prev = p;
            }
        }
    }

    /** {"x": casas, "y": casas} -> pixels, descontando a origem do mapa. */
    private static double[] point(JsonElement e, double ox, double oy, double cell) {
        if (e == null || !e.isJsonObject()) return null;
        JsonObject o = e.getAsJsonObject();
        double x = num(o, "x", Double.NaN);
        double y = num(o, "y", Double.NaN);
        if (!Double.isFinite(x) || !Double.isFinite(y)) return null;
        return new double[]{(x - ox) * cell, (y - oy) * cell};
    }

    private static boolean isWebp(byte[] b) {
        return b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }

    private static JsonObject obj(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static JsonArray arr(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static double num(JsonObject o, String k, double def) {
        JsonElement e = o.get(k);
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsDouble() : def;
        } catch (NumberFormatException ex) {
            return def;
        }
    }
}
