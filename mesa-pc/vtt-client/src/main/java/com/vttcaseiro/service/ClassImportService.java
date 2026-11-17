package com.vttcaseiro.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vttcaseiro.app.Log;
import com.vttcaseiro.content.ClassImporter;
import com.vttcaseiro.content.DndClass;
import com.vttcaseiro.repository.MesaRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Dono das classes de D&D importadas nesta mesa. Carrega o que ja foi
 * importado antes assim que a mesa abre, e {@link #importFromFile} le um
 * arquivo class-XXX.json do 5etools (ver com.vttcaseiro.content.ClassImporter,
 * que faz o trabalho de verdade) e salva o resultado de volta na mesa.
 */
public class ClassImportService {
    private static final Log log = Log.of(ClassImportService.class);

    private final MesaRepository repository;
    private List<DndClass> classes;

    public ClassImportService(MesaRepository repository) {
        this.repository = repository;
        try {
            this.classes = repository.loadClasses();
        } catch (IOException ex) {
            log.warn("Nao foi possivel carregar classes salvas, comecando do zero: " + ex.getMessage());
            this.classes = new ArrayList<>();
        }
    }

    public List<DndClass> getClasses() {
        return classes;
    }

    /** @throws IOException se nao conseguir ler o arquivo ou salvar o resultado
     * @throws IllegalArgumentException se o arquivo nao for um class-XXX.json valido (sem array "class")
     * @throws com.google.gson.JsonSyntaxException se o arquivo nao for um JSON valido */
    public ClassImporter.ImportResult importFromFile(Path jsonFile) throws IOException {
        String content = Files.readString(jsonFile);
        JsonObject payload = JsonParser.parseString(content).getAsJsonObject();
        ClassImporter.ImportResult result = ClassImporter.importClasses(payload, classes);
        repository.saveClasses(classes);
        log.info("Importacao de classes: " + result.imported().size() + " importadas, "
                + result.skipped().size() + " puladas.");
        return result;
    }
}
