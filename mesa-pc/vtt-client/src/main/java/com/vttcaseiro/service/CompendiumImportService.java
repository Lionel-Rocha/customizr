package com.vttcaseiro.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vttcaseiro.app.Log;
import com.vttcaseiro.compendium.BackgroundImporter;
import com.vttcaseiro.compendium.Compendium;
import com.vttcaseiro.compendium.FeatImporter;
import com.vttcaseiro.compendium.ItemImporter;
import com.vttcaseiro.compendium.SpeciesImporter;
import com.vttcaseiro.compendium.SpellImporter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Le os arquivos de dados do 5e.tools (antecedentes, especies/racas,
 * feats, itens, magias) e acrescenta o resultado ao {@link
 * CompendiumService} desta mesa -- mesmo papel que
 * {@link ClassImportService} tem pra classes, so que pros outros 5
 * tipos de conteudo que viram {@code CompendiumEntry} em vez de
 * {@code DndClass}.
 * <p>
 * ORDEM RECOMENDADA DE IMPORTACAO (pra ligacao entre tipos funcionar --
 * ver {@code BackgroundImporter}/{@code SpeciesImporter}): primeiro
 * {@link #importSpells}, depois {@link #importFeats}, so DEPOIS
 * {@link #importBackgrounds} e {@link #importSpecies} (que procuram
 * magia/feat ja existente pra linkar). {@link #importItems} nao
 * depende de ordem. Importar fora dessa ordem nao da erro nenhum --
 * so faz a entrada ficar sem o link cruzado (reimportar depois, na
 * ordem certa, NAO conserta isso: {@code Compendium.merge} pula a
 * entrada como duplicata e a versao sem link continua sendo a que
 * esta salva).
 */
public class CompendiumImportService {
    private static final Log log = Log.of(CompendiumImportService.class);

    private final CompendiumService compendiumService;

    public CompendiumImportService(CompendiumService compendiumService) {
        this.compendiumService = compendiumService;
    }

    /** @throws IOException se nao conseguir ler o arquivo ou salvar o resultado
     * @throws com.google.gson.JsonSyntaxException se o arquivo nao for um JSON valido */
    public Compendium.MergeResult importBackgrounds(Path jsonFile) throws IOException {
        JsonObject payload = readJson(jsonFile);
        var entries = BackgroundImporter.importBackgrounds(payload, compendiumService.getCompendium());
        return addAndLog(entries, "antecedentes");
    }

    public Compendium.MergeResult importSpecies(Path jsonFile) throws IOException {
        JsonObject payload = readJson(jsonFile);
        var entries = SpeciesImporter.importSpecies(payload, compendiumService.getCompendium());
        return addAndLog(entries, "especies");
    }

    public Compendium.MergeResult importFeats(Path jsonFile) throws IOException {
        JsonObject payload = readJson(jsonFile);
        var entries = FeatImporter.importFeats(payload);
        return addAndLog(entries, "feats");
    }

    /** Aceita tanto {@code items-base.json} quanto {@code items.json} --
     * ver {@code ItemImporter}, que confere os dois formatos de campo
     * ("baseitem" e "item") no mesmo arquivo. */
    public Compendium.MergeResult importItems(Path jsonFile) throws IOException {
        JsonObject payload = readJson(jsonFile);
        var entries = ItemImporter.importItems(payload);
        return addAndLog(entries, "itens");
    }

    public Compendium.MergeResult importSpells(Path jsonFile) throws IOException {
        JsonObject payload = readJson(jsonFile);
        var entries = SpellImporter.importSpells(payload);
        return addAndLog(entries, "magias");
    }

    private Compendium.MergeResult addAndLog(java.util.List<com.vttcaseiro.compendium.CompendiumEntry> entries,
            String label) throws IOException {
        Compendium.MergeResult result = compendiumService.addEntries(entries);
        log.info("Importação de " + label + ": " + result.added().size() + " novas, "
                + result.skipped().size() + " puladas (já existiam).");
        return result;
    }

    private JsonObject readJson(Path jsonFile) throws IOException {
        String content = Files.readString(jsonFile);
        return JsonParser.parseString(content).getAsJsonObject();
    }
}
