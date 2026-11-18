package com.vttcaseiro.content;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vttcaseiro.app.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Le um arquivo class-XXX.json do 5etools (formato:
 * {@code {"class":[...], "classFeature":[...], "subclass":[...], "subclassFeature":[...]}})
 * e monta {@link DndClass} com as features por nivel e as subclasses --
 * porta de {@code ClassService.importClasses} do projeto da minha noiva
 * (customizr), que faz exatamente isto em Spring/Jackson/Postgres. A
 * decisao de QUAL feature pertence a qual classe/subclasse, e em que
 * nivel, e identica -- so troquei Jackson por Gson e banco por lista em
 * memoria (que o chamador decide onde salvar -- ver
 * com.vttcaseiro.service.ClassImportService).
 * <p>
 * A parte difícil de acertar (casar classe com feature, achar o nivel
 * de desbloqueio de subclasse, extrair nivel de uma referencia tipo
 * "Rage|Barbarian||1", detectar se uma feature e escolha do jogador) ja
 * foi isolada em {@link ClassJsonAnalyzer} e testada à exaustao (24
 * casos). ESTA classe aqui -- a parte que so da pra fazer com o Gson de
 * verdade (ler o JsonObject/JsonArray) -- eu NAO consegui testar (sem
 * Gson disponivel no ambiente onde escrevi isto). Revisei com cuidado,
 * linha por linha, espelhando o codigo real dela -- mas e a unica peça
 * desta funcionalidade que fica sem prova de execucao.
 */
public final class ClassImporter {
    private static final Log log = Log.of(ClassImporter.class);
    private static final Gson GSON = new Gson();

    public record ImportedClass(String className, String sourceCode, int subclassCount, int featureCount) {
    }

    public record SkippedClass(String className, String sourceCode, String reason) {
    }

    /** {@code subclassesAdded}: subclasses que entraram em classes que ja estavam na mesa. */
    public record ImportResult(List<ImportedClass> imported, List<SkippedClass> skipped, int subclassesAdded) {
        public ImportResult(List<ImportedClass> imported, List<SkippedClass> skipped) {
            this(imported, skipped, 0);
        }
    }

    /** Fontes de classe que ficam de fora: World of Warcraft (nao e da Wizards nem do D&D Beyond). */
    private static final java.util.Set<String> EXCLUDED_SOURCES = java.util.Set.of("WOW");

    /** Unearthed Arcana (teste gratuito, "UA...") e World of Warcraft ficam de fora; o resto (Wizards e parceiros) entra. */
    public static boolean excludedSource(String code) {
        if (code == null) return true;
        String c = code.trim().toUpperCase(java.util.Locale.ROOT);
        return c.startsWith("UA") || EXCLUDED_SOURCES.contains(c);
    }

    /** Copia do class-*.json sem as classes, subclasses e habilidades de fonte de fora (ver excludedSource). */
    public static JsonObject officialOnly(JsonObject payload) {
        JsonObject out = new JsonObject();
        for (String key : List.of("class", "classFeature", "subclass", "subclassFeature")) {
            JsonArray kept = new JsonArray();
            JsonArray rows = arrayOrNull(payload, key);
            if (rows != null) {
                for (JsonElement row : rows) {
                    if (!row.isJsonObject()) continue;
                    JsonObject o = row.getAsJsonObject();
                    if (excludedSource(text(o, "source")) || (o.has("classSource") && excludedSource(text(o, "classSource")))
                            || (o.has("subclassSource") && excludedSource(text(o, "subclassSource")))) {
                        continue;
                    }
                    kept.add(o);
                }
            }
            out.add(key, kept);
        }
        return out;
    }

    private static String text(JsonObject o, String field) {
        JsonElement v = o.get(field);
        return v == null || !v.isJsonPrimitive() ? null : v.getAsString();
    }

    private ClassImporter() {
    }

    /**
     * @param payload o JSON inteiro do arquivo class-XXX.json
     * @param classes classes ja importadas antes (usado pra checar
     *                duplicata -- mesma logica "Already imported" do
     *                projeto dela) E TAMBEM onde as classes novas sao
     *                adicionadas -- ao final da chamada, esta lista
     *                contem tudo (o que ja tinha + o que acabou de
     *                importar), pronta pra salvar de volta.
     * @throws IllegalArgumentException se o payload nao tiver um array "class"
     */
    public static ImportResult importClasses(JsonObject payload, List<DndClass> classes) {
        JsonArray classEntries = arrayOrNull(payload, "class");
        if (classEntries == null) {
            throw new IllegalArgumentException("Esperava um objeto JSON com um array \"class\".");
        }
        List<Map<String, Object>> classFeatureRows = toMapList(arrayOrEmpty(payload, "classFeature"));
        List<Map<String, Object>> subclassRows = toMapList(arrayOrEmpty(payload, "subclass"));
        List<Map<String, Object>> subclassFeatureRows = toMapList(arrayOrEmpty(payload, "subclassFeature"));

        List<ImportedClass> imported = new ArrayList<>();
        List<SkippedClass> skipped = new ArrayList<>();
        int subclassesAdded = 0;

        for (JsonElement el : classEntries) {
            Map<String, Object> classEntry = toMap(el);
            String name = ClassJsonAnalyzer.text(classEntry, "name");
            String sourceCode = ClassJsonAnalyzer.text(classEntry, "source");

            String incomplete = incompleteReason(classEntry, sourceCode, subclassRows);
            if (incomplete != null) {
                skipped.add(new SkippedClass(name, sourceCode, incomplete));
                continue;
            }
            DndClass existing = classes.stream().filter(c ->
                    name.equals(c.getClassName()) && Objects.equals(sourceCode, c.getSourceCode())).findFirst().orElse(null);
            if (existing != null) {
                // classe que ja estava na mesa: so completa as subclasses que faltam (de outros livros)
                List<Map<String, Object>> missing = matching(subclassRows, row ->
                        ClassJsonAnalyzer.matchesClass(row, name, sourceCode)
                                && existing.getSubclasses().stream().noneMatch(sc ->
                                        Objects.equals(sc.getName(), ClassJsonAnalyzer.text(row, "name"))
                                                && Objects.equals(sc.getSourceCode(), ClassJsonAnalyzer.text(row, "source"))));
                subclassesAdded += importSubclasses(existing, missing, subclassFeatureRows);
                skipped.add(new SkippedClass(name, sourceCode, "Ja importada"));
                continue;
            }

            Map<String, Object> hitDie = ClassJsonAnalyzer.nestedMap(classEntry, "hd");
            int hitDieNumber = ClassJsonAnalyzer.optionalInt(hitDie, "number").orElse(1);
            int hitDieFaces = ClassJsonAnalyzer.optionalInt(hitDie, "faces").orElse(6);
            int subclassUnlockLevel = ClassJsonAnalyzer.findSubclassUnlockLevel(classEntry, sourceCode, subclassRows);

            DndClass dndClass = new DndClass(name, sourceCode, hitDieNumber, hitDieFaces, subclassUnlockLevel);
            dndClass.setSpellcastingAbility(ClassJsonAnalyzer.text(classEntry, "spellcastingAbility"));
            readProficiencies(classEntry, dndClass);

            List<Map<String, Object>> matchingClassFeatures = matching(classFeatureRows, row ->
                    ClassJsonAnalyzer.matchesClass(row, name, sourceCode));
            for (Map<String, Object> featureRow : matchingClassFeatures) {
                addClassFeature(dndClass, featureRow);
            }

            List<Map<String, Object>> matchingSubclasses = matching(subclassRows, row ->
                    ClassJsonAnalyzer.matchesClass(row, name, sourceCode));
            int subclassCount = importSubclasses(dndClass, matchingSubclasses, subclassFeatureRows);

            classes.add(dndClass);
            imported.add(new ImportedClass(name, sourceCode, subclassCount, matchingClassFeatures.size()));
        }

        return new ImportResult(imported, skipped, subclassesAdded);
    }

    /** Salvaguardas ("proficiency"), armaduras/armas e escolha de pericias
     * ("startingProficiencies"). Formatos do 5e.tools: string simples
     * ("light"), string com tag ("{@item shield|phb|shields}") ou objeto
     * ({"proficiency": "...", ...}) -- os tres viram texto legivel. */
    static void readProficiencies(Map<String, Object> classEntry, DndClass dndClass) {
        for (Object o : ClassJsonAnalyzer.nestedList(classEntry, "proficiency")) {
            if (o instanceof String s) dndClass.getSavingThrows().add(s.toLowerCase());
        }
        Map<String, Object> starting = ClassJsonAnalyzer.nestedMap(classEntry, "startingProficiencies");
        for (Object o : ClassJsonAnalyzer.nestedList(starting, "armor")) {
            String text = proficiencyText(o);
            if (text != null) dndClass.getArmorTraining().add(text.toLowerCase().contains("shield") ? "shield" : text.toLowerCase());
        }
        for (Object o : ClassJsonAnalyzer.nestedList(starting, "weapons")) {
            String text = proficiencyText(o);
            if (text != null) dndClass.getWeaponProficiencies().add(text);
        }
        for (Object o : ClassJsonAnalyzer.nestedList(starting, "skills")) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Object choose = m.get("choose");
            if (choose instanceof Map<?, ?> c) {
                if (c.get("from") instanceof List<?> from) {
                    for (Object skill : from) if (skill instanceof String s) dndClass.getSkillChoices().add(s.toLowerCase());
                }
                if (c.get("count") instanceof Number n) dndClass.setSkillChoiceCount(n.intValue());
            }
            Object any = m.get("any");
            if (any instanceof Number n && dndClass.getSkillChoices().isEmpty()) {
                dndClass.setSkillChoiceCount(n.intValue()); // "qualquer pericia" (Bardo)
            }
        }
    }

    private static String proficiencyText(Object o) {
        if (o instanceof String s) return EntriesRenderer.renderInline(s).trim();
        if (o instanceof Map<?, ?> m && m.get("proficiency") instanceof String s) return EntriesRenderer.renderInline(s).trim();
        return null;
    }

    private static String incompleteReason(
            Map<String, Object> classEntry, String sourceCode, List<Map<String, Object>> subclassRows) {
        String name = ClassJsonAnalyzer.text(classEntry, "name");
        if (ClassJsonAnalyzer.isBlank(name) || ClassJsonAnalyzer.isBlank(sourceCode)) {
            return "Nome e fonte da classe sao obrigatorios";
        }
        Map<String, Object> hitDie = ClassJsonAnalyzer.nestedMap(classEntry, "hd");
        if (ClassJsonAnalyzer.optionalInt(hitDie, "number").isEmpty()
                || ClassJsonAnalyzer.optionalInt(hitDie, "faces").isEmpty()) {
            return "Dado de vida (numero e faces) e obrigatorio";
        }
        if (ClassJsonAnalyzer.findSubclassUnlockLevel(classEntry, sourceCode, subclassRows) == null) {
            return "Nao consegui descobrir o nivel de desbloqueio de subclasse";
        }
        return null;
    }

    private static void addClassFeature(DndClass dndClass, Map<String, Object> featureRow) {
        ClassJsonAnalyzer.optionalInt(featureRow, "level").ifPresent(level -> {
            Feature feature = createFeature(featureRow, "class");
            dndClass.addFeature(feature, level, choiceGroup(featureRow, feature));
        });
    }

    private static int importSubclasses(
            DndClass dndClass,
            List<Map<String, Object>> subclassRows,
            List<Map<String, Object>> subclassFeatureRows) {
        int count = 0;
        for (Map<String, Object> subclassRow : subclassRows) {
            String name = ClassJsonAnalyzer.text(subclassRow, "name");
            String shortName = ClassJsonAnalyzer.text(subclassRow, "shortName");
            String sourceCode = ClassJsonAnalyzer.text(subclassRow, "source");
            if (ClassJsonAnalyzer.isBlank(name) || ClassJsonAnalyzer.isBlank(shortName)) {
                continue;
            }

            List<Map<String, Object>> matchingFeatures = matching(subclassFeatureRows, row ->
                    ClassJsonAnalyzer.matchesSubclassFeature(row, subclassRow));
            int unlockLevel = matchingFeatures.stream()
                    .map(row -> ClassJsonAnalyzer.optionalInt(row, "level"))
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .min(Integer::compareTo)
                    .orElse(dndClass.getSubclassUnlockLevel());

            Subclass subclass = dndClass.addSubclass(name, shortName, sourceCode, unlockLevel);
            for (Map<String, Object> featureRow : matchingFeatures) {
                ClassJsonAnalyzer.optionalInt(featureRow, "level").ifPresent(level -> {
                    Feature feature = createFeature(featureRow, "subclass");
                    subclass.addFeature(feature, level, choiceGroup(featureRow, feature));
                });
            }
            count++;
        }
        return count;
    }

    private static Feature createFeature(Map<String, Object> featureRow, String category) {
        String sourceCode = ClassJsonAnalyzer.text(featureRow, "source");
        Object entries = featureRow.get("entries");
        String description = entries == null ? null : GSON.toJson(entries);
        FeatureType type = ClassJsonAnalyzer.hasChoiceOptions(featureRow) ? FeatureType.CHOICE : FeatureType.AUTOMATIC;
        return new Feature(ClassJsonAnalyzer.text(featureRow, "name"), description, category, type, sourceCode);
    }

    private static String choiceGroup(Map<String, Object> featureRow, Feature feature) {
        if (feature.getType() != FeatureType.CHOICE) {
            return null;
        }
        String explicit = ClassJsonAnalyzer.text(featureRow, "choiceGroup");
        if (!ClassJsonAnalyzer.isBlank(explicit)) {
            return explicit;
        }
        return feature.getCategory() + ":" + feature.getName() + ":" + ClassJsonAnalyzer.text(featureRow, "level");
    }

    private static List<Map<String, Object>> matching(
            List<Map<String, Object>> rows, java.util.function.Predicate<Map<String, Object>> predicate) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (predicate.test(row)) {
                result.add(row);
            }
        }
        return result;
    }

    private static JsonArray arrayOrNull(JsonObject root, String field) {
        JsonElement value = root == null ? null : root.get(field);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    private static JsonArray arrayOrEmpty(JsonObject root, String field) {
        JsonArray array = arrayOrNull(root, field);
        return array != null ? array : new JsonArray();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(JsonElement element) {
        return (Map<String, Object>) GSON.fromJson(element, Object.class);
    }

    private static List<Map<String, Object>> toMapList(JsonArray array) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonElement el : array) {
            list.add(toMap(el));
        }
        return list;
    }
}
