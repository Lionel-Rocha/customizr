package com.vttcaseiro.importer;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Links pros geradores do Watabou ja com nome/tamanho/semente, pra "gerar o mapa deste lugar":
 * o pin da cidade abre o Medieval Fantasy City Generator com aquela cidade; o mestre exporta o
 * PNG e liga a imagem ao pin. A semente sai do nome, entao o mesmo nome gera sempre a mesma cidade.
 */
public final class GeneratorLinks {
    private GeneratorLinks() {
    }

    /** Tamanho da cidade no gerador (6 a 40) pela populacao aproximada. */
    public static int citySize(double population) {
        if (population <= 0) return 15;
        return (int) Math.max(6, Math.min(40, Math.ceil(2.13 * Math.pow(population / 1000.0, 0.385) * 2)));
    }

    /** Link pro gerador certo pro tipo de pin, ou null se nao tiver gerador pra esse tipo. */
    public static String forPlace(String name, String kind, double population) {
        String n = name == null ? "" : name.strip();
        long seed = Math.abs((long) n.toLowerCase().hashCode()) % 1_000_000_000L + 1;
        String enc = URLEncoder.encode(n, StandardCharsets.UTF_8).replace("+", "%20");
        return switch (kind == null ? "" : kind) {
            case "cidade", "capital" -> "https://watabou.github.io/city-generator/?size="
                    + citySize(population > 0 ? population : "capital".equals(kind) ? 40000 : 8000)
                    + "&seed=" + seed + "&name=" + enc
                    + ("capital".equals(kind) ? "&citadel=1&walls=1&plaza=1&temple=1" : "");
            case "vila" -> "https://watabou.github.io/village-generator/?seed=" + seed + "&name=" + enc;
            case "masmorra" -> "https://watabou.github.io/one-page-dungeon/?seed=" + seed;
            default -> null;
        };
    }
}
