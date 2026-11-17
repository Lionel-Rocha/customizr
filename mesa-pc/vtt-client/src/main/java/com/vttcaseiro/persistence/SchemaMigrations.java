package com.vttcaseiro.persistence;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vttcaseiro.app.Log;

import java.io.IOException;
import java.io.Reader;

/**
 * Versao do formato dos arquivos salvos da mesa + as migracoes entre
 * versoes. Cada arquivo de ficha/cena/monstro/party ganha um campo
 * {@code "schemaVersion"}; arquivo sem o campo e da versao 1 (tudo que foi
 * salvo antes disto existir).
 * <p>
 * Ao LER: o JSON vira arvore, passa pelas migracoes ate {@link #CURRENT} e
 * so entao vira objeto. Ao GRAVAR: o objeto vira arvore e ganha
 * {@code schemaVersion = CURRENT}. Assim, quando um campo for renomeado ou
 * mudar de tipo no futuro, basta escrever o passo "vN -&gt; vN+1" aqui --
 * os arquivos velhos continuam abrindo.
 * <p>
 * Pra adicionar uma migracao: suba {@link #CURRENT} e trate o caso novo em
 * {@link #step}. Sempre sobre a ARVORE JSON (nunca sobre a classe Java, que
 * ja tem o formato novo).
 */
public final class SchemaMigrations {
    private static final Log log = Log.of(SchemaMigrations.class);

    /** Versao atual do formato. */
    public static final int CURRENT = 2;
    public static final String FIELD = "schemaVersion";

    public enum Kind { CHARACTER, SCENE, MOB, PARTY }

    private SchemaMigrations() {
    }

    public static int versionOf(JsonObject json) {
        return json.has(FIELD) && json.get(FIELD).isJsonPrimitive() ? json.get(FIELD).getAsInt() : 1;
    }

    /** Leva o JSON ate a versao atual (altera e devolve o mesmo objeto). */
    public static JsonObject upgrade(Kind kind, JsonObject json) {
        int version = versionOf(json);
        if (version > CURRENT) {
            log.warn("Arquivo de " + kind + " foi salvo por uma versao MAIS NOVA do programa (formato " + version
                    + ", este entende ate " + CURRENT + "). Abrindo do jeito que der -- campos novos serao ignorados.");
            return json;
        }
        while (version < CURRENT) {
            step(kind, version, json);
            version++;
        }
        json.addProperty(FIELD, CURRENT);
        return json;
    }

    /** Um passo de migracao: de {@code from} pra {@code from + 1}. */
    private static void step(Kind kind, int from, JsonObject json) {
        if (from == 1 && kind == Kind.CHARACTER) {
            moveAttackTraitsToAttackTable(json);
        }
        // Cena/monstro/party: v1 -> v2 nao muda nada alem de carimbar a versao.
    }

    /**
     * v1 -> v2 (ficha): tracos com categoria "attack" eram o jeito antigo de
     * anotar ataque (a ficha nova ate vinha com um de exemplo). Agora existe a
     * tabela de ataques de verdade: viram linhas dela, com bonus e dano
     * extraidos do texto quando der ("+5 para acertar, 1d8+3 de dano").
     */
    static void moveAttackTraitsToAttackTable(JsonObject sheet) {
        if (!sheet.has("traits") || !sheet.get("traits").isJsonArray()) return;
        JsonArray traits = sheet.getAsJsonArray("traits");
        JsonArray attacks = sheet.has("attacks") && sheet.get("attacks").isJsonArray()
                ? sheet.getAsJsonArray("attacks") : new JsonArray();
        JsonArray kept = new JsonArray();
        for (JsonElement el : traits) {
            if (!el.isJsonObject()) {
                kept.add(el);
                continue;
            }
            JsonObject t = el.getAsJsonObject();
            String category = t.has("category") && !t.get("category").isJsonNull() ? t.get("category").getAsString() : "";
            if (!"attack".equalsIgnoreCase(category)) {
                kept.add(t);
                continue;
            }
            String name = t.has("name") && !t.get("name").isJsonNull() ? t.get("name").getAsString() : "Ataque";
            String desc = t.has("description") && !t.get("description").isJsonNull() ? t.get("description").getAsString() : "";
            JsonObject attack = new JsonObject();
            attack.addProperty("name", name);
            java.util.regex.Matcher bonus = java.util.regex.Pattern.compile("([+-]\\s*\\d+)\\s*(para acertar|to hit)").matcher(desc);
            attack.addProperty("attackBonusOrDc", bonus.find() ? bonus.group(1).replaceAll("\\s+", "") : "");
            java.util.regex.Matcher dmg = java.util.regex.Pattern.compile("\\d*d\\d+(\\s*[+-]\\s*\\d+)?[^.,;]*").matcher(desc);
            attack.addProperty("damageAndType", dmg.find() ? dmg.group().trim() : "");
            attack.addProperty("notes", desc);
            attacks.add(attack);
        }
        sheet.add("traits", kept);
        sheet.add("attacks", attacks);
    }

    /** Le um arquivo ja migrado pro formato atual. */
    public static <T> T read(Gson gson, Reader reader, Class<T> type, Kind kind) throws IOException {
        JsonElement tree;
        try {
            tree = JsonParser.parseReader(reader);
        } catch (RuntimeException ex) {
            throw new com.google.gson.JsonSyntaxException(ex.getMessage(), ex);
        }
        if (tree == null || tree.isJsonNull()) return null;
        if (!tree.isJsonObject()) throw new com.google.gson.JsonSyntaxException("Esperava um objeto JSON");
        return gson.fromJson(upgrade(kind, tree.getAsJsonObject()), type);
    }

    /** JSON pronto pra gravar, com a versao atual carimbada. */
    public static String write(Gson gson, Object value) {
        JsonElement tree = gson.toJsonTree(value);
        if (tree.isJsonObject()) tree.getAsJsonObject().addProperty(FIELD, CURRENT);
        return gson.toJson(tree);
    }
}
