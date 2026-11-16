package top.customizr.system.service;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import top.customizr.system.dto.ContentImportResponse;
import top.customizr.system.model.Components;
import top.customizr.system.model.DndClass;
import top.customizr.system.model.Duration;
import top.customizr.system.model.DurationUnit;
import top.customizr.system.model.EntriesRenderer;
import top.customizr.system.model.Source;
import top.customizr.system.model.Spell;
import top.customizr.system.model.SpellLevel;
import top.customizr.system.model.SpellSchool;
import top.customizr.system.model.Time;
import top.customizr.system.repository.DndClassRepository;
import top.customizr.system.repository.SourceRepository;
import top.customizr.system.repository.SpellRepository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Importa magias dos arquivos do 5etools ({@code spells-xphb.json} etc.) para o catalogo de
 * {@link Spell}, criando ou atualizando por nome + fonte. O que nao cabe nos campos (alcance que nao
 * e em pes, area, componente material, ritual, duracao especial) vai no comeco da descricao.
 * Listas de classe: campo "classes.fromClassList" da propria magia, ou o arquivo de lookup do 5etools
 * em {@link #importClassLists}.
 */
@Service
public class SpellImportService {

    private static final Map<String, SpellSchool> SCHOOLS = Map.of(
            "A", SpellSchool.Abjuration, "C", SpellSchool.Conjuration, "D", SpellSchool.Divination,
            "E", SpellSchool.Enchantment, "V", SpellSchool.Evocation, "I", SpellSchool.Illusion,
            "N", SpellSchool.Necromancy, "T", SpellSchool.Transmutation);

    private final SpellRepository spellRepository;
    private final SourceRepository sourceRepository;
    private final DndClassRepository dndClassRepository;

    public SpellImportService(SpellRepository spellRepository, SourceRepository sourceRepository,
                              DndClassRepository dndClassRepository) {
        this.spellRepository = spellRepository;
        this.sourceRepository = sourceRepository;
        this.dndClassRepository = dndClassRepository;
    }

    @Transactional
    public ContentImportResponse importSpells(JsonNode payload) {
        if (payload == null || !payload.path("spell").isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected a JSON object with a \"spell\" array (5etools format)");
        }
        List<ContentImportResponse.Processed> created = new ArrayList<>();
        List<ContentImportResponse.Processed> updated = new ArrayList<>();
        List<ContentImportResponse.Skipped> skipped = new ArrayList<>();
        for (JsonNode entry : payload.path("spell")) {
            String name = entry.path("name").asText("").trim();
            String code = entry.path("source").asText("").trim().toUpperCase(Locale.ROOT);
            if (name.isEmpty() || code.isEmpty()) {
                skipped.add(new ContentImportResponse.Skipped(name, code, "Name and source are required"));
                continue;
            }
            Source source = sourceRepository.getSourceByCode(code).orElse(null);
            if (source == null) {
                skipped.add(new ContentImportResponse.Skipped(name, code, "Source is not configured in the database"));
                continue;
            }
            try {
                ParsedSpell p = parse(entry);
                Spell spell = spellRepository.findByNameAndSource_Id(name, source.getId()).orElse(null);
                boolean isNew = spell == null;
                if (isNew) {
                    spell = new Spell(name, p.school, p.level, p.time, p.rangeFt, p.components, p.duration, source, p.description);
                } else {
                    spell.update(name, p.school, p.level, p.time, p.rangeFt, p.components, p.duration, source, p.description);
                }
                spell = spellRepository.save(spell);
                linkClasses(spell, entry.path("classes").path("fromClassList"));
                (isNew ? created : updated).add(new ContentImportResponse.Processed(spell.getSpellId(), name, code));
            } catch (IllegalArgumentException ex) {
                skipped.add(new ContentImportResponse.Skipped(name, code, ex.getMessage()));
            }
        }
        return new ContentImportResponse(created, updated, skipped);
    }

    /**
     * Arquivo de lookup do 5etools (gendata-spell-source-lookup.json):
     * {"xphb": {"fireball": {"class": {"XPHB": {"Sorcerer": true, "Wizard": true}}}}}.
     * Liga cada magia as classes ja importadas (/class/import). Devolve quantas ligacoes fez.
     */
    @Transactional
    public int importClassLists(JsonNode lookup) {
        if (lookup == null || !lookup.isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected the 5etools spell source lookup object");
        }
        int links = 0;
        for (Map.Entry<String, JsonNode> bySource : lookup.properties()) {
            for (Map.Entry<String, JsonNode> bySpell : bySource.getValue().properties()) {
                Spell spell = spellRepository
                        .findByNameIgnoreCaseAndSource_CodeIgnoreCase(bySpell.getKey(), bySource.getKey()).orElse(null);
                if (spell == null) continue;
                for (Map.Entry<String, JsonNode> classSource : bySpell.getValue().path("class").properties()) {
                    for (Map.Entry<String, JsonNode> cls : classSource.getValue().properties()) {
                        if (link(spell, cls.getKey(), classSource.getKey())) links++;
                    }
                }
            }
        }
        return links;
    }

    private void linkClasses(Spell spell, JsonNode fromClassList) {
        for (JsonNode c : fromClassList) {
            link(spell, c.path("name").asText(), c.path("source").asText());
        }
    }

    private boolean link(Spell spell, String className, String sourceCode) {
        DndClass dndClass = dndClassRepository.findByClassNameAndSourceCode(className, sourceCode.toUpperCase(Locale.ROOT))
                .orElse(null);
        if (dndClass == null || dndClass.getAvailableSpells().contains(spell)) {
            return false;
        }
        dndClass.addAvailableSpell(spell);
        return true;
    }

    // ------------------------------------------------------------------ leitura

    private record ParsedSpell(SpellSchool school, SpellLevel level, Time time, int rangeFt,
                               Set<Components> components, Duration duration, String description) {}

    static ParsedSpell parse(JsonNode e) {
        SpellSchool school = SCHOOLS.get(e.path("school").asText());
        if (school == null) throw new IllegalArgumentException("Unknown school: " + e.path("school").asText());
        int lv = e.path("level").asInt(-1);
        if (lv < 0 || lv > 9) throw new IllegalArgumentException("Invalid level");
        SpellLevel level = SpellLevel.values()[lv];

        List<String> header = new ArrayList<>();
        JsonNode t = e.path("time").path(0);
        Time time = time(t.path("number").asInt(1), t.path("unit").asText("action"));
        if (time == null) {
            time = Time.Action;
            header.add("Casting time: " + t.path("number").asInt(1) + " " + t.path("unit").asText());
        }
        if (t.has("condition")) header.add("Reaction trigger: " + EntriesRenderer.render(t.path("condition")));
        if (e.path("meta").path("ritual").asBoolean(false)) header.add("Ritual.");

        JsonNode range = e.path("range");
        String rangeType = range.path("type").asText("point");
        String distanceType = range.path("distance").path("type").asText("");
        int amount = range.path("distance").path("amount").asInt(0);
        int rangeFt = 0;
        switch (distanceType) {
            case "feet" -> rangeFt = amount;
            case "miles" -> rangeFt = amount * 5280;
            default -> { }
        }
        if (!"point".equals(rangeType)) {
            header.add("Range: Self (" + amount + "-" + ("miles".equals(distanceType) ? "mile" : "foot") + " " + rangeType + ").");
            rangeFt = 0;
        } else if (!"feet".equals(distanceType)) {
            header.add("Range: " + capitalize(distanceType) + ("miles".equals(distanceType) ? " (" + amount + ")" : "") + ".");
        }

        Set<Components> components = new LinkedHashSet<>();
        JsonNode c = e.path("components");
        if (c.path("v").asBoolean(false)) components.add(Components.Verbal);
        if (c.path("s").asBoolean(false)) components.add(Components.Somatic);
        if (c.has("m")) {
            components.add(Components.Material);
            JsonNode m = c.path("m");
            String text = m.isObject() ? m.path("text").asText("") : m.asText("");
            if (!text.isBlank()) header.add("Material: " + text + ".");
        }

        JsonNode d = e.path("duration").path(0);
        Duration duration;
        switch (d.path("type").asText("instant")) {
            case "timed" -> {
                boolean conc = d.path("concentration").asBoolean(false);
                int n = Math.max(1, d.path("duration").path("amount").asInt(1));
                DurationUnit unit = switch (d.path("duration").path("type").asText("minute")) {
                    case "round" -> DurationUnit.ROUND;
                    case "hour" -> DurationUnit.HOUR;
                    case "day" -> DurationUnit.DAY;
                    default -> DurationUnit.MINUTE;
                };
                duration = new Duration(n, unit, conc);
            }
            case "permanent" -> {
                duration = new Duration(0, DurationUnit.UNTIL_DISPELLED, false);
                JsonNode ends = d.path("ends");
                if (ends.isArray() && !ends.isEmpty()) {
                    List<String> how = new ArrayList<>();
                    for (JsonNode x : ends) how.add(x.asText());
                    header.add("Lasts until " + String.join(" or ", how) + ".");
                }
            }
            case "special" -> duration = new Duration(0, DurationUnit.SPECIAL, false);
            default -> duration = new Duration(0, DurationUnit.INSTANTANEOUS, false);
        }

        StringBuilder description = new StringBuilder();
        if (!header.isEmpty()) description.append(String.join(" ", header)).append("\n\n");
        String body = EntriesRenderer.render(e.path("entries"));
        if (body != null) description.append(body);
        String higher = EntriesRenderer.render(e.path("entriesHigherLevel"));
        if (higher != null) description.append("\n\n").append(higher);
        String text = description.toString().strip();
        return new ParsedSpell(school, level, time, rangeFt, components, duration,
                text.isEmpty() ? "(no description)" : text);
    }

    private static Time time(int number, String unit) {
        return switch (unit + ":" + number) {
            case "action:1" -> Time.Action;
            case "bonus:1" -> Time.BonusAction;
            case "reaction:1" -> Time.Reaction;
            case "minute:1" -> Time.OneMinute;
            case "minute:10" -> Time.TenMinutes;
            case "hour:1" -> Time.OneHour;
            case "hour:8" -> Time.EightHours;
            case "hour:12" -> Time.TwelveHours;
            case "hour:24" -> Time.TwentyFourHours;
            default -> null;
        };
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
