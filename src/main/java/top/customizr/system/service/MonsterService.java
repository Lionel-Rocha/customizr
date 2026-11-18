package top.customizr.system.service;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import top.customizr.system.dto.ContentImportResponse;
import top.customizr.system.dto.MonsterAction;
import top.customizr.system.dto.MonsterCopyRequest;
import top.customizr.system.dto.MonsterRequest;
import top.customizr.system.dto.MonsterResponse;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.DiceRoller;
import top.customizr.system.model.EntriesRenderer;
import top.customizr.system.model.Monster;
import top.customizr.system.model.Source;
import top.customizr.system.repository.CampaignRepository;
import top.customizr.system.repository.MonsterRepository;
import top.customizr.system.repository.SourceRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private final tools.jackson.databind.ObjectMapper objectMapper;
    private final CampaignRepository campaignRepository;
    private final MasterGuard masterGuard;

    public MonsterService(MonsterRepository monsterRepository, SourceRepository sourceRepository,
                          tools.jackson.databind.ObjectMapper objectMapper, CampaignRepository campaignRepository,
                          MasterGuard masterGuard) {
        this.monsterRepository = monsterRepository;
        this.sourceRepository = sourceRepository;
        this.objectMapper = objectMapper;
        this.campaignRepository = campaignRepository;
        this.masterGuard = masterGuard;
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
                monster.updateSourceData(e.path("page").canConvertToInt() ? e.path("page").asInt() : null,
                        objectMapper.convertValue(e, new tools.jackson.core.type.TypeReference<>() {}));
                monster = monsterRepository.save(monster);
                (isNew ? created : updated).add(new ContentImportResponse.Processed(monster.getId(), name, code));
            } catch (IllegalArgumentException ex) {
                skipped.add(new ContentImportResponse.Skipped(name, code, ex.getMessage()));
            }
        }
        return new ContentImportResponse(created, updated, skipped);
    }

    /** Lista do catalogo com filtro por nome e faixa de ND (ex.: minCr=0.25, maxCr=2). Sem os customizados. */
    public List<MonsterResponse.Summary> search(String name, Double minCr, Double maxCr) {
        return search(name, minCr, maxCr, null, null);
    }

    /** Catalogo + monstros customizados da campanha (se vier campaignId e quem pede for da campanha). */
    @Transactional
    public List<MonsterResponse.Summary> search(String name, Double minCr, Double maxCr, UUID campaignId, String username) {
        if (campaignId != null) masterGuard.requireMember(username, campaign(campaignId));
        List<Monster> base = name == null || name.isBlank() ? monsterRepository.findAllByOrderByName()
                : monsterRepository.findByNameContainingIgnoreCaseOrderByName(name.trim());
        return base.stream()
                .filter(m -> !m.isCustom() || (campaignId != null && m.getCampaign().getId().equals(campaignId)))
                .filter(m -> minCr == null || m.getChallengeValue() >= minCr)
                .filter(m -> maxCr == null || m.getChallengeValue() <= maxCr)
                .sorted(Comparator.comparingDouble(Monster::getChallengeValue).thenComparing(Monster::getName))
                .map(MonsterResponse.Summary::from)
                .toList();
    }

    public MonsterResponse read(UUID id) {
        return MonsterResponse.from(monster(id));
    }

    /** Ficha do monstro; a de um customizado so quem e da campanha ve. */
    @Transactional
    public MonsterResponse read(UUID id, String username) {
        Monster monster = monster(id);
        if (monster.isCustom()) masterGuard.requireMember(username, monster.getCampaign());
        return MonsterResponse.from(monster);
    }

    // ------------------------------------------------------------------ monstros customizados (homebrew)

    /** Ficha nova (em branco ou preenchida) nos monstros da campanha. So o mestre. */
    @Transactional
    public MonsterResponse createCustom(MonsterRequest request, String username) {
        if (request == null || request.campaignId() == null) throw badRequest("campaignId is required");
        Campaign campaign = campaign(request.campaignId());
        masterGuard.requireMaster(username, campaign);
        Source source = homebrewSource(campaign);
        String name = request.name() == null || request.name().isBlank() ? "New monster" : request.name().trim();
        requireFreeName(name, source, null);
        Monster monster = valid(() -> Monster.custom(name, source, campaign));
        fillCustom(monster, request);
        return MonsterResponse.from(monsterRepository.save(monster));
    }

    /** Edita a ficha customizada (todos os campos). Monstro do catalogo nao se edita: copie antes. */
    @Transactional
    public MonsterResponse updateCustom(UUID id, MonsterRequest request, String username) {
        Monster monster = customOfMaster(id, username);
        if (request == null) throw badRequest("Request body is required");
        if (request.name() != null && !request.name().isBlank() && !request.name().trim().equals(monster.getName())) {
            requireFreeName(request.name().trim(), monster.getSource(), monster.getId());
            valid(() -> { monster.rename(request.name()); return null; });
        }
        fillCustom(monster, request);
        return MonsterResponse.from(monster);
    }

    /** Copia um monstro (catalogo ou outro customizado) pra campanha, pra ajustar a vontade. */
    @Transactional
    public MonsterResponse copyToCampaign(UUID id, MonsterCopyRequest request, String username) {
        if (request == null || request.campaignId() == null) throw badRequest("campaignId is required");
        Monster original = monster(id);
        Campaign campaign = campaign(request.campaignId());
        masterGuard.requireMaster(username, campaign);
        if (original.isCustom()) masterGuard.requireMember(username, original.getCampaign());
        Source source = homebrewSource(campaign);
        String base = request.name() != null && !request.name().isBlank() ? request.name().trim() : original.getName();
        String name = base;
        for (int i = 2; monsterRepository.findByNameAndSource_Id(name, source.getId()).isPresent(); i++) {
            name = base + " (" + i + ")";
        }
        String finalName = name;
        Monster copy = valid(() -> Monster.custom(finalName, source, campaign));
        copy.updateCore(original.getSize(), original.getCreatureType(), original.getAlignment(), original.getArmorClass(),
                original.getArmorNote(), original.getHpAverage(), original.getHpFormula(), original.getSpeed(),
                new int[] {original.getStrength(), original.getDexterity(), original.getConstitution(),
                        original.getIntelligence(), original.getWisdom(), original.getCharisma()},
                original.getChallengeRating());
        copy.updateDetails(original.getSavingThrows(), original.getSkills(), original.getDamageVulnerabilities(),
                original.getDamageResistances(), original.getDamageImmunities(), original.getConditionImmunities(),
                original.getSenses(), original.getPassivePerception(), original.getLanguages());
        copy.updateFeatures(original.getTraits(), original.getActions(), original.getBonusActions(), original.getReactions(),
                original.getLegendaryActions(), original.getSpellcasting());
        Map<String, Object> metadata = original.getMetadata() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(original.getMetadata());
        metadata.put("copiedFrom", original.getName() + " (" + original.getSource().getCode() + ")");
        copy.updateSourceData(original.getPage(), metadata);
        return MonsterResponse.from(monsterRepository.save(copy));
    }

    @Transactional
    public void deleteCustom(UUID id, String username) {
        monsterRepository.delete(customOfMaster(id, username));
    }

    /** Fonte "homebrew" de cada campanha (HB-xxxxxxxx), pra nao misturar com o catalogo. */
    public Source homebrewSource(Campaign campaign) {
        String code = "HB-" + campaign.getId().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        return sourceRepository.getSourceByCode(code)
                .orElseGet(() -> sourceRepository.save(new Source("Homebrew: " + campaign.getName(), code)));
    }

    private void fillCustom(Monster m, MonsterRequest r) {
        if (r.hpAverage() != null && r.hpAverage() < 1) throw badRequest("Hit points must be at least 1");
        if (r.armorClass() != null && (r.armorClass() < 1 || r.armorClass() > 40)) {
            throw badRequest("Armor class must be between 1 and 40");
        }
        int[] abilities = {orTen(r.strength()), orTen(r.dexterity()), orTen(r.constitution()),
                orTen(r.intelligence()), orTen(r.wisdom()), orTen(r.charisma())};
        String cr = blank(r.challengeRating());
        if (r.actions() == null && m.getMetadata() != null) {
            // sem "actions" no pedido: mantem as acoes que ja estao (ex.: copia de um monstro do catalogo)
            valid(() -> {
                m.updateCore(blank(r.size()), blank(r.creatureType()), blank(r.alignment()), r.armorClass(), blank(r.armorNote()),
                        r.hpAverage(), blank(r.hpFormula()), blank(r.speed()), abilities, cr);
                m.updateDetails(blank(r.savingThrows()), blank(r.skills()), blank(r.damageVulnerabilities()),
                        blank(r.damageResistances()), blank(r.damageImmunities()), blank(r.conditionImmunities()),
                        blank(r.senses()), r.passivePerception(), blank(r.languages()));
                m.updateFeatures(blank(r.traits()), m.getActions(), m.getBonusActions(), m.getReactions(),
                        m.getLegendaryActions(), m.getSpellcasting());
                return null;
            });
            return;
        }
        List<MonsterAction> actions = r.actions() == null ? List.of() : r.actions();
        List<Map<String, Object>> entries = new ArrayList<>();
        List<Map<String, Object>> typed = new ArrayList<>();
        StringBuilder actionsText = new StringBuilder();
        for (MonsterAction a : actions) {
            if (a == null || a.name() == null || a.name().isBlank()) throw badRequest("Every action needs a name");
            String entry = valid(() -> actionEntry(a));
            entries.add(Map.of("name", a.name().trim(), "entries", List.of(entry)));
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("name", a.name().trim());
            raw.put("kind", kind(a));
            putIfPresent(raw, "attackBonus", a.attackBonus());
            putIfPresent(raw, "melee", a.melee());
            putIfPresent(raw, "reach", a.reach());
            putIfPresent(raw, "saveDc", a.saveDc());
            putIfPresent(raw, "saveAbility", a.saveAbility());
            putIfPresent(raw, "damage", a.damage());
            putIfPresent(raw, "damageType", a.damageType());
            putIfPresent(raw, "text", a.text());
            typed.add(raw);
            if (actionsText.length() > 0) actionsText.append('\n');
            actionsText.append(a.name().trim()).append(". ").append(EntriesRenderer.renderText(entry));
        }
        valid(() -> {
            m.updateCore(blank(r.size()), blank(r.creatureType()), blank(r.alignment()), r.armorClass(), blank(r.armorNote()),
                    r.hpAverage(), blank(r.hpFormula()), blank(r.speed()), abilities, cr);
            m.updateDetails(blank(r.savingThrows()), blank(r.skills()), blank(r.damageVulnerabilities()),
                    blank(r.damageResistances()), blank(r.damageImmunities()), blank(r.conditionImmunities()),
                    blank(r.senses()), r.passivePerception(), blank(r.languages()));
            m.updateFeatures(blank(r.traits()), actionsText.length() == 0 ? null : actionsText.toString(), null, null, null, null);
            return null;
        });
        // as acoes ficam tambem no formato do 5etools, pra mesa ler os ataques igual aos do catalogo
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", m.getName());
        metadata.put("source", m.getSource().getCode());
        metadata.put("action", entries);
        metadata.put("customActions", typed);
        m.updateSourceData(null, metadata);
    }

    /** Uma acao no formato do 5etools: "{@atkr m} {@hit 5}, reach 5 ft. {@h}5 ({@damage 1d6+2}) Slashing damage." */
    static String actionEntry(MonsterAction a) {
        String damage = a.damage() == null || a.damage().isBlank() ? null : a.damage().replaceAll("\\s+", "");
        if (damage != null) DiceRoller.roll(damage, sides -> 1); // valida a expressao
        String type = a.damageType() == null || a.damageType().isBlank() ? "" : " " + capitalize(a.damageType().trim());
        String hitText = damage == null ? "" : average(damage) + " ({@damage " + damage + "})" + type + " damage.";
        String extra = a.text() == null || a.text().isBlank() ? "" : " " + a.text().trim();
        return switch (kind(a)) {
            case "ATTACK" -> {
                if (a.attackBonus() == null) throw new IllegalArgumentException("Attack bonus is required for " + a.name());
                boolean melee = a.melee() == null || a.melee();
                String reach = a.reach() == null || a.reach().isBlank()
                        ? (melee ? "reach 5 ft." : "range 80/320 ft.") : a.reach().trim();
                yield "{@atkr " + (melee ? "m" : "r") + "} {@hit " + a.attackBonus() + "}, " + reach
                        + (damage == null ? "" : " {@h}" + hitText) + extra;
            }
            case "SAVE" -> {
                if (a.saveDc() == null) throw new IllegalArgumentException("Save DC is required for " + a.name());
                String ability = a.saveAbility() == null || a.saveAbility().isBlank()
                        ? "dex" : a.saveAbility().trim().toLowerCase(Locale.ROOT);
                if (!List.of("str", "dex", "con", "int", "wis", "cha").contains(ability)) {
                    throw new IllegalArgumentException("Save ability must be STR, DEX, CON, INT, WIS or CHA");
                }
                yield "{@actSave " + ability + "} {@dc " + a.saveDc() + "}."
                        + (damage == null ? "" : " {@actSaveFail} " + hitText) + extra;
            }
            default -> extra.trim();
        };
    }

    private static String kind(MonsterAction a) {
        String k = a.kind() == null ? "OTHER" : a.kind().trim().toUpperCase(Locale.ROOT);
        return List.of("ATTACK", "SAVE").contains(k) ? k : "OTHER";
    }

    /** Media do dano (cada dado pela media arredondada pra baixo, como no livro). */
    private static int average(String damage) {
        return DiceRoller.roll(damage, sides -> (sides + 1) / 2).total();
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null && !(value instanceof String str && str.isBlank())) map.put(key, value);
    }

    private static int orTen(Integer value) {
        return value == null ? 10 : value;
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void requireFreeName(String name, Source source, UUID except) {
        monsterRepository.findByNameAndSource_Id(name, source.getId())
                .filter(m -> !m.getId().equals(except))
                .ifPresent(m -> { throw badRequest("This campaign already has a monster named " + name); });
    }

    private Monster customOfMaster(UUID id, String username) {
        Monster monster = monster(id);
        if (!monster.isCustom()) throw badRequest("Catalog monsters can't be edited; copy it to the campaign first");
        masterGuard.requireMaster(username, monster.getCampaign());
        return monster;
    }

    private Monster monster(UUID id) {
        return monsterRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monster not found"));
    }

    private Campaign campaign(UUID id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));
    }

    private static <T> T valid(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
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
