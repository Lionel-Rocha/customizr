package com.vttcaseiro.compendium;

import com.google.gson.JsonObject;
import com.vttcaseiro.content.ClassJsonAnalyzer;
import com.vttcaseiro.content.EntriesRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Le um arquivo backgrounds.json do 5e.tools (formato:
 * {@code {"background": [...]}}) e devolve uma {@link CompendiumEntry}
 * do tipo {@link CompendiumEntryType#BACKGROUND} pra cada antecedente
 * com nome e fonte.
 * <p>
 * LIGACAO COM FEAT: um antecedente do 5e.tools concede um feat fixo
 * (ver {@link BackgroundJsonAnalyzer#grantedFeatNames}) -- se esse feat
 * JA existir no compendio desta mesa (importado antes ou nesta mesma
 * chamada, numa importacao anterior na mesma sessao), a entrada do
 * antecedente ganha o id dele em {@code relatedEntryIds}
 * automaticamente. Pra essa ligacao funcionar, importe feats.json ANTES
 * de backgrounds.json (ver HANDOFF.md) -- se a ordem for invertida, o
 * antecedente ainda importa normalmente, so fica sem o link.
 * <p>
 * A parte dificil (texto legivel, extrair nome de feat concedido) ja
 * foi isolada em {@link EntriesRenderer}/{@link BackgroundJsonAnalyzer}
 * e testada à exaustao. ESTA classe aqui -- a parte que so da pra fazer
 * com o Gson de verdade -- eu NAO consegui testar (sem Gson disponivel
 * no ambiente onde escrevi isto). Revisei com cuidado, mesmo padrao de
 * {@code ClassImporter}.
 */
public final class BackgroundImporter {

    private BackgroundImporter() {
    }

    public static List<CompendiumEntry> importBackgrounds(JsonObject payload, Compendium existing) {
        List<Map<String, Object>> rows =
                FiveToolsJson.toMapList(FiveToolsJson.arrayOrEmpty(payload, "background"));
        List<CompendiumEntry> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String name = ClassJsonAnalyzer.text(row, "name");
            String source = ClassJsonAnalyzer.text(row, "source");
            if (ClassJsonAnalyzer.isBlank(name) || ClassJsonAnalyzer.isBlank(source)) {
                continue;
            }
            CompendiumEntry entry = new CompendiumEntry(CompendiumEntryType.BACKGROUND, name);
            entry.setId(FiveToolsJson.stableId(CompendiumEntryType.BACKGROUND, source, name));
            entry.setSource(source);
            entry.setFullText(EntriesRenderer.render(ClassJsonAnalyzer.nestedList(row, "entries")));
            entry.addTag("antecedente");

            for (String featName : BackgroundJsonAnalyzer.grantedFeatNames(row)) {
                existing.findByTypeAndName(CompendiumEntryType.FEAT, featName)
                        .ifPresent(feat -> entry.addRelatedEntryId(feat.getId()));
            }
            result.add(entry);
        }
        return result;
    }
}
