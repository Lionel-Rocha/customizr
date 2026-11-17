package com.vttcaseiro.compendium;

import com.vttcaseiro.content.ClassJsonAnalyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * As partes de {@link SpeciesImporter} que sao logica pura -- mesma
 * divisao de {@code ClassImporter}/{@code ClassJsonAnalyzer} (ver
 * {@link BackgroundJsonAnalyzer} pro mesmo padrao do lado de
 * antecedentes).
 */
public final class SpeciesJsonAnalyzer {

    private SpeciesJsonAnalyzer() {
    }

    /** O campo "additionalSpells" de uma especie do 5e.tools concede
     * magias por nivel/situacao em formatos variados ("known"/"innate"
     * mapeando nivel-de-personagem -> lista de referencias tipo
     * {@code "light|xphb#c"}, o "#c" as vezes marcando que e conjurada
     * como truque). Devolve so o NOME da magia (antes do "|" e do "#"),
     * em Title Case, pra buscar no compendio -- percorre a estrutura
     * recursivamente porque a profundidade varia entre especies (algumas
     * tem so uma lista direta, outras uma lista por nivel). */
    @SuppressWarnings("unchecked")
    public static List<String> grantedSpellNames(Map<String, Object> speciesRow) {
        List<String> names = new ArrayList<>();
        for (Object block : ClassJsonAnalyzer.nestedList(speciesRow, "additionalSpells")) {
            if (!(block instanceof Map<?, ?> rawBlock)) {
                continue;
            }
            Map<String, Object> blockMap = (Map<String, Object>) rawBlock;
            // "ability" dentro do bloco e so a habilidade de conjuracao
            // (ex.: "cha") -- NAO e referencia de magia, por isso so
            // "known"/"innate"/"expanded" entram aqui.
            collectSpellRefs(blockMap.get("known"), names);
            collectSpellRefs(blockMap.get("innate"), names);
            collectSpellRefs(blockMap.get("expanded"), names);
        }
        return names;
    }

    private static void collectSpellRefs(Object node, List<String> names) {
        if (node instanceof Map<?, ?> rawMap) {
            for (Object v : ((Map<?, ?>) rawMap).values()) {
                collectSpellRefs(v, names);
            }
        } else if (node instanceof List<?> list) {
            for (Object item : list) {
                collectSpellRefs(item, names);
            }
        } else if (node instanceof String s) {
            String ref = s.split("\\|")[0];
            int hash = ref.indexOf('#');
            if (hash >= 0) {
                ref = ref.substring(0, hash);
            }
            ref = ref.trim();
            if (!ref.isBlank()) {
                String titled = BackgroundJsonAnalyzer.titleCase(ref);
                if (!names.contains(titled)) {
                    names.add(titled);
                }
            }
        }
    }
}
