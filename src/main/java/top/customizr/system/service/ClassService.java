package top.customizr.system.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.core.type.TypeReference;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.AddSubclassFeatureResponse;
import top.customizr.system.dto.DndClassResponse;
import top.customizr.system.dto.ImportClassesResponse;
import top.customizr.system.model.DndClass;
import top.customizr.system.model.Feature;
import top.customizr.system.model.FeatureType;
import top.customizr.system.model.Spell;
import top.customizr.system.model.Source;
import top.customizr.system.model.Subclass;
import top.customizr.system.model.SubclassSpellGrantType;
import top.customizr.system.repository.DndClassRepository;
import top.customizr.system.repository.FeatureRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.repository.SpellRepository;
import top.customizr.system.repository.SubclassRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class ClassService {

    private record SubclassImportResult(int count, int spellLinksAdded) {}

    private final DndClassRepository dndClassRepository;
    private final FeatureRepository featureRepository;
    private final SpellRepository spellRepository;
    private final SourceRepository sourceRepository;
    private final SubclassRepository subclassRepository;
    private final ObjectMapper objectMapper;

    public ClassService(
            DndClassRepository dndClassRepository,
            FeatureRepository featureRepository,
            SpellRepository spellRepository,
            SourceRepository sourceRepository,
            SubclassRepository subclassRepository,
            ObjectMapper objectMapper
    ) {
        this.dndClassRepository = dndClassRepository;
        this.featureRepository = featureRepository;
        this.spellRepository = spellRepository;
        this.sourceRepository = sourceRepository;
        this.subclassRepository = subclassRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ImportClassesResponse importClasses(JsonNode payload) {
        JsonNode classEntries = payload == null ? null : payload.get("class");
        if (classEntries == null || !classEntries.isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected a JSON object with a class array");
        }

        JsonNode classFeatureEntries = arrayOrEmpty(payload, "classFeature");
        JsonNode subclassEntries = arrayOrEmpty(payload, "subclass");
        JsonNode subclassFeatureEntries = arrayOrEmpty(payload, "subclassFeature");
        List<ImportClassesResponse.ImportedClass> imported = new ArrayList<>();
        List<ImportClassesResponse.SkippedClass> skipped = new ArrayList<>();
        List<ImportClassesResponse.UnresolvedSubclassSpell> unresolvedSubclassSpells = new ArrayList<>();
        int subclassSpellLinksAdded = 0;

        for (JsonNode classEntry : classEntries) {
            String name = text(classEntry, "name");
            String sourceCode = text(classEntry, "source");
            String incompleteReason = getIncompleteReason(classEntry, sourceCode, subclassEntries);
            if (incompleteReason != null) {
                skipped.add(new ImportClassesResponse.SkippedClass(name, sourceCode, incompleteReason));
                continue;
            }

            List<JsonNode> matchingSubclasses = matchingRows(
                    subclassEntries,
                    row -> matchesClass(row, name, sourceCode)
            );
            Optional<DndClass> existingClass = dndClassRepository.findByClassNameAndSourceCode(name, sourceCode);
            if (existingClass.isPresent()) {
                subclassSpellLinksAdded += syncSubclassSpells(
                        existingClass.get(),
                        matchingSubclasses,
                        unresolvedSubclassSpells
                );
                skipped.add(new ImportClassesResponse.SkippedClass(name, sourceCode, "Already imported"));
                continue;
            }

            int hitDieNumber = classEntry.path("hd").path("number").asInt();
            int hitDieFaces = classEntry.path("hd").path("faces").asInt();
            int subclassUnlockLevel = findSubclassUnlockLevel(classEntry, sourceCode, subclassEntries);
            List<JsonNode> matchingClassFeatures = matchingRows(
                    classFeatureEntries,
                    row -> matchesClass(row, name, sourceCode)
            );
            List<JsonNode> matchingSubclassFeatures = matchingRows(
                    subclassFeatureEntries,
                    row -> matchesClass(row, name, sourceCode)
            );

            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.set("class", classEntry.deepCopy());
            metadata.set("classFeature", toArray(matchingClassFeatures));
            metadata.set("subclass", toArray(matchingSubclasses));
            metadata.set("subclassFeature", toArray(matchingSubclassFeatures));
            if (payload.has("_meta")) {
                metadata.set("_meta", payload.get("_meta").deepCopy());
            }

            Source source = findSource(sourceCode);
            DndClass dndClass = new DndClass(
                    name,
                    sourceCode,
                    hitDieNumber,
                    hitDieFaces,
                    subclassUnlockLevel,
                    source,
                    text(classEntry, "edition"),
                    optionalInt(classEntry, "page").orElse(null),
                    optionalBoolean(classEntry, "srd"),
                    text(classEntry, "spellcastingAbility"),
                    text(classEntry, "casterProgression"),
                    text(classEntry, "preparedSpells"),
                    text(classEntry, "preparedSpellsChange"),
                    toMap(metadata)
            );

            for (JsonNode featureEntry : matchingClassFeatures) {
                importClassFeature(dndClass, featureEntry);
            }

            SubclassImportResult subclassResult = importSubclasses(
                    dndClass,
                    matchingSubclasses,
                    matchingSubclassFeatures,
                    subclassUnlockLevel,
                    unresolvedSubclassSpells
            );
            DndClass saved = dndClassRepository.save(dndClass);
            imported.add(new ImportClassesResponse.ImportedClass(
                    saved.getClassId(),
                    saved.getClassName(),
                    saved.getSourceCode(),
                    subclassResult.count(),
                    matchingClassFeatures.size()
            ));
            subclassSpellLinksAdded += subclassResult.spellLinksAdded();
        }

        return new ImportClassesResponse(
                imported,
                skipped,
                unresolvedSubclassSpells,
                subclassSpellLinksAdded
        );
    }

    @Transactional
    public AddSubclassFeatureResponse addSubclassFeature(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw badRequest("A subclass feature object is required");
        }

        String className = text(payload, "className");
        String classSource = text(payload, "classSource");
        String subclassShortName = text(payload, "subclassShortName");
        String subclassSource = text(payload, "subclassSource");
        String featureName = text(payload, "name");
        JsonNode levelNode = payload.get("level");
        if (isBlank(className) || isBlank(classSource) || isBlank(subclassShortName)) {
            throw badRequest("className, classSource, and subclassShortName are required");
        }
        if (isBlank(featureName)) {
            throw badRequest("Feature name is required");
        }
        if (levelNode == null || !levelNode.canConvertToInt()
                || levelNode.asInt() < 1 || levelNode.asInt() > 20) {
            throw badRequest("Feature level must be between 1 and 20");
        }

        DndClass dndClass = dndClassRepository.findByClassNameAndSourceCode(className, classSource)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found"));
        List<Subclass> matchingSubclasses = dndClass.getSubclasses().stream()
                .filter(subclass -> subclassShortName.equals(subclass.getShortName()))
                .filter(subclass -> isBlank(subclassSource)
                        || subclassSource.equals(subclass.getSourceCode()))
                .toList();
        if (matchingSubclasses.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subclass not found");
        }
        if (matchingSubclasses.size() > 1) {
            throw badRequest("subclassSource is required to identify the subclass");
        }

        Subclass subclass = matchingSubclasses.getFirst();
        int level = levelNode.asInt();
        if (level < subclass.getUnlockLevel()) {
            throw badRequest("Feature level cannot be before the subclass unlock level");
        }

        Feature feature = createFeature(payload, "subclass");
        featureRepository.save(feature);
        String choiceGroup = choiceGroup(payload, feature);
        subclass.addFeature(feature, level, choiceGroup);
        subclassRepository.save(subclass);

        return new AddSubclassFeatureResponse(
                feature.getFeatureId(),
                subclass.getSubclassId(),
                feature.getName(),
                feature.getSourceCode(),
                level,
                feature.getDescription(),
                choiceGroup
        );
    }

    @Transactional
    public List<DndClassResponse> getAllClasses() {
        return dndClassRepository.findAll().stream()
                .map(DndClassResponse::from)
                .toList();
    }

    private String getIncompleteReason(JsonNode classEntry, String sourceCode, JsonNode subclassEntries) {
        String name = text(classEntry, "name");
        if (name == null || name.isBlank() || sourceCode == null || sourceCode.isBlank()) {
            return "Class name and source are required";
        }
        JsonNode hitDie = classEntry.get("hd");
        if (hitDie == null || !hitDie.path("number").canConvertToInt()
                || !hitDie.path("faces").canConvertToInt()
                || hitDie.path("number").asInt() <= 0 || hitDie.path("faces").asInt() <= 0) {
            return "Hit-die number and faces are required";
        }
        if (findSubclassUnlockLevel(classEntry, sourceCode, subclassEntries) == null) {
            return "Unable to determine subclass unlock level";
        }
        return null;
    }

    private Integer findSubclassUnlockLevel(JsonNode classEntry, String sourceCode, JsonNode subclassEntries) {
        JsonNode explicitLevel = classEntry.get("subclassUnlockLevel");
        if (explicitLevel != null && explicitLevel.canConvertToInt()
                && explicitLevel.asInt() >= 1 && explicitLevel.asInt() <= 20) {
            return explicitLevel.asInt();
        }

        List<Integer> levels = new ArrayList<>();
        JsonNode classFeatures = classEntry.path("classFeatures");
        if (classFeatures.isArray()) {
            for (JsonNode featureRef : classFeatures) {
                if (featureRef.path("gainSubclassFeature").asBoolean()) {
                    Integer level = referenceLevel(classFeatureReference(featureRef));
                    if (level != null) {
                        levels.add(level);
                    }
                }
            }
        }
        if (!levels.isEmpty()) {
            return levels.stream().min(Comparator.naturalOrder()).orElseThrow();
        }

        String className = text(classEntry, "name");
        for (JsonNode subclass : subclassEntries) {
            if (!matchesClass(subclass, className, sourceCode)) {
                continue;
            }
            JsonNode featureRefs = subclass.path("subclassFeatures");
            if (featureRefs.isArray()) {
                for (JsonNode featureRef : featureRefs) {
                    Integer level = referenceLevel(featureRef.asText());
                    if (level != null) {
                        levels.add(level);
                    }
                }
            }
        }
        return levels.stream().min(Comparator.naturalOrder()).orElse(null);
    }

    private SubclassImportResult importSubclasses(
            DndClass dndClass,
            List<JsonNode> subclassEntries,
            List<JsonNode> subclassFeatureEntries,
            int classUnlockLevel,
            List<ImportClassesResponse.UnresolvedSubclassSpell> unresolvedSpells
    ) {
        int count = 0;
        int spellLinksAdded = 0;
        for (JsonNode subclassEntry : subclassEntries) {
            String name = text(subclassEntry, "name");
            String shortName = text(subclassEntry, "shortName");
            String sourceCode = text(subclassEntry, "source");
            if (name == null || shortName == null) {
                continue;
            }

            List<JsonNode> featureEntries = subclassFeatureEntries.stream()
                    .filter(feature -> matchesSubclassFeature(feature, subclassEntry))
                    .toList();
            int unlockLevel = featureEntries.stream()
                    .map(feature -> optionalInt(feature, "level"))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .min(Comparator.naturalOrder())
                    .orElse(classUnlockLevel);

            ObjectNode subclassMetadata = objectMapper.createObjectNode();
            subclassMetadata.set("subclass", subclassEntry.deepCopy());
            subclassMetadata.set("subclassFeature", toArray(featureEntries));
            Source source = sourceCode == null ? null : findSource(sourceCode);
            Subclass subclass = dndClass.addSubclass(
                    name,
                    shortName,
                    sourceCode,
                    unlockLevel,
                    source,
                    toMap(subclassMetadata)
            );

            for (JsonNode featureEntry : featureEntries) {
                Feature feature = createFeature(featureEntry, "subclass");
                featureRepository.save(feature);
                subclass.addFeature(
                        feature,
                        featureEntry.path("level").asInt(),
                        choiceGroup(featureEntry, feature)
                );
            }
            spellLinksAdded += importSubclassSpells(
                    dndClass, subclass, subclassEntry, unresolvedSpells, null
            );
            count++;
        }
        return new SubclassImportResult(count, spellLinksAdded);
    }

    private int syncSubclassSpells(
            DndClass dndClass,
            List<JsonNode> subclassEntries,
            List<ImportClassesResponse.UnresolvedSubclassSpell> unresolvedSpells
    ) {
        int spellLinksAdded = 0;
        for (JsonNode subclassEntry : subclassEntries) {
            String shortName = text(subclassEntry, "shortName");
            String sourceCode = text(subclassEntry, "source");
            List<Subclass> matchingSubclasses = dndClass.getSubclasses().stream()
                    .filter(subclass -> Objects.equals(subclass.getShortName(), shortName))
                    .filter(subclass -> Objects.equals(subclass.getSourceCode(), sourceCode))
                    .toList();

            if (matchingSubclasses.size() == 1) {
                spellLinksAdded += importSubclassSpells(
                        dndClass,
                        matchingSubclasses.getFirst(),
                        subclassEntry,
                        unresolvedSpells,
                        null
                );
            } else {
                String reason = matchingSubclasses.isEmpty()
                        ? "Subclass not found on imported class"
                        : "Multiple matching subclasses found";
                spellLinksAdded += importSubclassSpells(
                        dndClass, null, subclassEntry, unresolvedSpells, reason
                );
            }
        }
        return spellLinksAdded;
    }

    private int importSubclassSpells(
            DndClass dndClass,
            Subclass subclass,
            JsonNode subclassEntry,
            List<ImportClassesResponse.UnresolvedSubclassSpell> unresolvedSpells,
            String subclassError
    ) {
        JsonNode additionalSpells = subclassEntry.path("additionalSpells");
        if (!additionalSpells.isArray()) {
            return 0;
        }

        int spellLinksAdded = 0;
        for (int setIndex = 0; setIndex < additionalSpells.size(); setIndex++) {
            JsonNode spellSet = additionalSpells.get(setIndex);
            spellLinksAdded += importSubclassSpellCategory(
                    dndClass, subclass, subclassEntry, spellSet.get("prepared"),
                    SubclassSpellGrantType.PREPARED, setIndex, subclassError, unresolvedSpells
            );
            spellLinksAdded += importSubclassSpellCategory(
                    dndClass, subclass, subclassEntry, spellSet.get("innate"),
                    SubclassSpellGrantType.INNATE, setIndex, subclassError, unresolvedSpells
            );
        }
        return spellLinksAdded;
    }

    private int importSubclassSpellCategory(
            DndClass dndClass,
            Subclass subclass,
            JsonNode subclassEntry,
            JsonNode grants,
            SubclassSpellGrantType grantType,
            int setIndex,
            String subclassError,
            List<ImportClassesResponse.UnresolvedSubclassSpell> unresolvedSpells
    ) {
        if (grants == null || grants.isNull()) {
            return 0;
        }
        return collectSubclassSpellReferences(
                dndClass, subclass, subclassEntry, grants, grantType, setIndex,
                null, true, false, new ArrayList<>(), subclassError, unresolvedSpells
        );
    }

    private int collectSubclassSpellReferences(
            DndClass dndClass,
            Subclass subclass,
            JsonNode subclassEntry,
            JsonNode node,
            SubclassSpellGrantType grantType,
            int setIndex,
            Integer grantedAtLevel,
            boolean levelKey,
            boolean spellListEntry,
            List<String> path,
            String subclassError,
            List<ImportClassesResponse.UnresolvedSubclassSpell> unresolvedSpells
    ) {
        int spellLinksAdded = 0;
        if (node.isObject()) {
            for (var field : node.properties()) {
                path.add(field.getKey());
                Integer childLevel = levelKey ? parseLevel(field.getKey()) : grantedAtLevel;
                spellLinksAdded += collectSubclassSpellReferences(
                        dndClass, subclass, subclassEntry, field.getValue(), grantType, setIndex,
                        childLevel, false, spellListEntry, path, subclassError, unresolvedSpells
                );
                path.removeLast();
            }
            return spellLinksAdded;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                spellLinksAdded += collectSubclassSpellReferences(
                        dndClass, subclass, subclassEntry, child, grantType, setIndex,
                        grantedAtLevel, false, true, path, subclassError, unresolvedSpells
                );
            }
            return spellLinksAdded;
        }
        if (!node.isTextual() || !spellListEntry) {
            return 0;
        }
        if (subclass == null) {
            unresolvedSpells.add(unresolvedSpell(
                    dndClass, subclassEntry, node.asText().trim(), subclassError
            ));
            return 0;
        }

        String reference = node.asText().trim();
        int separator = reference.lastIndexOf('|');
        if (separator <= 0 || separator == reference.length() - 1) {
            unresolvedSpells.add(unresolvedSpell(
                    dndClass,
                    subclassEntry,
                    reference,
                    "Expected a spell reference in name|source format"
            ));
            return 0;
        }

        String spellName = reference.substring(0, separator).trim();
        String sourceCode = reference.substring(separator + 1).trim();
        if (spellName.isEmpty() || sourceCode.isEmpty()) {
            unresolvedSpells.add(unresolvedSpell(
                    dndClass, subclassEntry, reference, "Spell name and source are required"
            ));
            return 0;
        }

        Spell spell = spellRepository.findByNameIgnoreCaseAndSource_CodeIgnoreCase(spellName, sourceCode)
                .orElse(null);
        if (spell == null) {
            unresolvedSpells.add(unresolvedSpell(
                    dndClass, subclassEntry, reference, "No matching spell record"
            ));
            return 0;
        }

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("reference", reference);
        metadata.put("additionalSpellSet", setIndex);
        ArrayNode pathNode = metadata.putArray("path");
        path.forEach(pathNode::add);
        return subclass.addSpellIfMissing(
                spell,
                grantType,
                grantedAtLevel,
                setIndex,
                path,
                toMap(metadata)
        ) ? 1 : 0;
    }

    private ImportClassesResponse.UnresolvedSubclassSpell unresolvedSpell(
            DndClass dndClass,
            JsonNode subclassEntry,
            String reference,
            String reason
    ) {
        return new ImportClassesResponse.UnresolvedSubclassSpell(
                dndClass.getClassName(),
                dndClass.getSourceCode(),
                text(subclassEntry, "name"),
                text(subclassEntry, "source"),
                reference,
                reason
        );
    }

    private Integer parseLevel(String value) {
        try {
            int level = Integer.parseInt(value);
            return level >= 1 && level <= 20 ? level : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void importClassFeature(DndClass dndClass, JsonNode featureEntry) {
        JsonNode levelNode = featureEntry.get("level");
        if (levelNode == null || !levelNode.canConvertToInt()) {
            return;
        }
        Feature feature = createFeature(featureEntry, "class");
        featureRepository.save(feature);
        dndClass.addFeature(
                feature,
                levelNode.asInt(),
                choiceGroup(featureEntry, feature)
        );
    }

    private Feature createFeature(JsonNode featureEntry, String category) {
        String sourceCode = text(featureEntry, "source");
        Source source = sourceCode == null ? null : findSource(sourceCode);
        JsonNode entries = featureEntry.get("entries");
        String description = entries == null || entries.isNull() ? null : entries.toString();
        FeatureType type = hasChoiceOptions(featureEntry) ? FeatureType.CHOICE : FeatureType.AUTOMATIC;
        return new Feature(
                text(featureEntry, "name"),
                description,
                category,
                type,
                source,
                sourceCode,
                toMap(featureEntry)
        );
    }

    private String choiceGroup(JsonNode featureEntry, Feature feature) {
        if (feature.getType() != FeatureType.CHOICE) {
            return null;
        }
        String explicitGroup = text(featureEntry, "choiceGroup");
        if (explicitGroup != null && !explicitGroup.isBlank()) {
            return explicitGroup;
        }
        return feature.getCategory() + ":" + feature.getName() + ":" + featureEntry.path("level").asInt();
    }

    private boolean hasChoiceOptions(JsonNode node) {
        if (node.isObject()) {
            if ("options".equals(text(node, "type")) || node.has("choose")) {
                return true;
            }
            for (var field : node.properties()) {
                if (hasChoiceOptions(field.getValue())) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                if (hasChoiceOptions(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesClass(JsonNode row, String className, String sourceCode) {
        if (!className.equals(text(row, "className"))) {
            return false;
        }
        String rowSource = text(row, "classSource");
        return rowSource == null || rowSource.isBlank() || rowSource.equals(sourceCode);
    }

    private boolean matchesSubclassFeature(JsonNode feature, JsonNode subclass) {
        if (!matchesClass(feature, text(subclass, "className"), text(subclass, "classSource"))) {
            return false;
        }
        if (!text(subclass, "shortName").equals(text(feature, "subclassShortName"))) {
            return false;
        }
        String subclassSource = text(subclass, "source");
        String featureSubclassSource = text(feature, "subclassSource");
        return featureSubclassSource == null || featureSubclassSource.isBlank()
                || featureSubclassSource.equals(subclassSource);
    }

    private List<JsonNode> matchingRows(JsonNode rows, java.util.function.Predicate<JsonNode> predicate) {
        List<JsonNode> matches = new ArrayList<>();
        if (rows.isArray()) {
            for (JsonNode row : rows) {
                if (predicate.test(row)) {
                    matches.add(row);
                }
            }
        }
        return matches;
    }

    private ArrayNode toArray(List<JsonNode> nodes) {
        ArrayNode array = objectMapper.createArrayNode();
        nodes.forEach(node -> array.add(node.deepCopy()));
        return array;
    }

    private java.util.Map<String, Object> toMap(JsonNode node) {
        return objectMapper.convertValue(node, new TypeReference<>() {});
    }

    private String classFeatureReference(JsonNode featureRef) {
        if (featureRef.isTextual()) {
            return featureRef.asText();
        }
        return text(featureRef, "classFeature");
    }

    private Integer referenceLevel(String reference) {
        if (reference == null) {
            return null;
        }
        String[] parts = reference.split("\\|", -1);
        for (int index = parts.length - 1; index >= 0; index--) {
            try {
                int level = Integer.parseInt(parts[index]);
                if (level >= 1 && level <= 20) {
                    return level;
                }
            } catch (NumberFormatException ignored) {
                // Other reference segments may contain source identifiers.
            }
        }
        return null;
    }

    private Source findSource(String sourceCode) {
        return sourceRepository.getSourceByCode(sourceCode).orElse(null);
    }

    private JsonNode arrayOrEmpty(JsonNode root, String field) {
        JsonNode value = root.get(field);
        return value != null && value.isArray() ? value : objectMapper.createArrayNode();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private Optional<Integer> optionalInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.canConvertToInt()
                ? Optional.of(value.asInt())
                : Optional.empty();
    }

    private Boolean optionalBoolean(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asBoolean();
    }
}
