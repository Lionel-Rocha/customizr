package com.vttcaseiro.importer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * O JSON exportado do Azgaar ("Export > JSON > Full"). Alem dos reinos, povoados e marcadores (os mesmos do .map),
 * ele traz a geometria: cada celula (pack.cells) com os vertices do poligono, altura, bioma e reino, e os vertices
 * (pack.vertices) com as coordenadas. Com isso o mapa pode ser desenhado aqui ({@link AzgaarRender}), inteiro ou
 * so uma regiao, sem precisar do PNG. O arquivo pode ter dezenas de MB: as celulas sao lidas aos poucos, direto pra
 * arrays, sem montar a arvore inteira na memoria.
 */
public final class AzgaarJson {
    private AzgaarJson() {
    }

    /** A geometria do mapa: poligonos das celulas, altura (0-100; abaixo de 20 e agua), bioma, reino e cores. */
    public record Geometry(int[][] cellVertices, int[][] cellNeighbors, double[] cellX, double[] cellY, int[] height,
                           int[] biome, int[] state, double[] vertexX, double[] vertexY, String[] biomeColors,
                           String[] stateColors, List<double[]> statePoles, List<String> stateNames, List<Integer> stateIds,
                           List<River> rivers, List<Route> routes) {
        public int cells() {
            return height.length;
        }

        /** A mesma geometria com outras cores (por numero do reino) e nomes dos reinos (na ordem de stateIds). */
        public Geometry withStates(String[] colors, List<String> names) {
            return new Geometry(cellVertices, cellNeighbors, cellX, cellY, height, biome, state, vertexX, vertexY, biomeColors,
                    colors, statePoles, names, stateIds, rivers, routes);
        }
    }

    public record River(int[] cells, double discharge, String name) {
    }

    /** Estrada: "roads", "trails" ou "searoutes", e os pontos (x, y). */
    public record Route(String group, double[] xs, double[] ys) {
    }

    /** names: bases de nomes e culturas (null no .map). */
    public record Result(AzgaarMap.World world, Geometry geometry, AzgaarNames.Book names) {
        public Result(AzgaarMap.World world, Geometry geometry) {
            this(world, geometry, null);
        }
    }

    /** O texto parece um JSON "Full" do Azgaar? (tem "info" com a descricao do gerador e "pack") */
    public static boolean looksLike(String head) {
        return head != null && head.contains("Fantasy-map-generator") || head != null && head.contains("\"pack\"");
    }

    public static Result read(Reader in) throws IOException {
        JsonObject info = null;
        JsonObject settings = null;
        JsonArray states = new JsonArray(), burgs = new JsonArray(), markers = new JsonArray(),
                cultures = new JsonArray(), religions = new JsonArray(), rivers = new JsonArray(), routes = new JsonArray(),
                notes = new JsonArray(), nameBases = new JsonArray();
        JsonElement biomes = null;
        List<int[]> cellV = new ArrayList<>(), cellC = new ArrayList<>();
        List<double[]> cellP = new ArrayList<>();
        List<Integer> cellH = new ArrayList<>(), cellB = new ArrayList<>(), cellS = new ArrayList<>();
        List<double[]> vertP = new ArrayList<>();
        try (JsonReader r = new JsonReader(in)) {
            r.setStrictness(com.google.gson.Strictness.LENIENT);
            if (r.peek() != JsonToken.BEGIN_OBJECT) throw new IOException("Não é um JSON do Azgaar.");
            r.beginObject();
            while (r.hasNext()) {
                String key = r.nextName();
                switch (key) {
                    case "info" -> info = obj(JsonParser.parseReader(r));
                    case "settings" -> settings = obj(JsonParser.parseReader(r));
                    case "notes" -> notes = arr(JsonParser.parseReader(r));
                    case "nameBases" -> nameBases = arr(JsonParser.parseReader(r));
                    case "pack" -> {
                        r.beginObject();
                        while (r.hasNext()) {
                            String pk = r.nextName();
                            switch (pk) {
                                case "cells" -> readCells(r, cellV, cellC, cellP, cellH, cellB, cellS);
                                case "vertices" -> readVertices(r, vertP);
                                case "states" -> states = arr(JsonParser.parseReader(r));
                                case "burgs" -> burgs = arr(JsonParser.parseReader(r));
                                case "markers" -> markers = arr(JsonParser.parseReader(r));
                                case "cultures" -> cultures = arr(JsonParser.parseReader(r));
                                case "religions" -> religions = arr(JsonParser.parseReader(r));
                                case "rivers" -> rivers = arr(JsonParser.parseReader(r));
                                case "routes" -> routes = arr(JsonParser.parseReader(r));
                                case "biomes" -> biomes = JsonParser.parseReader(r);
                                default -> r.skipValue();
                            }
                        }
                        r.endObject();
                    }
                    default -> r.skipValue();
                }
            }
        } catch (RuntimeException ex) {
            throw new IOException("JSON do Azgaar estragado: " + ex.getMessage());
        }
        if (info == null || states.isEmpty() && burgs.isEmpty() && cellV.isEmpty()) {
            throw new IOException("Esse JSON não parece do Azgaar. No gerador: Export > JSON > \"Full\".");
        }
        int width = Json.intOr(info, "width", 0), height = Json.intOr(info, "height", 0);
        String mapName = Json.str(info, "mapName");
        double distanceScale = 3;
        String distanceUnit = "km";
        double popRate = 1000, urban = 1;
        int year = 0;
        String era = null;
        String description = null;
        if (settings != null) {
            if (mapName == null) mapName = Json.str(settings, "mapName");
            Double ds = Json.num(settings, "distanceScale");
            if (ds != null && ds > 0) distanceScale = ds;
            if (Json.str(settings, "distanceUnit") != null) distanceUnit = Json.str(settings, "distanceUnit");
            Double pr = Json.num(settings, "populationRate");
            if (pr != null && pr > 0) popRate = pr;
            Double ur = Json.num(settings, "urbanization");
            if (ur != null && ur > 0) urban = ur;
            year = Json.intOr(settings, "options.map.lore.calendar.year", 0);
            era = Json.str(settings, "options.map.lore.calendar.era");
            description = Json.stripHtml(Json.str(settings, "options.map.lore.description"));
        }
        AzgaarMap.World world = AzgaarMap.build(Json.str(info, "version"), mapName, description, width, height,
                distanceScale, distanceUnit, popRate, urban, year, era, notes, states, burgs, markers, cultures, religions, null);

        Geometry geo = null;
        if (!cellV.isEmpty() && !vertP.isEmpty()) {
            int n = cellV.size();
            double[] cx = new double[n], cy = new double[n];
            int[] h = new int[n], b = new int[n], s = new int[n];
            for (int i = 0; i < n; i++) {
                double[] p = cellP.get(i);
                cx[i] = p == null ? 0 : p[0];
                cy[i] = p == null ? 0 : p[1];
                h[i] = cellH.get(i);
                b[i] = cellB.get(i);
                s[i] = cellS.get(i);
            }
            double[] vx = new double[vertP.size()], vy = new double[vertP.size()];
            for (int i = 0; i < vertP.size(); i++) {
                double[] p = vertP.get(i);
                vx[i] = p == null ? 0 : p[0];
                vy[i] = p == null ? 0 : p[1];
            }
            List<double[]> poles = new ArrayList<>();
            List<String> names = new ArrayList<>();
            List<Integer> ids = new ArrayList<>();
            int maxState = 0;
            for (JsonElement el : states) if (el.isJsonObject()) maxState = Math.max(maxState, Json.intOr(el.getAsJsonObject(), "i", 0));
            String[] stateColors = new String[maxState + 1];
            for (JsonElement el : states) {
                if (!el.isJsonObject()) continue;
                JsonObject st = el.getAsJsonObject();
                int i = Json.intOr(st, "i", 0);
                if (i <= 0 || st.has("removed")) continue;
                stateColors[i] = Json.str(st, "color");
                JsonArray pole = Json.arr(st, "pole");
                if (pole.size() == 2 && isNum(pole.get(0)) && isNum(pole.get(1)) && Json.str(st, "name") != null) {
                    poles.add(new double[]{pole.get(0).getAsDouble(), pole.get(1).getAsDouble()});
                    names.add(Json.str(st, "name"));
                    ids.add(i);
                }
            }
            List<River> riverList = new ArrayList<>();
            for (JsonElement el : rivers) {
                if (!el.isJsonObject()) continue;
                JsonArray cs = Json.arr(el.getAsJsonObject(), "cells");
                int[] cells = new int[cs.size()];
                for (int i = 0; i < cs.size(); i++) cells[i] = cs.get(i).isJsonPrimitive() ? cs.get(i).getAsInt() : -1;
                Double dis = Json.num(el.getAsJsonObject(), "discharge");
                riverList.add(new River(cells, dis == null ? 10 : dis, Json.str(el.getAsJsonObject(), "name")));
            }
            List<Route> routeList = new ArrayList<>();
            for (JsonElement el : routes) {
                if (!el.isJsonObject()) continue;
                JsonArray pts = Json.arr(el.getAsJsonObject(), "points");
                double[] xs = new double[pts.size()], ys = new double[pts.size()];
                for (int i = 0; i < pts.size(); i++) {
                    JsonArray pt = pts.get(i).isJsonArray() ? pts.get(i).getAsJsonArray() : null;
                    if (pt == null || pt.size() < 2 || !isNum(pt.get(0)) || !isNum(pt.get(1))) continue; // ponto sem numero: pula
                    xs[i] = pt.get(0).getAsDouble();
                    ys[i] = pt.get(1).getAsDouble();
                }
                routeList.add(new Route(Json.str(el.getAsJsonObject(), "group"), xs, ys));
            }
            geo = new Geometry(cellV.toArray(new int[0][]), cellC.toArray(new int[0][]), cx, cy, h, b, s, vx, vy,
                    biomeColors(biomes), stateColors, poles, names, ids, riverList, routeList);
        }
        return new Result(world, geo, AzgaarNames.read(nameBases, cultures));
    }

    /** Cores dos biomas: lista de objetos {color} (versoes novas) ou {color: [...]} (antigas). */
    static String[] biomeColors(JsonElement biomes) {
        List<String> out = new ArrayList<>();
        if (biomes != null && biomes.isJsonArray()) {
            for (JsonElement el : biomes.getAsJsonArray()) out.add(el.isJsonObject() ? Json.str(el.getAsJsonObject(), "color") : null);
        } else if (biomes != null && biomes.isJsonObject()) {
            for (JsonElement el : Json.arr(biomes.getAsJsonObject(), "color")) out.add(el.isJsonPrimitive() ? el.getAsString() : null);
        }
        if (out.isEmpty()) {
            // padrao do gerador (marinho, deserto quente, deserto frio, savana, campo, floresta tropical seca,
            // floresta tropical umida, floresta temperada umida, floresta temperada, taiga, tundra, geleira, pantano)
            out.addAll(List.of("#466eab", "#fbe79f", "#b5b887", "#d2d082", "#c8d68f", "#b6d95d", "#29bc56", "#7dcb35",
                    "#409c43", "#4b6b32", "#96784b", "#d5e7eb", "#0b9131"));
        }
        return out.toArray(new String[0]);
    }

    private static void readCells(JsonReader r, List<int[]> cellV, List<int[]> cellC, List<double[]> cellP, List<Integer> cellH,
                                  List<Integer> cellB, List<Integer> cellS) throws IOException {
        r.beginArray();
        while (r.hasNext()) {
            int[] v = null, c = null;
            double[] p = null;
            int h = 0, b = 0, s = 0;
            r.beginObject();
            while (r.hasNext()) {
                switch (r.nextName()) {
                    case "v" -> v = ints(r);
                    case "c" -> c = ints(r);
                    case "p" -> p = doubles(r);
                    case "h" -> h = intValue(r);
                    case "biome" -> b = intValue(r);
                    case "state" -> s = intValue(r);
                    default -> r.skipValue();
                }
            }
            r.endObject();
            cellV.add(v == null ? new int[0] : v);
            cellC.add(c == null ? new int[0] : c);
            cellP.add(p);
            cellH.add(h);
            cellB.add(b);
            cellS.add(s);
        }
        r.endArray();
    }

    private static void readVertices(JsonReader r, List<double[]> vertP) throws IOException {
        r.beginArray();
        while (r.hasNext()) {
            double[] p = null;
            r.beginObject();
            while (r.hasNext()) {
                if ("p".equals(r.nextName())) p = doubles(r);
                else r.skipValue();
            }
            r.endObject();
            vertP.add(p);
        }
        r.endArray();
    }

    private static int intValue(JsonReader r) throws IOException {
        if (r.peek() == JsonToken.NULL) {
            r.nextNull();
            return 0;
        }
        return (int) Math.round(r.nextDouble());
    }

    private static int[] ints(JsonReader r) throws IOException {
        if (r.peek() != JsonToken.BEGIN_ARRAY) {
            r.skipValue();
            return new int[0];
        }
        List<Integer> out = new ArrayList<>();
        r.beginArray();
        while (r.hasNext()) out.add(intValue(r));
        r.endArray();
        int[] a = new int[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }

    private static double[] doubles(JsonReader r) throws IOException {
        if (r.peek() != JsonToken.BEGIN_ARRAY) {
            r.skipValue();
            return null;
        }
        List<Double> out = new ArrayList<>();
        r.beginArray();
        while (r.hasNext()) {
            if (r.peek() == JsonToken.NULL) {
                r.nextNull();
                out.add(0.0);
            } else {
                out.add(r.nextDouble());
            }
        }
        r.endArray();
        double[] a = new double[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }

    private static JsonObject obj(JsonElement e) {
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static JsonArray arr(JsonElement e) {
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    /** Numero de verdade (o JSON pode trazer null num ponto). */
    private static boolean isNum(com.google.gson.JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber();
    }
}
