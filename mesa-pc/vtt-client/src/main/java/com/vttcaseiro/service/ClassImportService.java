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
    /** Resumo da importacao das classes oficiais: classes novas, subclasses novas e arquivos lidos. */
    public record OfficialImport(int classes, int subclasses, int files) {
        public boolean changedSomething() {
            return classes + subclasses > 0;
        }
    }

    /**
     * Classes e subclasses oficiais (Wizards e parceiros do D&D Beyond; sem World of Warcraft nem Unearthed Arcana)
     * a partir de um index.json + class-*.json do 5etools. {@code reader} le um arquivo pelo nome (null = nao tem).
     * Grava a mesa uma vez so no fim.
     */
    public OfficialImport importOfficial(java.util.function.Function<String, String> reader) throws IOException {
        String index = reader.apply("index.json");
        if (index == null) return new OfficialImport(0, 0, 0);
        int classesAdded = 0;
        int subclassesAdded = 0;
        int files = 0;
        for (var entry : JsonParser.parseString(index).getAsJsonObject().entrySet()) {
            String content = reader.apply(entry.getValue().getAsString());
            if (content == null) continue;
            JsonObject payload = ClassImporter.officialOnly(JsonParser.parseString(content).getAsJsonObject());
            if (payload.getAsJsonArray("class").isEmpty()) continue;
            ClassImporter.ImportResult result = ClassImporter.importClasses(payload, classes);
            classesAdded += result.imported().size();
            subclassesAdded += result.subclassesAdded() + result.imported().stream().mapToInt(c -> c.subclassCount()).sum();
            files++;
        }
        if (classesAdded + subclassesAdded > 0) repository.saveClasses(classes);
        log.info("Classes oficiais: " + classesAdded + " classes e " + subclassesAdded + " subclasses novas.");
        return new OfficialImport(classesAdded, subclassesAdded, files);
    }

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
