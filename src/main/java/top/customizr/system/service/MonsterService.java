package top.customizr.system.service;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import top.customizr.system.dto.ContentImportResponse;
import top.customizr.system.dto.MonsterResponse;
import top.customizr.system.model.EntriesRenderer;
import top.customizr.system.model.Monster;
import top.customizr.system.model.Source;
import top.customizr.system.repository.MonsterRepository;
import top.customizr.system.repository.SourceRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Catalogo de monstros: importacao do bestiario do 5etools, busca e consulta. */
@Service
public class MonsterService {

    private static final Map<String, String> SIZES = Map.of(
            "T", "Tiny", "S", "Small", "M", "Medium", "L", "Large", "H", "Huge", "G", "Gargantuan");
    private static final Map<String, String> ALIGNMENT = Map.of(
            "L", "Lawful", "N", "Neutral", "C", "Chaotic", "G", "Good", "E", "Evil", "U", "Unaligned", "A", "Any alignment");
    private static final String[] ABILITIES = {"str", "dex", "con", "int", "wis", "cha"};

    private final MonsterRepository monsterRepository;
    private final SourceRepository sourceRepository;

    public MonsterService(MonsterRepository monsterRepository, SourceRepository sourceRepository) {
        this.monsterRepository = monsterRepository;
        this.sourceRepository = sourceRepository;
    }

    /** Arquivo bestiary-*.json do 5etools ({"monster": [...]}); cria ou atualiza por nome + fonte. */
    @Transactional
    public ContentImportResponse importBestiary(JsonNode payload) {
        if (payload == null || !payload.path("monster").isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected a JSON object with a \"monster\" array (5etools format)");
        }
        List<ContentImportResponse.Processed> created = new ArrayList<>();
        List<ContentImportResponse.Processed> updated = new ArrayList<>();
        List<ContentImportResponse.Skipped> skipped = new ArrayList<>();
        for (JsonNode e : payload.path("monster")) {
            String name = e.path("name").asText("").trim();
            String code = e.path("source").asText("").trim().toUpperCase(Locale.ROOT);
            if (name.isEmpty() || code.isEmpty()) {
                skipped.add(new ContentImportResponse.Skipped(name, code, "Name and source are required"));
                continue;
            }
            if (e.has("_copy")) {
                skipped.add(new ContentImportResponse.Skipped(name, code, "Copies of other entries (_copy) are not supported yet"));
                continue;
            }
            Source source = sourceRepository.getSourceByCode(code).orElse(null);
            if (source == null) {
                skipped.add(new ContentImportResponse.Skipped(name, code, "Source is not configured in the database"));
                continue;
            }
            try {
                Monster monster = monsterRepository.findByNameAndSource_Id(name, source.getId()).orElse(null);
                boolean isNew = monster == null;
                if (isNew) monster = new Monster(name, source);
                fill(monster, e);
                monster = monsterRepository.save(monster);
                (isNew ? created : updated).add(new ContentImportResponse.Processed(monster.getId(), name, code));
            } catch (IllegalArgumentException ex) {
                skipped.add(new ContentImportResponse.Skipped(name, code, ex.getMessage()));
            }
        }
        return new ContentImportResponse(created, updated, skipped);
    }

    /** Lista com filtro por nome e faixa de ND (ex.: minCr=0.25, maxCr=2). */
    public List<MonsterResponse.Summary> search(String name, Double minCr, Double maxCr) {
        List<Monster> base = name == null || name.isBlank() ? monsterRepository.findAllByOrderByName()
                : monsterRepository.findByNameContainingIgnoreCaseOrderByName(name.trim());
        return base.stream()
                .filter(m -> minCr == null || m.getChallengeValue() >= minCr)
                .filter(m -> maxCr == null || m.getChallengeValue() <= maxCr)
                .sorted(Comparator.comparingDouble(Monster::getChallengeValue).thenComparing(Monster::getName))
                .map(MonsterResponse.Summary::from)
                .toList();
    }

    public MonsterResponse read(UUID id) {
        return MonsterResponse.from(monsterRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monster not found")));
    }

    // ------------------------------------------------------------------ leitura do 5etools

    static void fill(Monster m, JsonNode e) {
        int[] abilities = new int[6];
        for (int i = 0; i < 6; i++) abilities[i] = e.path(ABILITIES[i]).asInt(10);
        JsonNode ac = e.path("ac").path(0);
        Integer armorClass = ac.isNumber() ? Integer.valueOf(ac.asInt()) : ac.path("ac").isNumber() ? ac.path("ac").asInt() : null;
        String armorNote = null;
        if (ac.path("from").isArray()) {
            List<String> from = new ArrayList<>();
            for (JsonNode f : ac.path("from")) from.add(EntriesRenderer.render(f));
            armorNote = String.join(", ", from);
        }
        JsonNode hp = e.path("hp");
        m.updateCore(sizes(e.path("size")), type(e.path("type")), alignment(e.path("alignment")), armorClass, armorNote,
                hp.path("average").isNumber() ? hp.path("average").asInt() : null,
                hp.path("formula").asText(hp.path("special").asText(null)), speed(e.path("speed")), abilities,
                cr(e.path("cr")));
        m.updateDetails(bonuses(e.path("save"), true), bonuses(e.path("skill"), false),
                damage(e.path("vulnerable")), damage(e.path("resist")), damage(e.path("immune")),
                damage(e.path("conditionImmune")), list(e.path("senses")),
                e.path("passive").isNumber() ? e.path("passive").asInt() : null, list(e.path("languages")));
        m.updateFeatures(features(e.path("trait")), features(e.path("action")), features(e.path("bonus")),
                features(e.path("reaction")), legendary(e), spellcasting(e.path("spellcasting")));
    }

    private static String sizes(JsonNode size) {
        List<String> out = new ArrayList<>();
        for (JsonNode s : size) out.add(SIZES.getOrDefault(s.asText(), s.asText()));
        return out.isEmpty() ? null : String.join(" or ", out);
    }

    private static String type(JsonNode type) {
        if (type.isString()) return capitalize(type.asText());
        if (!type.isObject()) return null;
        String base = type.path("type").isObject() ? "Creature" : capitalize(type.path("type").asText(""));
        List<String> tags = new ArrayList<>();
        for (JsonNode t : type.path("tags")) tags.add(t.isObject() ? t.path("tag").asText() : t.asText());
        return tags.isEmpty() ? base : base + " (" + String.join(", ", tags) + ")";
    }

    private static String alignment(JsonNode alignment) {
        if (!alignment.isArray() || alignment.isEmpty()) return null;
        List<String> parts = new ArrayList<>();
        for (JsonNode a : alignment) {
            if (a.isString()) parts.add(ALIGNMENT.getOrDefault(a.asText(), a.asText()));
            else if (a.has("special")) parts.add(a.path("special").asText());
        }
        String text = String.join(" ", parts);
        return "Neutral Neutral".equals(text) ? "Neutral" : text;
    }

    private static String speed(JsonNode speed) {
        if (!speed.isObject()) return null;
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, JsonNode> f : speed.properties()) {
            if (f.getKey().equals("canHover")) continue;
            JsonNode v = f.getValue();
            String value = v.isNumber() ? v.asInt() + " ft." : v.path("number").asInt() + " ft. " + v.path("condition").asText("");
            parts.add((f.getKey().equals("walk") ? "" : f.getKey() + " ") + value.strip());
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static String cr(JsonNode cr) {
        if (cr.isString()) return cr.asText();
        if (cr.isObject()) return cr.path("cr").asText(null);
        return null;
    }

    private static String bonuses(JsonNode node, boolean upper) {
        if (!node.isObject()) return null;
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, JsonNode> f : node.properties()) {
            String key = upper ? f.getKey().toUpperCase() : capitalize(f.getKey());
            parts.add(key + " " + f.getValue().asText());
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    /** ["fire", {"resist": ["cold"], "note": "from nonmagical attacks"}] -> "Fire; Cold (from nonmagical attacks)". */
    private static String damage(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) return null;
        List<String> parts = new ArrayList<>();
        for (JsonNode d : node) {
            if (d.isString()) {
                parts.add(capitalize(d.asText()));
            } else if (d.isObject()) {
                if (d.has("special")) {
                    parts.add(EntriesRenderer.renderText(d.path("special").asText()));
                    continue;
                }
                List<String> inner = new ArrayList<>();
                for (Map.Entry<String, JsonNode> f : d.properties()) {
                    if (f.getValue().isArray()) {
                        for (JsonNode x : f.getValue()) if (x.isString()) inner.add(capitalize(x.asText()));
                    }
                }
                String note = d.path("note").asText(d.path("preNote").asText(""));
                parts.add(String.join(", ", inner) + (note.isBlank() ? "" : " (" + EntriesRenderer.renderText(note) + ")"));
            }
        }
        return String.join("; ", parts);
    }

    private static String list(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) return null;
        List<String> parts = new ArrayList<>();
        for (JsonNode x : node) parts.add(EntriesRenderer.render(x));
        return String.join(", ", parts);
    }

    private static String features(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) return null;
        List<String> parts = new ArrayList<>();
        for (JsonNode f : node) {
            String name = f.path("name").asText(null);
            String body = EntriesRenderer.render(f.path("entries"));
            String text = (name == null ? "" : EntriesRenderer.render(f.path("name")) + ". ") + (body == null ? "" : body);
            if (!text.isBlank()) parts.add(text.strip());
        }
        return parts.isEmpty() ? null : String.join("\n\n", parts);
    }

    private static String legendary(JsonNode e) {
        String actions = features(e.path("legendary"));
        if (actions == null) return null;
        String header = EntriesRenderer.render(e.path("legendaryHeader"));
        return header == null ? actions : header + "\n\n" + actions;
    }

    private static String spellcasting(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) return null;
        List<String> blocks = new ArrayList<>();
        for (JsonNode sc : node) {
            StringBuilder b = new StringBuilder(EntriesRenderer.render(sc.path("name")) + ". ");
            String header = EntriesRenderer.render(sc.path("headerEntries"));
            if (header != null) b.append(header);
            if (sc.path("will").isArray()) b.append("\nAt will: ").append(spells(sc.path("will")));
            for (Map.Entry<String, JsonNode> d : sc.path("daily").properties()) {
                String per = d.getKey().endsWith("e") ? d.getKey().replace("e", "") + "/day each" : d.getKey() + "/day";
                b.append("\n").append(per).append(": ").append(spells(d.getValue()));
            }
            for (Map.Entry<String, JsonNode> lv : sc.path("spells").properties()) {
                b.append("\n").append("0".equals(lv.getKey()) ? "Cantrips" : "Level " + lv.getKey()).append(": ")
                        .append(spells(lv.getValue().path("spells")));
            }
            blocks.add(b.toString().strip());
        }
        return String.join("\n\n", blocks);
    }

    private static String spells(JsonNode list) {
        List<String> names = new ArrayList<>();
        for (JsonNode s : list) names.add(EntriesRenderer.render(s));
        return String.join(", ", names);
    }

    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
