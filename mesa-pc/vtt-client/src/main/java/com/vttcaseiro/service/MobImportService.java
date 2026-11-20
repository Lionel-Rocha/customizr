package com.vttcaseiro.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vttcaseiro.app.Log;
import com.vttcaseiro.mob.MobImporter;
import com.vttcaseiro.mob.MobSheet;
import com.vttcaseiro.repository.MesaRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Le um {@code bestiary-*.json} do 5e.tools (ex.: {@code bestiary-xmm.json},
 * o Manual dos Monstros 2024) e salva cada monstro novo como uma
 * {@link MobSheet} nesta mesa -- mesmo papel que {@link ClassImportService}
 * tem pra classes e {@link CompendiumImportService} pros outros 5 tipos de
 * conteudo, so que pra monstro (ver {@code MobImporter} pro motivo de nao
 * ser o mesmo sistema de {@code CompendiumEntry}).
 */
public class MobImportService {
    private static final Log log = Log.of(MobImportService.class);

    private final MesaRepository repository;
    private final MobService mobService;

    public MobImportService(MesaRepository repository, MobService mobService) {
        this.repository = repository;
        this.mobService = mobService;
    }

    /** @throws IOException se nao conseguir ler o arquivo ou salvar algum monstro
     * @throws com.google.gson.JsonSyntaxException se o arquivo nao for um JSON valido */
    public MobImporter.ImportResult importFromFile(Path jsonFile) throws IOException {
        return importFromJson(Files.readString(jsonFile));
    }

    /** Mesmo import, a partir do texto JSON (usado pelo conteudo pronto que
     * vem dentro do programa, ver Main.onLoadSeedContent). */
    public MobImporter.ImportResult importFromJson(String content) throws IOException {
        JsonObject payload = JsonParser.parseString(content).getAsJsonObject();
        MobImporter.ImportResult result = MobImporter.importMonsters(payload, repository.listMobs());
        for (MobSheet mob : result.imported()) {
            mobService.save(mob);
        }
        log.info("Importação de monstros: " + result.imported().size() + " novo(s), "
                + result.skipped().size() + " pulado(s) (nome já existia nesta mesa).");
        return result;
    }
}
