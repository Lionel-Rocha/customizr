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
import top.customizr.system.dto.DndClassResponse;
import top.customizr.system.dto.ImportClassesResponse;
import top.customizr.system.model.DndClass;
import top.customizr.system.model.Feature;
import top.customizr.system.model.FeatureType;
import top.customizr.system.model.Source;
import top.customizr.system.model.Subclass;
import top.customizr.system.repository.DndClassRepository;
import top.customizr.system.repository.FeatureRepository;
import top.customizr.system.repository.SourceRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ClassService {

    private final DndClassRepository dndClassRepository;
    private final FeatureRepository featureRepository;
    private final SourceRepository sourceRepository;
    private final ObjectMapper objectMapper;

    public ClassService(
            DndClassRepository dndClassRepository,
            FeatureRepository featureRepository,
            SourceRepository sourceRepository,
            ObjectMapper objectMapper
    ) {
        this.dndClassRepository = dndClassRepository;
        this.featureRepository = featureRepository;
        this.sourceRepository = sourceRepository;
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

        for (JsonNode classEntry : classEntries) {
            String name = text(classEntry, "name");
            String sourceCode = text(classEntry, "source");
            String incompleteReason = getIncompleteReason(classEntry, sourceCode, subclassEntries);
            if (incompleteReason != null) {
                skipped.add(new ImportClassesResponse.SkippedClass(name, sourceCode, incompleteReason));
                continue;
            }

            if (dndClassRepository.findByClassNameAndSourceCode(name, sourceCode).isPresent()) {
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
            List<JsonNode> matchingSubclasses = matchingRows(
                    subclassEntries,
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

            int subclassCount = importSubclasses(
                    dndClass,
                    matchingSubclasses,
                    matchingSubclassFeatures,
                    subclassUnlockLevel
            );
            DndClass saved = dndClassRepository.save(dndClass);
            imported.add(new ImportClassesResponse.ImportedClass(
                    saved.getClassId(),
                    saved.getClassName(),
                    saved.getSourceCode(),
                    subclassCount,
                    matchingClassFeatures.size()
            ));
        }

        return new ImportClassesResponse(imported, skipped);
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

    private int importSubclasses(
            DndClass dndClass,
            List<JsonNode> subclassEntries,
            List<JsonNode> subclassFeatureEntries,
            int classUnlockLevel
    ) {
        int count = 0;
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
            count++;
        }
        return count;
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
