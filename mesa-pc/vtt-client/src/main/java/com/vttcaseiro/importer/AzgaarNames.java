package com.vttcaseiro.importer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Nomes no estilo de cada cultura de um mundo do Azgaar. O JSON "Full" traz as bases de nomes (listas de nomes reais,
 * ex.: "German", "Elven") e cada cultura aponta pra uma delas. Igual o gerador faz: uma cadeia de letras aprendida da
 * lista (que letra costuma vir depois de cada par) inventa nomes novos que soam daquele povo.
 */
public final class AzgaarNames {

    /** Base de nomes: nome da base, tamanho minimo e maximo, e os exemplos. */
    public record Base(String name, int min, int max, List<String> words) {
    }

    /** Cultura do mundo e o numero da base de nomes dela. */
    public record Culture(int id, String name, int base) {
    }

    /** Tudo que precisa pra sugerir nomes (fica guardado a parte, pequeno, junto do mundo). */
    public record Book(List<Culture> cultures, List<Base> bases) {

        /** Culturas com base de nomes que existe (as "Wildlands"/sem base ficam de fora). */
        public List<Culture> usableCultures() {
            List<Culture> out = new ArrayList<>();
            for (Culture c : cultures) if (c.base >= 0 && c.base < bases.size() && !bases.get(c.base).words.isEmpty()) out.add(c);
            return out;
        }

        /** Um nome novo no estilo da cultura (null se ela nao tem base). */
        public String suggest(int cultureId, Random random) {
            for (Culture c : cultures) {
                if (c.id == cultureId && c.base >= 0 && c.base < bases.size()) return AzgaarNames.generate(bases.get(c.base), random);
            }
            return null;
        }
    }

    private AzgaarNames() {
    }

    /** Le "nameBases" (topo do JSON) e "pack.cultures". */
    static Book read(JsonArray nameBases, JsonArray cultures) {
        List<Base> bases = new ArrayList<>();
        for (JsonElement el : nameBases == null ? new JsonArray() : nameBases) {
            if (!el.isJsonObject()) {
                bases.add(new Base("", 0, 0, List.of()));
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            String b = Json.str(o, "b");
            List<String> words = new ArrayList<>();
            if (b != null) {
                for (String w : b.split(",")) {
                    String t = w.strip();
                    if (t.length() >= 2 && t.length() <= 30) words.add(t);
                }
            }
            bases.add(new Base(Json.str(o, "name"), Json.intOr(o, "min", 4), Json.intOr(o, "max", 10), words));
        }
        List<Culture> list = new ArrayList<>();
        for (JsonElement el : cultures == null ? new JsonArray() : cultures) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            if (o.has("removed") && o.get("removed").isJsonPrimitive() && o.get("removed").getAsBoolean()) continue;
            String name = Json.str(o, "name");
            if (name == null || name.isBlank()) continue;
            list.add(new Culture(Json.intOr(o, "i", 0), name, Json.intOr(o, "base", -1)));
        }
        return new Book(list, bases);
    }

    /** Inventa um nome com a cadeia de letras da base (tenta ate caber no tamanho; nunca repete um exemplo). */
    static String generate(Base base, Random random) {
        if (base.words.isEmpty()) return null;
        Map<String, List<Character>> chain = new HashMap<>();
        java.util.Set<String> known = new java.util.HashSet<>();
        for (String w : base.words) {
            String s = w.toLowerCase(Locale.ROOT);
            known.add(s);
            String key = "";
            for (int i = 0; i <= s.length(); i++) {
                char next = i < s.length() ? s.charAt(i) : '\0'; // '\0' = fim da palavra
                chain.computeIfAbsent(key, k -> new ArrayList<>()).add(next);
                if (next == '\0') break;
                key = (key + next);
                if (key.length() > 2) key = key.substring(key.length() - 2);
            }
        }
        int min = Math.max(2, base.min), max = Math.max(min + 1, Math.min(20, base.max));
        String best = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            StringBuilder sb = new StringBuilder();
            String key = "";
            int steps = 0;
            while (sb.length() < max && steps++ < 200) { // (limite: nunca fica preso)
                List<Character> options = chain.get(key);
                if (options == null || options.isEmpty()) break;
                char c = options.get(random.nextInt(options.size()));
                if (c == '\0') {
                    if (sb.length() >= min) break;
                    // curto demais: tenta outra letra; se so da pra terminar aqui, desiste dessa tentativa
                    if (options.stream().allMatch(o -> o == '\0')) break;
                    continue;
                }
                sb.append(c);
                key = (key + c);
                if (key.length() > 2) key = key.substring(key.length() - 2);
            }
            String name = sb.toString().strip();
            if (name.length() < min || name.endsWith("-") || name.endsWith("'")) continue;
            if (known.contains(name)) {
                if (best == null) best = name;
                continue;
            }
            return capitalize(name);
        }
        return best == null ? capitalize(base.words.get(random.nextInt(base.words.size())).toLowerCase(Locale.ROOT)) : capitalize(best);
    }

    /** Primeira letra de cada parte maiuscula ("aldor-veth" -> "Aldor-Veth", "ap enfors" -> "Ap Enfors"). */
    static String capitalize(String s) {
        StringBuilder out = new StringBuilder(s.length());
        boolean up = true;
        for (char c : s.toCharArray()) {
            out.append(up ? Character.toUpperCase(c) : c);
            up = c == ' ' || c == '-';
        }
        return out.toString();
    }
}
