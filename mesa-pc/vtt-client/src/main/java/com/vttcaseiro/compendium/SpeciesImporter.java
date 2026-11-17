package com.vttcaseiro.compendium;

import com.google.gson.JsonObject;
import com.vttcaseiro.content.ClassJsonAnalyzer;
import com.vttcaseiro.content.EntriesRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Le um arquivo races.json do 5e.tools (formato:
 * {@code {"race": [...], "subrace": [...]}}) e devolve uma
 * {@link CompendiumEntry} do tipo {@link CompendiumEntryType#SPECIES}
 * pra cada especie (e pra cada SUB-especie que tenha nome proprio --
 * uma variante sem nome distinto, como algumas opcoes "escolha um
 * traco" de Elfo, e pulada de proposito, pra nao virar uma entrada
 * vazia/confusa no compendio; o nome da entrada fica
 * "EspecieBase (NomeDaSubespecie)").
 * <p>
 * LIGACAO COM MAGIA: uma especie pode conceder magias fixas (ver
 * {@link SpeciesJsonAnalyzer#grantedSpellNames}) -- se essa magia JA
 * existir no compendio desta mesa, a entrada da especie ganha o id dela
 * em {@code relatedEntryIds} automaticamente. Pra essa ligacao
 * funcionar, importe o arquivo de magias ANTES de races.json (ver
 * HANDOFF.md) -- se a ordem for invertida, a especie ainda importa
 * normalmente, so fica sem o link.
 * <p>
 * A parte dificil ja foi isolada em {@link EntriesRenderer}/
 * {@link SpeciesJsonAnalyzer} e testada à exaustao. ESTA classe aqui --
 * a parte que so da pra fazer com o Gson de verdade -- eu NAO consegui
 * testar (sem Gson disponivel no ambiente onde escrevi isto). Revisei
 * com cuidado, mesmo padrao de {@code ClassImporter}.
 */
public final class SpeciesImporter {

    private SpeciesImporter() {
    }

    public static List<CompendiumEntry> importSpecies(JsonObject payload, Compendium existing) {
        List<CompendiumEntry> result = new ArrayList<>();

        for (Map<String, Object> row : FiveToolsJson.toMapList(FiveToolsJson.arrayOrEmpty(payload, "race"))) {
            CompendiumEntry entry = buildEntry(row, null, existing);
            if (entry != null) {
                result.add(entry);
            }
        }

        for (Map<String, Object> row : FiveToolsJson.toMapList(FiveToolsJson.arrayOrEmpty(payload, "subrace"))) {
            String subraceName = ClassJsonAnalyzer.text(row, "name");
            String raceName = ClassJsonAnalyzer.text(row, "raceName");
            if (ClassJsonAnalyzer.isBlank(subraceName) || ClassJsonAnalyzer.isBlank(raceName)) {
                continue; // variante sem nome proprio -- nao vira entrada separada
            }
            CompendiumEntry entry = buildEntry(row, raceName + " (" + subraceName + ")", existing);
            if (entry != null) {
                result.add(entry);
            }
        }

        return result;
    }

    private static CompendiumEntry buildEntry(Map<String, Object> row, String overrideName, Compendium existing) {
        String name = overrideName != null ? overrideName : ClassJsonAnalyzer.text(row, "name");
        String source = ClassJsonAnalyzer.text(row, "source");
        if (ClassJsonAnalyzer.isBlank(name) || ClassJsonAnalyzer.isBlank(source)) {
            return null;
        }

        CompendiumEntry entry = new CompendiumEntry(CompendiumEntryType.SPECIES, name);
        entry.setId(FiveToolsJson.stableId(CompendiumEntryType.SPECIES, source, name));
        entry.setSource(source);
        entry.setFullText(EntriesRenderer.render(ClassJsonAnalyzer.nestedList(row, "entries")));
        entry.addTag("especie");

        for (String spellName : SpeciesJsonAnalyzer.grantedSpellNames(row)) {
            existing.findByTypeAndName(CompendiumEntryType.SPELL, spellName)
                    .ifPresent(spell -> entry.addRelatedEntryId(spell.getId()));
        }
        return entry;
    }
}
