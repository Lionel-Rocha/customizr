package com.vttcaseiro.compendium;

import com.google.gson.JsonObject;
import com.vttcaseiro.content.ClassJsonAnalyzer;
import com.vttcaseiro.content.EntriesRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Le um arquivo feats.json do 5e.tools (formato:
 * {@code {"feat": [...]}}) e devolve uma {@link CompendiumEntry} do
 * tipo {@link CompendiumEntryType#FEAT} pra cada feat com nome e fonte
 * (os dois campos minimos pra uma entrada ter sentido -- sem eles, a
 * linha e silenciosamente ignorada). Quem chama decide onde juntar
 * (ver {@code Compendium.merge}) e salvar (ver
 * {@code com.vttcaseiro.service.CompendiumImportService}).
 * <p>
 * A parte dificil (texto legivel a partir de "entries") ja foi isolada
 * em {@link EntriesRenderer} e testada à exaustao. ESTA classe aqui --
 * a parte que so da pra fazer com o Gson de verdade -- eu NAO consegui
 * testar (sem Gson disponivel no ambiente onde escrevi isto). Revisei
 * com cuidado, mesmo padrao de {@code ClassImporter}.
 */
public final class FeatImporter {

    private FeatImporter() {
    }

    public static List<CompendiumEntry> importFeats(JsonObject payload) {
        List<Map<String, Object>> rows = FiveToolsJson.toMapList(FiveToolsJson.arrayOrEmpty(payload, "feat"));
        List<CompendiumEntry> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String name = ClassJsonAnalyzer.text(row, "name");
            String source = ClassJsonAnalyzer.text(row, "source");
            if (ClassJsonAnalyzer.isBlank(name) || ClassJsonAnalyzer.isBlank(source)) {
                continue;
            }
            CompendiumEntry entry = new CompendiumEntry(CompendiumEntryType.FEAT, name);
            entry.setId(FiveToolsJson.stableId(CompendiumEntryType.FEAT, source, name));
            entry.setSource(source);
            entry.setFullText(EntriesRenderer.render(ClassJsonAnalyzer.nestedList(row, "entries")));
            entry.setShortDescription(prerequisiteSummary(row));
            entry.addTag("feat");
            String category = ClassJsonAnalyzer.text(row, "category");
            if (!ClassJsonAnalyzer.isBlank(category)) {
                entry.addTag(category);
            }
            result.add(entry);
        }
        return result;
    }

    /** O campo "prerequisite" do 5e.tools e uma arvore bem rica (nivel
     * minimo, requisito de habilidade, de proficiencia, de outro feat
     * etc.) -- reconstruir isso em texto curto e preciso levaria quase
     * tanto codigo quanto {@code character.prerequisite} ja tem pro feat
     * MECANICO (que e o que de fato trava a escolha quando o jogador
     * pega o feat pra valer). Aqui, no feat de CATALOGO, so avisa que
     * existe um pre-requisito -- o texto completo dele ja esta na
     * descricao completa (fullText) de qualquer forma. */
    private static String prerequisiteSummary(Map<String, Object> row) {
        List<Object> prereqs = ClassJsonAnalyzer.nestedList(row, "prerequisite");
        return prereqs.isEmpty() ? "" : "Tem pre-requisito (ver descricao completa)";
    }
}
