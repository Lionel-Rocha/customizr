package top.customizr.system.model;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Links pros geradores do Watabou (watabou.github.io), ja com o nome e uma semente fixa pelo nome
 * (a mesma cidade gera sempre o mesmo mapa). O mestre abre, ajusta, exporta PNG e sobe como mapa da cidade.
 */
public final class GeneratorLinks {

    private GeneratorLinks() {}

    /** Tamanho da cidade no gerador (6 a 40) pela populacao aproximada. */
    public static int citySize(double population) {
        if (population <= 0) {
            return 15;
        }
        return (int) Math.max(6, Math.min(40, Math.ceil(2.13 * Math.pow(population / 1000.0, 0.385) * 2)));
    }

    public static String forCity(String name, Integer population, boolean capital) {
        String n = name == null ? "" : name.strip();
        long seed = seed(n);
        String encoded = URLEncoder.encode(n, StandardCharsets.UTF_8).replace("+", "%20");
        double pop = population != null && population > 0 ? population : capital ? 40000 : 8000;
        if (pop < 1000 && !capital) {
            return "https://watabou.github.io/village-generator/?seed=" + seed + "&name=" + encoded;
        }
        return "https://watabou.github.io/city-generator/?size=" + citySize(pop) + "&seed=" + seed + "&name=" + encoded
                + (capital ? "&citadel=1&walls=1&plaza=1&temple=1" : "");
    }

    public static String forDungeon(String name) {
        return "https://watabou.github.io/one-page-dungeon/?seed=" + seed(name == null ? "" : name.strip());
    }

    private static long seed(String name) {
        return Math.abs((long) name.toLowerCase().hashCode()) % 1_000_000_000L + 1;
    }
}
