package com.vttcaseiro.compendium;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Glue de Gson compartilhada pelos 5 importadores de compendio
 * (BackgroundImporter/SpeciesImporter/FeatImporter/ItemImporter/
 * SpellImporter) -- a mesma coisa que {@code ClassImporter} fazia
 * sozinho (ler um array JSON e devolver Map/List genericos pra lógica
 * pura dos *JsonAnalyzer processar), so que agora compartilhada em vez
 * de repetida 5 vezes. ESTA classe (ler o JsonObject/JsonArray de
 * verdade) eu NAO consegui testar aqui (sem Gson disponivel no ambiente
 * onde escrevi isto) -- revisada com cuidado, mesmo padrao de
 * {@code ClassImporter}.
 */
final class FiveToolsJson {

    private static final Gson GSON = new Gson();

    private FiveToolsJson() {
    }

    static JsonArray arrayOrEmpty(JsonObject root, String field) {
        JsonElement value = root == null ? null : root.get(field);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> toMap(JsonElement element) {
        return (Map<String, Object>) GSON.fromJson(element, Object.class);
    }

    static List<Map<String, Object>> toMapList(JsonArray array) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonElement el : array) {
            list.add(toMap(el));
        }
        return list;
    }

    /** Id deterministico (tipo+fonte+nome, tudo em minusculo) pra uma
     * entrada importada -- NAO e o que impede duplicata ao reimportar o
     * mesmo arquivo (isso e {@link Compendium#merge}, que compara
     * tipo+nome+fonte do conteudo, nao o id); e so uma conveniencia pra
     * reimportar a mesma fonte produzir sempre o MESMO id, em vez de um
     * UUID novo aleatorio a cada vez -- o que ajuda se algo em outro
     * lugar (ex.: um save antigo de CompendiumReference) guardou esse id
     * e precisar continuar apontando pro mesmo lugar depois de uma
     * reimportacao. */
    static String stableId(CompendiumEntryType type, String source, String name) {
        return (type.name() + "|" + nullToEmpty(source) + "|" + nullToEmpty(name))
                .toLowerCase(Locale.ROOT);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
