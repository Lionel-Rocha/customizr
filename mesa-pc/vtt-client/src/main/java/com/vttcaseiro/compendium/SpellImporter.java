package com.vttcaseiro.compendium;

import com.google.gson.JsonObject;
import com.vttcaseiro.content.ClassJsonAnalyzer;
import com.vttcaseiro.content.EntriesRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Le um arquivo spells-XXX.json do 5e.tools (formato:
 * {@code {"spell": [...]}}) e devolve uma {@link CompendiumEntry} do
 * tipo {@link CompendiumEntryType#SPELL} pra cada magia com nome e
 * fonte. Cada livro tem seu proprio arquivo (ex.: {@code
 * spells-xphb.json} pro Jogador 2024) -- importar as magias de outro
 * livro e so apontar pro arquivo dele, o importador nao muda.
 * <p>
 * Nao inclui {@code entriesHigherLevel} (o paragrafo "Usando um Espaco
 * de Magia de Nivel Maior") no texto principal de proposito -- ele vai
 * ANEXADO no final do texto completo, separado por uma linha em branco,
 * em vez de misturado com a descricao base.
 * <p>
 * A parte dificil (texto legivel, tempo de conjuracao/alcance/duracao/
 * componentes formatados) ja foi isolada em {@link EntriesRenderer}/
 * {@link SpellJsonAnalyzer} e testada à exaustao. ESTA classe aqui -- a
 * parte que so da pra fazer com o Gson de verdade -- eu NAO consegui
 * testar (sem Gson disponivel no ambiente onde escrevi isto). Revisei
 * com cuidado, mesmo padrao de {@code ClassImporter}.
 */
public final class SpellImporter {

    private SpellImporter() {
    }

    public static List<CompendiumEntry> importSpells(JsonObject payload) {
        List<Map<String, Object>> rows = FiveToolsJson.toMapList(FiveToolsJson.arrayOrEmpty(payload, "spell"));
        List<CompendiumEntry> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String name = ClassJsonAnalyzer.text(row, "name");
            String source = ClassJsonAnalyzer.text(row, "source");
            if (ClassJsonAnalyzer.isBlank(name) || ClassJsonAnalyzer.isBlank(source)) {
                continue;
            }

            CompendiumEntry entry = new CompendiumEntry(CompendiumEntryType.SPELL, name);
            entry.setId(FiveToolsJson.stableId(CompendiumEntryType.SPELL, source, name));
            entry.setSource(source);

            entry.setSpellLevel(ClassJsonAnalyzer.optionalInt(row, "level").orElse(null));
            entry.setSpellSchool(SpellJsonAnalyzer.schoolName(ClassJsonAnalyzer.text(row, "school")));

            List<Object> timeField = ClassJsonAnalyzer.nestedList(row, "time");
            // A condicao da reacao/acao bonus vem com tags ({@variantrule
            // Unarmed Strike|XPHB}): passa pelo renderizador como o resto.
            entry.setSpellCastingTime(EntriesRenderer.renderInline(SpellJsonAnalyzer.castingTime(timeField)));

            Map<String, Object> rangeField = ClassJsonAnalyzer.nestedMap(row, "range");
            entry.setSpellRange(SpellJsonAnalyzer.range(rangeField));

            List<Object> durationField = ClassJsonAnalyzer.nestedList(row, "duration");
            entry.setSpellDuration(SpellJsonAnalyzer.duration(durationField));
            entry.setSpellConcentration(SpellJsonAnalyzer.concentration(durationField));

            Map<String, Object> meta = ClassJsonAnalyzer.nestedMap(row, "meta");
            entry.setSpellRitual(Boolean.TRUE.equals(meta.get("ritual")));

            Map<String, Object> componentsField = ClassJsonAnalyzer.nestedMap(row, "components");
            String componentsText = SpellJsonAnalyzer.components(componentsField);

            // Linha de estatisticas ANTES da descricao em prosa -- ainda
            // nao existe tela que mostre tempo/alcance/duracao/componentes
            // separado do resto, entao isso garante que apareca no texto
            // que vai pro chat.
            List<String> statParts = new ArrayList<>();
            if (!entry.getSpellCastingTime().isBlank()) statParts.add("Casting Time: " + entry.getSpellCastingTime());
            if (!entry.getSpellRange().isBlank()) statParts.add("Range: " + entry.getSpellRange());
            if (!componentsText.isBlank()) statParts.add("Components: " + componentsText);
            if (!entry.getSpellDuration().isBlank()) statParts.add("Duration: " + entry.getSpellDuration());
            if (Boolean.TRUE.equals(entry.getSpellRitual())) statParts.add("Ritual");
            String statsLine = String.join(" | ", statParts);

            String prose = EntriesRenderer.render(ClassJsonAnalyzer.nestedList(row, "entries"));
            List<Object> higherLevel = ClassJsonAnalyzer.nestedList(row, "entriesHigherLevel");
            String higherLevelText = higherLevel.isEmpty() ? "" : EntriesRenderer.render(higherLevel);

            StringBuilder body = new StringBuilder();
            appendBlock(body, statsLine);
            appendBlock(body, prose);
            appendBlock(body, higherLevelText);
            entry.setFullText(body.toString());

            entry.addTag("magia");
            if (entry.getSpellSchool() != null && !entry.getSpellSchool().isBlank()) {
                entry.addTag(entry.getSpellSchool());
            }

            result.add(entry);
        }
        return result;
    }

    private static void appendBlock(StringBuilder body, String block) {
        if (block == null || block.isBlank()) {
            return;
        }
        if (body.length() > 0) {
            body.append("\n\n");
        }
        body.append(block);
    }
}
