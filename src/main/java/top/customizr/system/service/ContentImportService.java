package top.customizr.system.service;

import top.customizr.system.dto.BackgroundResponse;
import top.customizr.system.dto.FeatResponse;
import top.customizr.system.dto.SpeciesResponse;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import top.customizr.system.dto.ContentImportResponse;
import top.customizr.system.model.Background;
import top.customizr.system.model.EntriesRenderer;
import top.customizr.system.model.FeatDefinition;
import top.customizr.system.model.Source;
import top.customizr.system.model.Species;
import top.customizr.system.repository.BackgroundRepository;
import top.customizr.system.repository.FeatDefinitionRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.repository.SpeciesRepository;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Importa especies ("race"), antecedentes ("background") e talentos ("feat") dos arquivos do
 * 5etools, do mesmo jeito do /item/update-from-dump: cada entrada e identificada por nome + fonte;
 * se ja existe, atualiza; fonte que nao esta cadastrada e ignorada (com o motivo).
 */
@Service
public class ContentImportService {

    private static final Map<String, String> SIZES = Map.of(
            "T", "Tiny", "S", "Small", "M", "Medium", "L", "Large", "H", "Huge", "G", "Gargantuan");
    private static final Map<String, String> FEAT_CATEGORIES = Map.of(
            "O", "Origin", "G", "General", "FS", "Fighting Style", "EB", "Epic Boon");

    private final SpeciesRepository speciesRepository;
    private final BackgroundRepository backgroundRepository;
    private final FeatDefinitionRepository featRepository;
    private final SourceRepository sourceRepository;
    private final tools.jackson.databind.ObjectMapper objectMapper;
    private final top.customizr.system.repository.FeatureRepository featureRepository;

    public ContentImportService(
            SpeciesRepository speciesRepository,
            BackgroundRepository backgroundRepository,
            FeatDefinitionRepository featRepository,
            SourceRepository sourceRepository,
            tools.jackson.databind.ObjectMapper objectMapper,
            top.customizr.system.repository.FeatureRepository featureRepository
    ) {
        this.featureRepository = featureRepository;
        this.speciesRepository = speciesRepository;
        this.backgroundRepository = backgroundRepository;
        this.featRepository = featRepository;
        this.sourceRepository = sourceRepository;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------ importacao

    /** Arquivo races.json do 5etools (array "race"; tambem aceita "species"). */
    @Transactional
    public ContentImportResponse importSpecies(JsonNode payload) {
        JsonNode entries = array(payload, "race", "species");
        Result result = new Result();
        for (JsonNode entry : entries) {
            Source source = sourceFor(entry, result);
            if (source == null) continue;
            String name = entry.path("name").asText().trim();
            Species species = speciesRepository.findByNameAndSource_Id(name, source.getId()).orElse(null);
            boolean created = species == null;
            if (created) species = new Species(name, source);
            species.update(size(entry.path("size")), speed(entry.path("speed")),
                    EntriesRenderer.abilityBonuses(entry.path("ability")));
            species.updateSourceData(rawEntries(entry.path("entries")), EntriesRenderer.render(entry.path("entries")),
                    page(entry), toMap(entry));
            if (species.getTraits().isEmpty()) {
                species.replaceTraits(features(entry.path("entries"), "species", source));
            }
            species = speciesRepository.save(species);
            result.add(created, species.getId(), name, source.getCode());
        }
        return result.response();
    }

    /** Arquivo backgrounds.json do 5etools (array "background"). */
    @Transactional
    public ContentImportResponse importBackgrounds(JsonNode payload) {
        JsonNode entries = array(payload, "background");
        Result result = new Result();
        for (JsonNode entry : entries) {
            Source source = sourceFor(entry, result);
            if (source == null) continue;
            String name = entry.path("name").asText().trim();
            Background background = backgroundRepository.findByNameAndSource_Id(name, source.getId()).orElse(null);
            boolean created = background == null;
            if (created) background = new Background(name, source);
            background.update(EntriesRenderer.abilityBonuses(entry.path("ability")),
                    keysOf(entry.path("skillProficiencies")), firstKey(entry.path("feats")));
            background.updateSourceData(rawEntries(entry.path("entries")), EntriesRenderer.render(entry.path("entries")),
                    page(entry), toMap(entry));
            if (background.getFeatures().isEmpty()) {
                background.replaceFeatures(features(entry.path("entries"), "background", source));
            }
            background = backgroundRepository.save(background);
            result.add(created, background.getId(), name, source.getCode());
        }
        return result.response();
    }

    /** Arquivo feats.json do 5etools (array "feat"). */
    @Transactional
    public ContentImportResponse importFeats(JsonNode payload) {
        JsonNode entries = array(payload, "feat");
        Result result = new Result();
        for (JsonNode entry : entries) {
            Source source = sourceFor(entry, result);
            if (source == null) continue;
            String name = entry.path("name").asText().trim();
            FeatDefinition feat = featRepository.findByNameAndSource_Id(name, source.getId()).orElse(null);
            boolean created = feat == null;
            if (created) feat = new FeatDefinition(name, source);
            String category = entry.path("category").asText(null);
            feat.update(category == null ? null : FEAT_CATEGORIES.getOrDefault(category, category),
                    prerequisite(entry.path("prerequisite")), entry.path("repeatable").asBoolean(false));
            feat.updateSourceData(rawEntries(entry.path("entries")), EntriesRenderer.render(entry.path("entries")),
                    page(entry), toMap(entry));
            feat = featRepository.save(feat);
            result.add(created, feat.getId(), name, source.getCode());
        }
        return result.response();
    }

    // ------------------------------------------------------------------ consulta

    public List<SpeciesResponse> species(String name) {
        return (name == null || name.isBlank() ? speciesRepository.findAllByOrderByName()
                : speciesRepository.findByNameContainingIgnoreCaseOrderByName(name.trim()))
                .stream().map(SpeciesResponse::from).toList();
    }

    public List<BackgroundResponse> backgrounds(String name) {
        return (name == null || name.isBlank() ? backgroundRepository.findAllByOrderByName()
                : backgroundRepository.findByNameContainingIgnoreCaseOrderByName(name.trim()))
                .stream().map(BackgroundResponse::from).toList();
    }

    public List<FeatResponse> feats(String name) {
        return (name == null || name.isBlank() ? featRepository.findAllByOrderByName()
                : featRepository.findByNameContainingIgnoreCaseOrderByName(name.trim()))
                .stream().map(FeatResponse::from).toList();
    }

    // ------------------------------------------------------------------ leitura do 5etools

    /**
     * Cada bloco com nome dos "entries" vira um {@link top.customizr.system.model.Feature}, como as features
     * de classe no ClassService: descricao crua (com tags), categoria, tipo CHOICE quando tem opcoes
     * ("options"/"choose") e a entrada original em metadata. Listas com itens nomeados (antecedentes de
     * 2024: "Ability Scores:", "Feat:"...) viram um Feature por item.
     */
    private List<top.customizr.system.model.Feature> features(JsonNode entries, String category, Source source) {
        List<top.customizr.system.model.Feature> out = new ArrayList<>();
        for (JsonNode node : entries) {
            if (!node.isObject()) continue;
            if (node.has("name")) {
                out.add(feature(node, category, source));
            } else if ("list".equals(node.path("type").asText())) {
                for (JsonNode item : node.path("items")) {
                    if (item.isObject() && item.has("name")) out.add(feature(item, category, source));
                }
            }
        }
        return out;
    }

    private top.customizr.system.model.Feature feature(JsonNode node, String category, Source source) {
        JsonNode body = node.has("entries") ? node.path("entries") : node.path("entry");
        String name = EntriesRenderer.renderText(node.path("name").asText()).replaceAll("[:.]\\s*$", "").trim();
        top.customizr.system.model.Feature feature = new top.customizr.system.model.Feature(
                name,
                rawEntries(body),
                category,
                hasChoiceOptions(node) ? top.customizr.system.model.FeatureType.CHOICE
                        : top.customizr.system.model.FeatureType.AUTOMATIC,
                source,
                source.getCode(),
                toMap(node));
        return featureRepository.save(feature);
    }

    /** Mesma regra do ClassService.hasChoiceOptions. */
    private boolean hasChoiceOptions(JsonNode node) {
        if (node.isObject()) {
            if ("options".equals(node.path("type").asText()) || node.has("choose")) return true;
            for (var field : node.properties()) if (hasChoiceOptions(field.getValue())) return true;
        } else if (node.isArray()) {
            for (JsonNode child : node) if (hasChoiceOptions(child)) return true;
        }
        return false;
    }

    /** Entrada original como Map (vai pra coluna JSON metadata, como no ClassService). */
    private java.util.Map<String, Object> toMap(JsonNode node) {
        return objectMapper.convertValue(node, new tools.jackson.core.type.TypeReference<>() {});
    }

    /** "entries" cru em JSON (com as tags {@...}), igual ao Feature.description do ClassService. */
    private static String rawEntries(JsonNode entries) {
        return entries == null || entries.isNull() || entries.isMissingNode() ? null : entries.toString();
    }

    private static Integer page(JsonNode entry) {
        return entry.path("page").canConvertToInt() ? entry.path("page").asInt() : null;
    }

    private JsonNode array(JsonNode payload, String... keys) {
        if (payload != null) {
            for (String key : keys) {
                if (payload.path(key).isArray()) return payload.path(key);
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Expected a JSON object with a \"" + keys[0] + "\" array (5etools format)");
    }

    private Source sourceFor(JsonNode entry, Result result) {
        String name = entry.path("name").asText(null);
        String code = entry.path("source").asText(null);
        if (name == null || name.isBlank()) {
            result.skip(null, code, "Name is required");
            return null;
        }
        if (entry.has("_copy")) {
            result.skip(name, code, "Copies of other entries (_copy) are not supported yet");
            return null;
        }
        if (code == null || code.isBlank()) {
            result.skip(name, null, "Source is required");
            return null;
        }
        Source source = sourceRepository.getSourceByCode(code.trim().toUpperCase(Locale.ROOT)).orElse(null);
        if (source == null) {
            result.skip(name, code, "Source is not configured in the database");
        }
        return source;
    }

    private static String size(JsonNode size) {
        if (size.isArray()) {
            List<String> sizes = new ArrayList<>();
            for (JsonNode s : size) sizes.add(SIZES.getOrDefault(s.asText(), s.asText()));
            return sizes.isEmpty() ? null : String.join(" or ", sizes);
        }
        return size.isMissingNode() || size.isNull() ? null : SIZES.getOrDefault(size.asText(), size.asText());
    }

    private static Integer speed(JsonNode speed) {
        if (speed.isNumber()) return speed.asInt();
        if (speed.path("walk").isNumber()) return speed.path("walk").asInt();
        return null;
    }

    /** [{"insight": true, "religion": true}] -> "Insight, Religion". */
    private static String keysOf(JsonNode list) {
        if (!list.isArray() || list.isEmpty()) return null;
        List<String> names = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = list.get(0).properties().iterator();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> f = fields.next();
            if (f.getValue().isBoolean() && f.getValue().asBoolean()) names.add(capitalize(f.getKey()));
        }
        return names.isEmpty() ? null : String.join(", ", names);
    }

    /** [{"magic initiate; cleric|xphb": true}] -> "Magic Initiate; Cleric". */
    private static String firstKey(JsonNode list) {
        if (!list.isArray() || list.isEmpty()) return null;
        Iterator<String> names = list.get(0).propertyNames().iterator();
        if (!names.hasNext()) return null;
        String key = names.next();
        int bar = key.indexOf('|');
        return capitalize(bar < 0 ? key : key.substring(0, bar));
    }

    /** Pre-requisitos do 5etools em texto: nivel, atributo, especie, talento, conjuracao... */
    private static String prerequisite(JsonNode prerequisites) {
        if (!prerequisites.isArray() || prerequisites.isEmpty()) return null;
        List<String> options = new ArrayList<>();
        for (JsonNode p : prerequisites) {
            List<String> parts = new ArrayList<>();
            if (p.has("level")) {
                JsonNode level = p.path("level");
                parts.add("Level " + (level.isNumber() ? level.asInt() : level.path("level").asInt()));
            }
            for (JsonNode ability : p.path("ability")) {
                List<String> any = new ArrayList<>();
                ability.properties().forEach(f -> any.add(f.getKey().toUpperCase() + " " + f.getValue().asInt() + "+"));
                parts.add(String.join(" or ", any));
            }
            for (JsonNode race : p.path("race")) parts.add(capitalize(race.path("name").asText()));
            for (JsonNode feat : p.path("feat")) {
                String f = feat.asText();
                parts.add(capitalize(f.contains("|") ? f.substring(0, f.indexOf('|')) : f));
            }
            if (p.has("spellcasting") || p.has("spellcasting2020") || p.has("spellcastingFeature")) {
                parts.add("Spellcasting or Pact Magic");
            }
            if (p.has("other")) parts.add(p.path("other").asText());
            if (!parts.isEmpty()) options.add(String.join(", ", parts));
        }
        return options.isEmpty() ? null : String.join(" or ", options);
    }

    private static String capitalize(String s) {
        StringBuilder sb = new StringBuilder();
        boolean up = true;
        for (char c : s.toCharArray()) {
            sb.append(up ? Character.toUpperCase(c) : c);
            up = c == ' ' || c == '(' || c == '-' || c == ';';
        }
        return sb.toString();
    }

    private static final class Result {
        final List<ContentImportResponse.Processed> created = new ArrayList<>();
        final List<ContentImportResponse.Processed> updated = new ArrayList<>();
        final List<ContentImportResponse.Skipped> skipped = new ArrayList<>();

        void add(boolean isNew, java.util.UUID id, String name, String source) {
            (isNew ? created : updated).add(new ContentImportResponse.Processed(id, name, source));
        }

        void skip(String name, String source, String reason) {
            skipped.add(new ContentImportResponse.Skipped(name, source, reason));
        }

        ContentImportResponse response() {
            return new ContentImportResponse(created, updated, skipped);
        }
    }
}
