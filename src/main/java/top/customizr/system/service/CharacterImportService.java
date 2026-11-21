package top.customizr.system.service;

import jakarta.transaction.Transactional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import top.customizr.system.dto.CharacterImportResponse;
import top.customizr.system.dto.CreateCharacterRequest;
import top.customizr.system.dto.UpdateAttributesRequest;
import top.customizr.system.dto.UpdateCombatStatsRequest;
import top.customizr.system.model.Attributes;
import top.customizr.system.model.Character;
import top.customizr.system.model.DndClass;
import top.customizr.system.repository.DndClassRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Importa um personagem pronto: ator do Foundry VTT (dnd5e, "Export Data"), personagem do
 * D&D Beyond (JSON) ou PDF preenchivel (ficha oficial de 5e, "Exportar para PDF" do D&D Beyond).
 * Le nome, atributos, PV, CA, deslocamento e classes; as classes sao ligadas pelo nome as que ja
 * foram importadas em /class/import (as que nao existem voltam em {@code notes}).
 */
@Service
public class CharacterImportService {

    public static final int MAX_BYTES = 5 * 1024 * 1024;

    private static final String[] ABILITY_KEYS = {"str", "dex", "con", "int", "wis", "cha"};
    private static final String[][] PDF_ABILITY_ALIASES = {
            {"str", "strength", "forca", "for", "strscore"},
            {"dex", "dexterity", "destreza", "des", "dexscore"},
            {"con", "constitution", "constituicao", "conscore"},
            {"int", "intelligence", "inteligencia", "intscore"},
            {"wis", "wisdom", "sabedoria", "sab", "wisscore"},
            {"cha", "charisma", "carisma", "car", "chascore"}};
    private static final Pattern INT = Pattern.compile("[+-]?\\s*\\d+");
    private static final Pattern CLASS_LEVEL = Pattern.compile("([\\p{L}][\\p{L} '()-]*?)\\s*(\\d{1,2})");

    private final CharacterService characterService;
    private final DndClassRepository dndClassRepository;
    private final ObjectMapper objectMapper;

    public CharacterImportService(
            CharacterService characterService,
            DndClassRepository dndClassRepository,
            ObjectMapper objectMapper
    ) {
        this.characterService = characterService;
        this.dndClassRepository = dndClassRepository;
        this.objectMapper = objectMapper;
    }

    /** Dados lidos do arquivo, antes de virar personagem. Atributos/PV podem faltar (null). */
    public record ImportedSheet(
            String format,
            String name,
            int[] abilities,
            Integer hpMax,
            Integer hpCurrent,
            Integer hpTemp,
            Integer armorClass,
            Integer speedFt,
            List<ClassLevel> classes,
            List<String> notes
    ) {}

    public record ClassLevel(String name, int level) {}

    @Transactional
    public CharacterImportResponse importCharacter(UUID campaignId, String fileName, byte[] content, String username) {
        if (content == null || content.length == 0) {
            throw badRequest("O arquivo está vazio");
        }
        if (content.length > MAX_BYTES) {
            throw badRequest("Arquivo grande demais (máximo 5 MB)");
        }
        ImportedSheet sheet;
        try {
            sheet = read(fileName, content);
        } catch (IOException | IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }

        Character character = characterService.create(new CreateCharacterRequest(campaignId, sheet.name()), username);
        UUID id = character.getCharacterId();
        List<String> notes = new ArrayList<>(sheet.notes());
        int[] a = sheet.abilities();
        if (a != null) {
            characterService.updateAttributes(id, new UpdateAttributesRequest(a[0], a[1], a[2], a[3], a[4], a[5]), username);
        }
        List<String> addedClasses = new ArrayList<>();
        List<DndClass> known = dndClassRepository.findAll();
        for (ClassLevel classLevel : sheet.classes()) {
            DndClass match = known.stream()
                    .filter(c -> norm(c.getClassName()).equals(norm(classLevel.name())))
                    .findFirst().orElse(null);
            if (match == null) {
                notes.add("Classe \"" + classLevel.name() + "\" (nível " + classLevel.level()
                        + ") is not imported yet; import it in /class/import and add it to the sheet.");
                continue;
            }
            try {
                characterService.addClass(id, new top.customizr.system.dto.AddCharacterClassRequest(
                        match.getClassId(), Math.max(1, Math.min(20, classLevel.level()))), username);
                addedClasses.add(match.getClassName() + " " + classLevel.level());
            } catch (ResponseStatusException ex) {
                notes.add("Não deu pra pôr a classe " + classLevel.name() + ": " + ex.getReason());
            }
        }
        Integer hpMax = sheet.hpMax();
        if (hpMax == null) {
            hpMax = character.getSheet().getSuggestedHpMax();
            if (hpMax != null) {
                notes.add("O PV máximo não estava no arquivo; calculado pelas classes (" + hpMax + ").");
            }
        }
        Integer armorClass = sheet.armorClass();
        if (armorClass == null && a != null) {
            armorClass = 10 + Attributes.modifierOf(a[1]);
            notes.add("A CA não estava no arquivo; usando 10 + DES (" + armorClass + "). Confira a armadura.");
        }
        Integer hpCurrent = sheet.hpCurrent() == null ? hpMax
                : hpMax == null ? sheet.hpCurrent() : Math.min(hpMax, sheet.hpCurrent());
        characterService.updateCombatStats(id,
                new UpdateCombatStatsRequest(hpMax, hpCurrent, sheet.hpTemp(), armorClass, sheet.speedFt()), username);

        return new CharacterImportResponse(id, sheet.name(), sheet.format(), addedClasses, notes);
    }

    /** Descobre o formato pelo conteudo (PDF comeca com %PDF; JSON pelo formato do objeto). */
    public ImportedSheet read(String fileName, byte[] content) throws IOException {
        if (content.length >= 4 && new String(content, 0, 4, StandardCharsets.ISO_8859_1).equals("%PDF")) {
            return readPdf(content);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(new String(content, StandardCharsets.UTF_8));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("O arquivo não é PDF nem JSON válido");
        }
        if (root.path("system").path("abilities").isObject()) {
            return readFoundry(root);
        }
        JsonNode ddb = root.path("data").isObject() ? root.path("data") : root;
        if (ddb.path("stats").isArray() && ddb.has("classes")) {
            return readDdb(ddb);
        }
        throw new IllegalArgumentException("JSON desconhecido. Aceitos: ator dnd5e do Foundry VTT ou personagem do D&D Beyond");
    }

    // ------------------------------------------------------------------ Foundry VTT (dnd5e)

    ImportedSheet readFoundry(JsonNode actor) {
        List<String> notes = new ArrayList<>();
        JsonNode sys = actor.path("system");
        int[] scores = new int[6];
        for (int i = 0; i < 6; i++) {
            scores[i] = clampScore(sys.path("abilities").path(ABILITY_KEYS[i]).path("value").asInt(10));
        }
        JsonNode hp = sys.path("attributes").path("hp");
        Integer hpMax = positiveOrNull(hp.path("max"));
        Integer hpCurrent = intOrNull(hp.path("value"));
        Integer hpTemp = positiveOrNull(hp.path("temp"));
        JsonNode ac = sys.path("attributes").path("ac");
        Integer armorClass = positiveOrNull(ac.path("flat"));
        if (armorClass == null) {
            armorClass = positiveOrNull(ac.path("value"));
        }
        if (armorClass == null) {
            armorClass = foundryArmorClass(actor, Attributes.modifierOf(scores[1]));
            notes.add("CA calculada como o Foundry faz (armadura vestida + DES + escudo): " + armorClass
                    + ". Check it if the character has a magic item or feature that changes it.");
        }
        Integer speed = positiveOrNull(sys.path("attributes").path("movement").path("walk"));

        List<ClassLevel> classes = new ArrayList<>();
        for (JsonNode item : actor.path("items")) {
            if ("class".equals(item.path("type").asText())) {
                classes.add(new ClassLevel(item.path("name").asText("?"), Math.max(1, item.path("system").path("levels").asInt(1))));
            }
        }
        return new ImportedSheet("Foundry VTT (dnd5e)", nameOr(actor.path("name").asText(null)), scores,
                hpMax, hpCurrent, hpTemp, armorClass, speed, classes, notes);
    }

    /** CA pela armadura vestida: leve = base + DES; media = base + DES (max 2); pesada = base; + escudo. */
    static int foundryArmorClass(JsonNode actor, int dexMod) {
        Integer armorBase = null;
        int dexCap = 99;
        int shield = 0;
        for (JsonNode item : actor.path("items")) {
            if (!"equipment".equals(item.path("type").asText())) continue;
            JsonNode is = item.path("system");
            if (!is.path("equipped").asBoolean(false)) continue;
            String kind = is.path("type").path("value").asText(is.path("armor").path("type").asText(""));
            int value = is.path("armor").path("value").asInt(0) + is.path("armor").path("magicalBonus").asInt(0);
            if ("shield".equals(kind)) {
                shield += value;
            } else if (("light".equals(kind) || "medium".equals(kind) || "heavy".equals(kind))
                    && (armorBase == null || value > armorBase)) {
                armorBase = value;
                int cap = is.path("armor").path("dex").asInt(0);
                dexCap = "heavy".equals(kind) ? 0 : "medium".equals(kind) ? (cap == 0 ? 2 : cap) : (cap == 0 ? 99 : cap);
            }
        }
        int armorClass = armorBase == null ? 10 + dexMod : armorBase + Math.min(dexMod, dexCap);
        return armorClass + shield;
    }

    // ------------------------------------------------------------------ D&D Beyond

    private static final String[] DDB_SCORE_SUBTYPES = {"strength-score", "dexterity-score", "constitution-score",
            "intelligence-score", "wisdom-score", "charisma-score"};

    ImportedSheet readDdb(JsonNode d) {
        List<String> notes = new ArrayList<>();
        int[] scores = {10, 10, 10, 10, 10, 10};
        for (JsonNode stat : d.path("stats")) addStat(stat, scores, false);
        for (JsonNode stat : d.path("bonusStats")) addStat(stat, scores, true);
        for (JsonNode group : d.path("modifiers")) {
            for (JsonNode modifier : group) {
                if (!"bonus".equals(modifier.path("type").asText())) continue;
                String sub = modifier.path("subType").asText();
                for (int i = 0; i < 6; i++) {
                    if (DDB_SCORE_SUBTYPES[i].equals(sub)) {
                        scores[i] += modifier.path("value").asInt(modifier.path("fixedValue").asInt(0));
                    }
                }
            }
        }
        for (JsonNode stat : d.path("overrideStats")) {
            int id = stat.path("id").asInt(0);
            if (id >= 1 && id <= 6 && stat.path("value").isNumber()) scores[id - 1] = stat.path("value").asInt();
        }
        for (int i = 0; i < 6; i++) scores[i] = clampScore(scores[i]);

        List<ClassLevel> classes = new ArrayList<>();
        int level = 0;
        for (JsonNode c : d.path("classes")) {
            int lv = Math.max(1, c.path("level").asInt(1));
            level += lv;
            classes.add(new ClassLevel(c.path("definition").path("name").asText("?"), lv));
        }
        Integer hpMax;
        if (d.path("overrideHitPoints").isNumber()) {
            hpMax = d.path("overrideHitPoints").asInt();
        } else {
            int base = d.path("baseHitPoints").asInt(0) + d.path("bonusHitPoints").asInt(0);
            hpMax = base > 0 ? base + Attributes.modifierOf(scores[2]) * Math.max(1, level) : null;
        }
        Integer hpCurrent = hpMax == null ? null : Math.max(0, hpMax - d.path("removedHitPoints").asInt(0));
        Integer temp = positiveOrNull(d.path("temporaryHitPoints"));
        Integer speed = positiveOrNull(d.path("race").path("weightSpeeds").path("normal").path("walk"));
        notes.add("O D&D Beyond não exporta a CA (depende da armadura); confira na ficha.");
        return new ImportedSheet("D&D Beyond (JSON)", nameOr(d.path("name").asText(null)), scores,
                hpMax, hpCurrent, temp, null, speed, classes, notes);
    }

    private static void addStat(JsonNode stat, int[] scores, boolean bonus) {
        int id = stat.path("id").asInt(0);
        if (id < 1 || id > 6 || !stat.path("value").isNumber()) return;
        if (bonus) scores[id - 1] += stat.path("value").asInt();
        else scores[id - 1] = stat.path("value").asInt();
    }

    // ------------------------------------------------------------------ PDF preenchivel

    ImportedSheet readPdf(byte[] pdf) throws IOException {
        Map<String, String> fields = new LinkedHashMap<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            if (doc.isEncrypted()) {
                doc.setAllSecurityToBeRemoved(true);
            }
            PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
            if (form != null) {
                for (PDField field : form.getFieldTree()) {
                    String key = norm(field.getFullyQualifiedName());
                    try {
                        String value = field instanceof PDCheckBox box ? (box.isChecked() ? "Yes" : "Off") : field.getValueAsString();
                        if (value != null && !value.isBlank()) fields.putIfAbsent(key, value.strip());
                    } catch (RuntimeException ignored) {
                        // campo estranho: pula
                    }
                }
            }
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException ex) {
            throw new IllegalArgumentException("O PDF tem senha; tire a senha e tente de novo");
        }
        List<String> notes = new ArrayList<>();
        String name = first(fields, "charactername", "charactername2", "name", "nome", "nomedopersonagem", "personagem");
        int[] scores = new int[6];
        int recognized = name == null ? 0 : 1;
        for (int i = 0; i < 6; i++) {
            Integer score = null;
            for (String alias : PDF_ABILITY_ALIASES[i]) {
                Integer v = number(fields.get(alias));
                if (v != null && v >= 1 && v <= 30) { score = v; break; }
            }
            if (score != null) recognized++;
            scores[i] = score == null ? 10 : score;
        }
        if (recognized < 3) {
            throw new IllegalArgumentException("Não deu pra ler uma ficha deste PDF. Funciona com PDF preenchível "
                    + "PDFs (official 5e sheet, D&D Beyond \"Export to PDF\"); scanned PDFs have no data inside.");
        }
        List<ClassLevel> classes = new ArrayList<>();
        String classLevel = first(fields, "classlevel", "classandlevel", "classeenivel", "classenivel");
        if (classLevel != null) {
            Matcher m = CLASS_LEVEL.matcher(classLevel);
            while (m.find()) classes.add(new ClassLevel(m.group(1).strip(), Integer.parseInt(m.group(2))));
        }
        Integer ac = number(first(fields, "ac", "armorclass", "ca", "classedearmadura", "armor"));
        Integer hpMax = number(first(fields, "hpmax", "maxhp", "hitpointmaximum", "pvmax", "pvmaximo", "maxhitpoints"));
        Integer hp = number(first(fields, "hpcurrent", "currenthp", "currenthitpoints", "pvatuais", "pvatual"));
        Integer temp = number(first(fields, "hptemp", "temphp", "temporaryhitpoints", "pvtemporarios"));
        String speedText = first(fields, "speed", "deslocamento", "movimento", "walkingspeed");
        Integer speed = speedFeet(speedText);
        if (speedText != null && speed == null) notes.add("Não deu pra ler o deslocamento \"" + speedText + "\".");
        return new ImportedSheet("Fillable PDF (" + fields.size() + " fields)", nameOr(name), scores,
                hpMax != null && hpMax > 0 ? hpMax : null, hp, temp,
                ac != null && ac > 0 && ac < 40 ? ac : null, speed, classes, notes);
    }

    /** "30 ft" -> 30; "9 m" -> 30 (1,5 m = 5 pes). */
    static Integer speedFeet(String raw) {
        if (raw == null) return null;
        Integer n = number(raw);
        if (n == null) return null;
        String t = raw.toLowerCase(Locale.ROOT);
        if (t.matches(".*\\d\\s*(m|metros?)\\b.*")) {
            return (int) Math.round(n / 1.5) * 5;
        }
        return n;
    }

    // ------------------------------------------------------------------ utilitarios

    static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String first(Map<String, String> fields, String... keys) {
        for (String key : keys) {
            String v = fields.get(key);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    static Integer number(String v) {
        if (v == null) return null;
        Matcher m = INT.matcher(v);
        if (!m.find()) return null;
        try {
            return Integer.parseInt(m.group().replaceAll("\\s+", "").replace("+", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int clampScore(int v) {
        return Math.max(1, Math.min(30, v));
    }

    private static Integer intOrNull(JsonNode n) {
        return n != null && n.isNumber() ? n.asInt() : null;
    }

    private static Integer positiveOrNull(JsonNode n) {
        Integer v = intOrNull(n);
        return v != null && v > 0 ? v : null;
    }

    private static String nameOr(String name) {
        return name == null || name.isBlank() ? "Imported character" : name.strip();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
