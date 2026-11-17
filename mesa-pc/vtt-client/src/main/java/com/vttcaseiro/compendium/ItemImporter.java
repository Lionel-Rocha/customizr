package com.vttcaseiro.compendium;

import com.google.gson.JsonObject;
import com.vttcaseiro.content.ClassJsonAnalyzer;
import com.vttcaseiro.content.EntriesRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Le um arquivo de item do 5e.tools -- tanto {@code items-base.json}
 * (formato {@code {"baseitem": [...]}}, equipamento mundano: armas,
 * armaduras, ferramentas) quanto {@code items.json} (formato
 * {@code {"item": [...]}}, que mistura mundano e magico) -- e devolve
 * uma {@link CompendiumEntry} do tipo {@link CompendiumEntryType#ITEM}
 * pra cada item com nome e fonte. Os dois formatos usam basicamente os
 * mesmos nomes de campo (rarity/value/weight/reqAttune/entries), por
 * isso um metodo so cobre os dois arquivos -- quem chama so precisa
 * saber qual dos dois campos ("baseitem" ou "item") o arquivo que abriu
 * tem; esta classe confere os dois e importa o que encontrar.
 * <p>
 * Item mundano (sem "entries" no 5e.tools, so os campos mecanicos) fica
 * com {@code fullText} vazio -- a descricao dele e so os numeros
 * (peso/valor), que ja ficam nos campos proprios de
 * {@link CompendiumEntry}, nao faz sentido inventar um texto.
 * <p>
 * A parte dificil ja foi isolada em {@link EntriesRenderer}/
 * {@link ItemJsonAnalyzer} e testada à exaustao. ESTA classe aqui -- a
 * parte que so da pra fazer com o Gson de verdade -- eu NAO consegui
 * testar (sem Gson disponivel no ambiente onde escrevi isto). Revisei
 * com cuidado, mesmo padrao de {@code ClassImporter}.
 */
public final class ItemImporter {

    private ItemImporter() {
    }

    public static List<CompendiumEntry> importItems(JsonObject payload) {
        List<CompendiumEntry> result = new ArrayList<>();
        for (Map<String, Object> row : FiveToolsJson.toMapList(FiveToolsJson.arrayOrEmpty(payload, "baseitem"))) {
            addIfValid(row, result);
        }
        for (Map<String, Object> row : FiveToolsJson.toMapList(FiveToolsJson.arrayOrEmpty(payload, "item"))) {
            addIfValid(row, result);
        }
        return result;
    }

    private static void addIfValid(Map<String, Object> row, List<CompendiumEntry> result) {
        String name = ClassJsonAnalyzer.text(row, "name");
        String source = ClassJsonAnalyzer.text(row, "source");
        if (ClassJsonAnalyzer.isBlank(name) || ClassJsonAnalyzer.isBlank(source)) {
            return;
        }

        CompendiumEntry entry = new CompendiumEntry(CompendiumEntryType.ITEM, name);
        entry.setId(FiveToolsJson.stableId(CompendiumEntryType.ITEM, source, name));
        entry.setSource(source);

        Double valueGp = ItemJsonAnalyzer.valueGp(row);
        Double weight = ClassJsonAnalyzer.optionalInt(row, "weight")
                .map(Integer::doubleValue)
                .orElseGet(() -> readWeightAsDouble(row));
        entry.setItemValueGp(valueGp);
        entry.setItemWeightLb(weight);
        entry.setItemIsMagic(ItemJsonAnalyzer.isMagic(row));
        entry.setItemRarity(ItemJsonAnalyzer.displayRarity(row));
        entry.setItemRequiresAttunement(ItemJsonAnalyzer.requiresAttunement(row));

        // Linha de estatisticas ANTES da descricao em prosa -- ainda nao
        // existe tela que mostre valor/peso separado dos outros campos,
        // entao isso garante que apareca no texto que vai pro chat.
        List<String> statParts = new ArrayList<>();
        if (valueGp != null) statParts.add("Valor: " + ItemJsonAnalyzer.formatNumber(valueGp) + " PO");
        if (weight != null) statParts.add("Peso: " + ItemJsonAnalyzer.formatNumber(weight) + " lb");
        String statsLine = String.join(" | ", statParts);

        String prose = EntriesRenderer.render(ClassJsonAnalyzer.nestedList(row, "entries"));
        String attunementNote = ItemJsonAnalyzer.attunementNote(row);
        String attunementLine = attunementNote.isBlank() ? "" : "Requer sintonizacao (" + attunementNote + ").";

        StringBuilder body = new StringBuilder();
        appendBlock(body, statsLine);
        appendBlock(body, attunementLine);
        appendBlock(body, prose);
        entry.setFullText(body.toString());
        entry.addTag("item");
        if (!entry.getItemRarity().isBlank()) {
            entry.addTag(entry.getItemRarity());
        }

        result.add(entry);
    }

    /** "weight" quase sempre e inteiro, mas alguns itens (ex.: Dart,
     * 0.25 lb) usam fracao -- {@code ClassJsonAnalyzer.optionalInt} so
     * aceita inteiro, por isso este fallback especifico pra numero
     * fracionario em vez de perder o peso desses itens. */
    private static Double readWeightAsDouble(Map<String, Object> row) {
        Object value = row.get("weight");
        return value instanceof Number n ? n.doubleValue() : null;
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
