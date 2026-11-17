package com.vttcaseiro.importer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vttcaseiro.model.Drawing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mundo do Azgaar's Fantasy Map Generator (azgaar.github.io/Fantasy-Map-Generator).
 * A imagem vem do "Export > PNG/JPEG"; o JSON opcional ("Export > JSON > Full") traz o nome do
 * mundo, os reinos e as cidades (pack.burgs, em pixels do mapa de info.width x info.height).
 * Daqui sai o texto do diario (reinos, capitais, maiores cidades) e, se pedido, marcadores com
 * o nome das cidades na posicao certa da imagem.
 */
public final class AzgaarWorld {

    public record Burg(String name, double x, double y, boolean capital, double population, String state) {
    }

    public record Result(String mapName, int mapWidth, int mapHeight, List<Burg> burgs, String journalText) {
        /**
         * Pins clicaveis pras cidades (capitais primeiro, depois as maiores), escalados pra imagem.
         * Cada pin ja traz reino/populacao e o link pra gerar o mapa da cidade no Watabou.
         */
        public List<com.vttcaseiro.model.MapPin> pins(int imageWidth, int imageHeight, int max) {
            List<com.vttcaseiro.model.MapPin> out = new ArrayList<>();
            if (mapWidth <= 0 || mapHeight <= 0) return out;
            double sx = imageWidth / (double) mapWidth, sy = imageHeight / (double) mapHeight;
            List<Burg> sorted = new ArrayList<>(burgs);
            sorted.sort(Comparator.comparing(Burg::capital).thenComparingDouble(Burg::population).reversed());
            for (Burg b : sorted) {
                if (out.size() >= max) break;
                String kind = b.capital ? "capital" : b.population >= 2000 ? "cidade" : "vila";
                com.vttcaseiro.model.MapPin pin = new com.vttcaseiro.model.MapPin(b.x * sx, b.y * sy, b.name, kind);
                StringBuilder t = new StringBuilder();
                if (b.capital) t.append("Capital").append(b.state != null ? " de " + b.state : "").append(".\n");
                else if (b.state != null) t.append("Reino: ").append(b.state).append("\n");
                if (b.population > 0) {
                    t.append("Populacao: ~").append(String.format("%,d", Math.round(b.population)).replace(',', '.'))
                            .append(" habitantes\n");
                }
                pin.setText(t.toString().strip());
                pin.setGeneratorUrl(GeneratorLinks.forPlace(b.name, kind, b.population));
                out.add(pin);
            }
            return out;
        }

        /** Marcadores com o nome das cidades, escalados pro tamanho da imagem importada. */
        public List<Drawing> labels(int imageWidth, int imageHeight, boolean capitalsOnly, int max) {
            List<Drawing> out = new ArrayList<>();
            if (mapWidth <= 0 || mapHeight <= 0) return out;
            double sx = imageWidth / (double) mapWidth, sy = imageHeight / (double) mapHeight;
            List<Burg> sorted = new ArrayList<>(burgs);
            sorted.sort(Comparator.comparing(Burg::capital).thenComparingDouble(Burg::population).reversed());
            for (Burg b : sorted) {
                if (out.size() >= max) break;
                if (capitalsOnly && !b.capital) continue;
                out.add(Drawing.text(b.x * sx, b.y * sy, (b.capital ? "★ " : "") + b.name,
                        b.capital ? "#FFD27A" : "#FFF4DE"));
            }
            return out;
        }
    }

    private AzgaarWorld() {
    }

    public static Result parse(String json) throws IOException {
        JsonObject o;
        try {
            JsonElement e = JsonParser.parseString(json.startsWith("﻿") ? json.substring(1) : json);
            o = e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (RuntimeException ex) {
            throw new IOException("Não é um JSON válido: " + ex.getMessage());
        }
        if (o == null || Json.obj(o, "pack") == null) {
            throw new IOException("Esse JSON não parece do Azgaar. No gerador: Menu > Export > JSON > \"Full\". "
                    + "(O arquivo .map de salvar também não serve aqui.)");
        }
        String name = Json.str(o, "info.mapName");
        if (name == null) name = "Mundo";
        int w = Json.intOr(o, "info.width", 0), h = Json.intOr(o, "info.height", 0);
        double popRate = Json.num(o, "settings.populationRate") == null ? 1000 : Json.num(o, "settings.populationRate");
        double urban = Json.num(o, "settings.urbanization") == null ? 1 : Json.num(o, "settings.urbanization");

        Map<Integer, String> states = new HashMap<>();
        List<String[]> stateRows = new ArrayList<>();
        for (JsonElement el : Json.arr(o, "pack.states")) {
            if (!el.isJsonObject()) continue;
            JsonObject s = el.getAsJsonObject();
            Integer i = Json.integer(s, "i");
            String sn = Json.str(s, "name");
            if (i == null || sn == null || i == 0 || s.has("removed")) continue;
            String full = Json.str(s, "fullName");
            states.put(i, sn);
            stateRows.add(new String[]{String.valueOf(i), full != null ? full : sn, String.valueOf(Json.intOr(s, "capital", 0))});
        }

        List<Burg> burgs = new ArrayList<>();
        Map<Integer, Burg> byId = new HashMap<>();
        for (JsonElement el : Json.arr(o, "pack.burgs")) {
            if (!el.isJsonObject()) continue;
            JsonObject b = el.getAsJsonObject();
            String bn = Json.str(b, "name");
            Double x = Json.num(b, "x"), y = Json.num(b, "y");
            if (bn == null || x == null || y == null || b.has("removed")) continue;
            boolean capital = Json.intOr(b, "capital", 0) == 1;
            double pop = (Json.num(b, "population") == null ? 0 : Json.num(b, "population")) * popRate * urban;
            Burg burg = new Burg(bn, x, y, capital, pop, states.get(Json.intOr(b, "state", 0)));
            burgs.add(burg);
            Integer id = Json.integer(b, "i");
            if (id != null) byId.put(id, burg);
        }

        StringBuilder t = new StringBuilder();
        t.append("Mundo gerado no Azgaar's Fantasy Map Generator.\n");
        if (!stateRows.isEmpty()) {
            t.append("\nREINOS\n");
            for (String[] r : stateRows) {
                Burg cap = byId.get(Integer.parseInt(r[2]));
                t.append("- ").append(r[1]).append(cap != null ? " (capital: " + cap.name + ")" : "").append('\n');
            }
        }
        List<Burg> biggest = new ArrayList<>(burgs);
        biggest.sort(Comparator.comparingDouble(Burg::population).reversed());
        if (!biggest.isEmpty()) {
            t.append("\nMAIORES CIDADES\n");
            for (Burg b : biggest.subList(0, Math.min(25, biggest.size()))) {
                t.append("- ").append(b.name);
                if (b.state != null) t.append(", ").append(b.state);
                if (b.population > 0) t.append(" (~").append(String.format("%,d", Math.round(b.population)).replace(',', '.'))
                        .append(" hab.)");
                t.append('\n');
            }
            t.append("\n").append(burgs.size()).append(" povoados no total.");
        }
        return new Result(name, w, h, burgs, t.toString().strip());
    }
}
