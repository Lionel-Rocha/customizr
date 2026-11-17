package com.vttcaseiro.importer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vttcaseiro.character.Ability;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.Skill;

import java.text.Normalizer;
import java.util.Locale;

/** Leitura tolerante de JSON de terceiros (campo faltando ou do tipo errado vira null/0) + ajudas comuns. */
final class Json {
    private Json() {
    }

    static JsonElement at(JsonObject o, String path) {
        if (o == null) return null;
        JsonElement cur = o;
        for (String k : path.split("\\.")) {
            if (cur == null || !cur.isJsonObject()) return null;
            cur = cur.getAsJsonObject().get(k);
        }
        return cur == null || cur.isJsonNull() ? null : cur;
    }

    static JsonObject obj(JsonObject o, String path) {
        JsonElement e = at(o, path);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    static JsonArray arr(JsonObject o, String path) {
        JsonElement e = at(o, path);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    static String str(JsonObject o, String path) {
        JsonElement e = at(o, path);
        if (e == null || !e.isJsonPrimitive()) return null;
        String s = e.getAsString().strip();
        return s.isEmpty() ? null : s;
    }

    static Double num(JsonObject o, String path) {
        JsonElement e = at(o, path);
        if (e == null || !e.isJsonPrimitive()) return null;
        try {
            return e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : Double.parseDouble(e.getAsString().trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    static Integer integer(JsonObject o, String path) {
        Double d = num(o, path);
        return d == null ? null : (int) Math.round(d);
    }

    static int intOr(JsonObject o, String path, int fallback) {
        Integer v = integer(o, path);
        return v == null ? fallback : v;
    }

    /** Tira tags HTML (descricoes do Foundry/D&D Beyond vem em HTML). */
    static String stripHtml(String html) {
        if (html == null) return null;
        String s = html.replaceAll("(?i)<br\\s*/?>|</p>|</li>|</h\\d>", "\n").replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&rsquo;", "'");
        s = s.replaceAll("[ \\t]+", " ").replaceAll("\\n\\s*\\n+", "\n\n").strip();
        return s.isEmpty() ? null : s;
    }

    /** "fórça D'água" -> "forcadagua" (pra comparar nomes de campo/pericia). */
    static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    static void setAbility(CharacterSheet s, Ability a, int score) {
        int v = Math.max(1, Math.min(30, score));
        switch (a) {
            case STRENGTH -> s.setStrength(v);
            case DEXTERITY -> s.setDexterity(v);
            case CONSTITUTION -> s.setConstitution(v);
            case INTELLIGENCE -> s.setIntelligence(v);
            case WISDOM -> s.setWisdom(v);
            case CHARISMA -> s.setCharisma(v);
        }
    }

    /** Pericia pelo nome em ingles ou portugues ("sleight-of-hand", "Prestidigitação", "animal handling"). */
    static Skill skill(String name) {
        String n = norm(name);
        if (n.isEmpty()) return null;
        for (Skill sk : Skill.values()) {
            if (norm(sk.name()).equals(n) || norm(sk.getDisplayNamePt()).equals(n)) return sk;
        }
        return switch (n) {
            case "animal", "animals", "adestraranimais", "lidaranimais" -> Skill.ANIMAL_HANDLING;
            case "slightofhand", "sleight", "slight", "furto", "maosleves" -> Skill.SLEIGHT_OF_HAND;
            case "intuicao", "insight" -> Skill.INSIGHT;
            case "atuacao", "performance" -> Skill.PERFORMANCE;
            default -> null;
        };
    }

    /** Deslocamento em pes -> texto em metros como a ficha usa ("30" -> "9 m"). */
    static String feetToMeters(double feet) {
        double m = Math.round(feet / 5.0 * 1.5 * 10) / 10.0;
        return (m == Math.floor(m) ? String.valueOf((int) m) : String.valueOf(m)).replace('.', ',') + " m";
    }

    static String titleCase(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
