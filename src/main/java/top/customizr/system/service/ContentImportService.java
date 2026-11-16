package top.customizr.system.service;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import top.customizr.system.dto.ContentImportResponse;
import top.customizr.system.dto.ContentResponse;
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

    public ContentImportService(
            SpeciesRepository speciesRepository,
            BackgroundRepository backgroundRepository,
            FeatDefinitionRepository featRepository,
            SourceRepository sourceRepository
    ) {
        this.speciesRepository = speciesRepository;
        this.backgroundRepository = backgroundRepository;
        this.featRepository = featRepository;
        this.sourceRepository = sourceRepository;
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
                    EntriesRenderer.abilityBonuses(entry.path("ability")), EntriesRenderer.render(entry.path("entries")));
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
                    keysOf(entry.path("skillProficiencies")), firstKey(entry.path("feats")),
                    EntriesRenderer.render(entry.path("entries")));
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
                    prerequisite(entry.path("prerequisite")), entry.path("repeatable").asBoolean(false),
                    EntriesRenderer.render(entry.path("entries")));
            feat = featRepository.save(feat);
            result.add(created, feat.getId(), name, source.getCode());
        }
        return result.response();
    }

    // ------------------------------------------------------------------ consulta

    public List<ContentResponse.SpeciesInfo> species(String name) {
        return (name == null || name.isBlank() ? speciesRepository.findAllByOrderByName()
                : speciesRepository.findByNameContainingIgnoreCaseOrderByName(name.trim()))
                .stream().map(ContentResponse.SpeciesInfo::from).toList();
    }

    public List<ContentResponse.BackgroundInfo> backgrounds(String name) {
        return (name == null || name.isBlank() ? backgroundRepository.findAllByOrderByName()
                : backgroundRepository.findByNameContainingIgnoreCaseOrderByName(name.trim()))
                .stream().map(ContentResponse.BackgroundInfo::from).toList();
    }

    public List<ContentResponse.FeatInfo> feats(String name) {
        return (name == null || name.isBlank() ? featRepository.findAllByOrderByName()
                : featRepository.findByNameContainingIgnoreCaseOrderByName(name.trim()))
                .stream().map(ContentResponse.FeatInfo::from).toList();
    }

    // ------------------------------------------------------------------ leitura do 5etools

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
