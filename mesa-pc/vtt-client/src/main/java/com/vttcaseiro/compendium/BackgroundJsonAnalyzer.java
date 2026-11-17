package com.vttcaseiro.compendium;

import com.vttcaseiro.content.ClassJsonAnalyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * As partes de {@link BackgroundImporter} que sao logica pura (so Map/
 * List/String, sem tocar no Gson) -- mesma divisao de {@code ClassImporter}/
 * {@code ClassJsonAnalyzer}: esta classe aqui da pra testar de verdade;
 * {@link BackgroundImporter} (a parte que le o JsonObject de verdade) so
 * foi revisada.
 */
public final class BackgroundJsonAnalyzer {

    private BackgroundJsonAnalyzer() {
    }

    /** O campo "feats" de um antecedente do 5e.tools e uma lista de
     * objetos cuja CHAVE e o nome do feat concedido -- as vezes com uma
     * nota de sub-escolha depois de ";" (ex.: a chave literal e
     * {@code "magic initiate; cleric|xphb"}, que significa "o feat Magic
     * Initiate, escolhendo a opcao Cleric") e sempre com a fonte depois
     * de "|". Devolve so o nome do feat, em Title Case, pra buscar no
     * compendio (ver {@link Compendium#findByTypeAndName}) -- a nota de
     * sub-escolha e a fonte sao descartadas de proposito aqui (a
     * descricao completa do feat, com a sub-escolha, ja vem no proprio
     * texto do antecedente). */
    @SuppressWarnings("unchecked")
    public static List<String> grantedFeatNames(Map<String, Object> backgroundRow) {
        List<String> names = new ArrayList<>();
        for (Object entry : ClassJsonAnalyzer.nestedList(backgroundRow, "feats")) {
            if (!(entry instanceof Map<?, ?> rawMap)) {
                continue;
            }
            for (Object key : ((Map<String, Object>) rawMap).keySet()) {
                String raw = String.valueOf(key);
                String withoutSource = raw.split("\\|")[0];
                String justName = withoutSource.split(";")[0].trim();
                if (!justName.isBlank()) {
                    String titled = titleCase(justName);
                    if (!names.contains(titled)) {
                        names.add(titled);
                    }
                }
            }
        }
        return names;
    }

    /** "magic initiate" -> "Magic Initiate". Nao tenta ser perfeito com
     * nomes que o 5e.tools capitaliza de forma especial (ex.: siglas) --
     * so capitaliza a primeira letra de cada palavra separada por
     * espaco, que cobre a imensa maioria dos nomes de feat/antecedente. */
    public static String titleCase(String lower) {
        if (lower == null || lower.isBlank()) {
            return "";
        }
        String[] words = lower.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
