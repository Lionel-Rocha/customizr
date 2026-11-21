package top.customizr.system.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import top.customizr.system.dto.CharacterSheetResponse;
import top.customizr.system.dto.LevelUpPreviewResponse;
import top.customizr.system.dto.LevelUpRequest;
import top.customizr.system.dto.LevelUpResponse;
import top.customizr.system.model.AbilityScore;
import top.customizr.system.model.Attributes;
import top.customizr.system.model.Character;
import top.customizr.system.model.CharacterClass;
import top.customizr.system.model.ClassFeature;
import top.customizr.system.model.ClassResources;
import top.customizr.system.model.DiceRoller;
import top.customizr.system.model.DndClass;
import top.customizr.system.model.FeatDefinition;
import top.customizr.system.model.RollResult;
import top.customizr.system.model.Sheet;
import top.customizr.system.model.SpellSlotCalculator;
import top.customizr.system.model.SpellSlotCount;
import top.customizr.system.model.Subclass;
import top.customizr.system.repository.CharacterRepository;
import top.customizr.system.repository.DndClassRepository;
import top.customizr.system.repository.FeatDefinitionRepository;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Assistente de subir de nivel: mostra o que muda (PV, proficiencia, habilidades, subclasse, Aumento de Valor de
 * Atributo, espacos de magia, recursos) e aplica tudo de uma vez.
 */
@Service
public class LevelUpService {

    private static final String ASI = "Ability Score Improvement";
    private static final String EPIC_BOON = "Epic Boon";

    private final CharacterRepository characterRepository;
    private final DndClassRepository dndClassRepository;
    private final FeatDefinitionRepository featRepository;
    private final ChatService chatService;
    private final LiveTableService liveTable;

    public LevelUpService(CharacterRepository characterRepository, DndClassRepository dndClassRepository,
                          FeatDefinitionRepository featRepository, ChatService chatService, LiveTableService liveTable) {
        this.characterRepository = characterRepository;
        this.dndClassRepository = dndClassRepository;
        this.featRepository = featRepository;
        this.chatService = chatService;
        this.liveTable = liveTable;
    }

    @Transactional(readOnly = true)
    public LevelUpPreviewResponse preview(UUID characterId, UUID classId, String username) {
        Character character = manageable(characterId, username);
        return plan(character, targetClass(character, classId));
    }

    @Transactional
    public LevelUpResponse apply(UUID characterId, LevelUpRequest request, String username) {
        Character character = manageable(characterId, username);
        if (request == null) throw badRequest("Falta o corpo da requisição");
        DndClass dndClass = targetClass(character, request.classId());
        LevelUpPreviewResponse plan = plan(character, dndClass);
        Sheet sheet = character.getSheet();

        // escolhas do nivel, conferidas antes de mexer na ficha
        Subclass subclass = null;
        if (request.subclassId() != null) {
            if (!plan.subclassRequired()) throw badRequest("Este nível não escolhe subclasse");
            subclass = dndClass.getSubclasses().stream().filter(s -> s.getSubclassId().equals(request.subclassId()))
                    .findFirst().orElseThrow(() -> badRequest("A subclasse não é de " + dndClass.getClassName()));
        } else if (plan.subclassRequired() && !plan.subclasses().isEmpty()) {
            throw badRequest("Escolha a subclasse de " + dndClass.getClassName() + " " + plan.classLevel());
        }
        Map<AbilityScore, Integer> increases = cleanIncreases(request.abilityIncreases());
        int increaseTotal = increases.values().stream().mapToInt(Integer::intValue).sum();
        FeatDefinition feat = request.featId() == null ? null
                : featRepository.findById(request.featId()).orElseThrow(() -> notFound("talento"));
        boolean improvement = plan.abilityScoreImprovement() || plan.epicBoon();
        if (!improvement && (feat != null || increaseTotal > 0)) {
            throw badRequest("Este nível não dá Aumento no Valor de Atributo");
        }
        if (feat != null && increaseTotal > 1) {
            throw badRequest("Com talento, só +1 em um atributo (meio-talento)");
        }
        if (feat == null && increaseTotal > 0 && (increaseTotal != 2 || increases.values().stream().anyMatch(v -> v > 2))) {
            throw badRequest("O Aumento no Valor de Atributo é +2 em um atributo ou +1 em dois");
        }

        int levelsBefore = sheet.getTotalLevel();
        int conBefore = conModifier(sheet);
        CharacterClass characterClass = valid(() -> sheet.gainClassLevel(dndClass));
        Subclass chosen = subclass;
        if (chosen != null) valid(() -> { characterClass.chooseSubclass(chosen, characterClass.getLevel()); return null; });
        valid(() -> { sheet.increaseAbilities(increases, plan.abilityCap()); return null; });
        if (feat != null) valid(() -> sheet.addCatalogFeat(feat, sheet.getTotalLevel(), null));

        // PV: CON nova vale pro nivel novo e, se o modificador subiu, pra todos os anteriores
        int con = conModifier(sheet);
        int retroactive = Math.max(0, (con - conBefore) * levelsBefore);
        String hpRoll = null;
        int levelHp;
        if (plan.fixedHp() != null) {
            levelHp = Math.max(1, dndClass.getHitDie() + con);
        } else {
            LevelUpRequest.HitPointMethod method = request.hpMethod() == null
                    ? LevelUpRequest.HitPointMethod.AVERAGE : request.hpMethod();
            int die = Math.max(1, dndClass.getHitDie());
            int rolled = switch (method) {
                case AVERAGE -> die / 2 + 1;
                case ROLL -> {
                    RollResult roll = DiceRoller.roll("1d" + die);
                    hpRoll = "1d" + die + "[" + roll.total() + "]" + (con >= 0 ? "+" : "") + con;
                    yield roll.total();
                }
                case MANUAL -> {
                    Integer value = request.hpValue();
                    if (value == null || value < 1 || value > die) throw badRequest("hpValue vai de 1 a " + die);
                    yield value;
                }
            };
            levelHp = Math.max(1, rolled + con);
        }
        sheet.raiseMaxHp(levelHp + retroactive);

        liveTable.characterChanged(character);
        if (!Boolean.FALSE.equals(request.announce())) {
            chatService.postCard(character.getCampaign().getId(), character.getCharacterId(),
                    character.getName() + " chegou ao nível " + sheet.getTotalLevel(),
                    announcement(characterClass, plan, levelHp + retroactive, chosen, feat, increases), username);
        }
        return new LevelUpResponse(plan, levelHp + retroactive, retroactive, hpRoll,
                chosen == null ? null : chosen.getName(), feat == null ? null : feat.getName(),
                CharacterSheetResponse.from(character));
    }

    // ------------------------------------------------------------------ o plano do nivel

    private LevelUpPreviewResponse plan(Character character, DndClass dndClass) {
        Sheet sheet = character.getSheet();
        if (sheet.getTotalLevel() >= Sheet.MAX_LEVEL) throw badRequest("O personagem já está no nível " + Sheet.MAX_LEVEL);
        CharacterClass current = sheet.classOf(dndClass);
        boolean newClass = current == null;
        int classLevel = newClass ? 1 : current.getLevel() + 1;
        int totalLevel = sheet.getTotalLevel() + 1;
        int die = Math.max(1, dndClass.getHitDie());
        int con = conModifier(sheet);
        List<String> warnings = new ArrayList<>();

        // habilidades do nivel: da classe e da subclasse ja escolhida
        List<String> features = new ArrayList<>();
        Set<String> choices = new LinkedHashSet<>();
        boolean asi = false;
        boolean boon = false;
        List<FeatureAt> atLevel = new ArrayList<>();
        for (ClassFeature f : dndClass.getFeatures()) {
            if (f.getLevel() == classLevel) atLevel.add(new FeatureAt(f.getFeature().getName(), f.getChoiceGroup()));
        }
        if (current != null && current.getSubclass() != null) {
            current.getSubclass().getFeatures().stream().filter(f -> f.getLevel() == classLevel)
                    .forEach(f -> atLevel.add(new FeatureAt(f.getFeature().getName(), f.getChoiceGroup())));
        }
        for (FeatureAt f : atLevel) {
            if (f.choiceGroup() != null) { choices.add(f.choiceGroup()); continue; }
            features.add(f.name());
            if (f.name().equalsIgnoreCase(ASI)) asi = true;
            if (f.name().equalsIgnoreCase(EPIC_BOON)) boon = true;
        }
        if (dndClass.getFeatures().isEmpty()) {
            // classe importada sem a lista de habilidades: usa a tabela padrao de Aumento de Valor de Atributo
            boolean classic = ClassResources.isClassic(dndClass);
            String name = dndClass.getClassName().toLowerCase();
            boolean asiLevel = classLevel == 4 || classLevel == 8 || classLevel == 12 || classLevel == 16
                    || (name.equals("fighter") && (classLevel == 6 || classLevel == 14))
                    || (name.equals("rogue") && classLevel == 10);
            if (classLevel == 19) { if (classic) asiLevel = true; else boon = true; }
            asi = asiLevel;
            if (asi || boon) warnings.add("As características da classe não foram importadas: os níveis de Aumento no Valor de Atributo vêm da tabela padrão");
        }

        boolean subclassRequired = classLevel >= dndClass.getSubclassUnlockLevel()
                && (current == null || current.getSubclass() == null);
        List<LevelUpPreviewResponse.SubclassOption> subclasses = subclassRequired
                ? dndClass.getSubclasses().stream()
                        .filter(s -> s.getUnlockLevel() <= classLevel)
                        .map(s -> new LevelUpPreviewResponse.SubclassOption(s.getSubclassId(), s.getName(), s.getSourceCode()))
                        .toList()
                : List.of();
        if (subclassRequired && subclasses.isEmpty()) warnings.add("Nenhuma subclasse importada pra " + dndClass.getClassName());

        if (newClass && !sheet.getClasses().isEmpty()) {
            warnings.addAll(unmetMulticlass(sheet, dndClass));
            for (CharacterClass cc : sheet.getClasses()) warnings.addAll(unmetMulticlass(sheet, cc.getDndClass()));
        }
        if (sheet.getAttributes() == null) warnings.add("Atributos não preenchidos: a Constituição conta como +0");
        boolean experienceReady = character.getLevelByExperience() >= totalLevel;
        if (!experienceReady) warnings.add("A experiência está abaixo do nível " + totalLevel + " (tudo bem se a mesa sobe por marcos)");

        List<CharacterClass> after = sheet.classesAfterLevelUp(dndClass);
        return new LevelUpPreviewResponse(
                dndClass.getClassId(), dndClass.getClassName(), newClass, classLevel, totalLevel,
                die, con, Math.max(1, die / 2 + 1 + con),
                sheet.getClasses().isEmpty() ? Math.max(1, die + con) : null,
                proficiency(totalLevel - 1), proficiency(totalLevel),
                features, List.copyOf(choices), subclassRequired, subclasses,
                asi, boon, boon ? 30 : 20,
                slotChanges(sheet.getClasses(), after), resourceChanges(sheet, after),
                experienceReady, warnings);
    }

    private record FeatureAt(String name, String choiceGroup) {}

    private static int proficiency(int level) {
        return 2 + (Math.min(Math.max(1, level), 20) - 1) / 4;
    }

    private static List<LevelUpPreviewResponse.SlotChange> slotChanges(List<CharacterClass> before, List<CharacterClass> after) {
        Map<String, int[]> table = new LinkedHashMap<>();
        tally(table, before, 0);
        tally(table, after, 1);
        List<LevelUpPreviewResponse.SlotChange> out = new ArrayList<>();
        table.forEach((key, v) -> {
            if (v[0] == v[1]) return;
            String[] parts = key.split(":");
            out.add(new LevelUpPreviewResponse.SlotChange(parts[0], Integer.parseInt(parts[1]), v[0], v[1]));
        });
        return out;
    }

    private static void tally(Map<String, int[]> table, List<CharacterClass> classes, int column) {
        List<SpellSlotCount> slots;
        try {
            slots = SpellSlotCalculator.calculate(classes);
        } catch (IllegalArgumentException ex) {
            return;
        }
        for (SpellSlotCount s : slots) {
            table.computeIfAbsent(s.pool().name() + ":" + s.spellLevel(), k -> new int[2])[column] += s.totalSlots();
        }
    }

    private static List<LevelUpPreviewResponse.ResourceChange> resourceChanges(Sheet sheet, List<CharacterClass> after) {
        Map<String, Integer> before = new LinkedHashMap<>();
        ClassResources.of(sheet).forEach(r -> before.put(r.key(), r.max()));
        List<LevelUpPreviewResponse.ResourceChange> out = new ArrayList<>();
        for (ClassResources.ClassResource r : ClassResources.of(sheet, after)) {
            int was = before.getOrDefault(r.key(), 0);
            if (was != r.max()) out.add(new LevelUpPreviewResponse.ResourceChange(r.key(), r.name(), was, r.max()));
        }
        return out;
    }

    /**
     * Pre-requisito de multiclasse do 5etools ({@code multiclassing.requirements}): chaves soltas valem todas
     * ({@code {"int": 13}}); dentro de {@code "or"} basta uma ({@code {"or": [{"str": 13, "dex": 13}]}}).
     */
    @SuppressWarnings("unchecked")
    private static List<String> unmetMulticlass(Sheet sheet, DndClass dndClass) {
        Object cls = dndClass.getMetadata() == null ? null : dndClass.getMetadata().get("class");
        Object mc = cls instanceof Map<?, ?> m ? m.get("multiclassing") : null;
        Object req = mc instanceof Map<?, ?> m ? m.get("requirements") : null;
        if (!(req instanceof Map<?, ?> requirements) || sheet.getAttributes() == null) return List.of();
        List<String> missing = new ArrayList<>();
        for (Map.Entry<?, ?> e : requirements.entrySet()) {
            String key = String.valueOf(e.getKey());
            if (key.equals("or") && e.getValue() instanceof List<?> options) {
                for (Object option : options) {
                    if (!(option instanceof Map<?, ?> any)) continue;
                    boolean ok = any.entrySet().stream().anyMatch(o -> meets(sheet.getAttributes(), String.valueOf(o.getKey()), o.getValue()));
                    if (!ok) missing.add(String.join(" or ", any.entrySet().stream()
                            .map(o -> o.getKey().toString().toUpperCase() + " " + o.getValue()).toList()));
                }
            } else if (!meets(sheet.getAttributes(), key, e.getValue())) {
                missing.add(key.toUpperCase() + " " + e.getValue());
            }
        }
        return missing.isEmpty() ? List.of()
                : List.of("Multiclassing with " + dndClass.getClassName() + " needs " + String.join(" and ", missing));
    }

    private static boolean meets(Attributes a, String ability, Object minimum) {
        if (!(minimum instanceof Number n)) return true;
        int score = switch (ability.toLowerCase()) {
            case "str" -> a.getStrength();
            case "dex" -> a.getDexterity();
            case "con" -> a.getConstitution();
            case "int" -> a.getIntelligence();
            case "wis" -> a.getWisdom();
            case "cha" -> a.getCharisma();
            default -> Integer.MAX_VALUE;
        };
        return score >= n.intValue();
    }

    private static String announcement(CharacterClass cc, LevelUpPreviewResponse plan, int hp, Subclass subclass,
                                       FeatDefinition feat, Map<AbilityScore, Integer> increases) {
        List<String> lines = new ArrayList<>();
        lines.add(cc.getDndClass().getClassName() + " " + cc.getLevel() + " | +" + hp + " PV");
        if (plan.proficiencyAfter() > plan.proficiencyBefore()) lines.add("Bônus de proficiência +" + plan.proficiencyAfter());
        if (subclass != null) lines.add("Subclasse: " + subclass.getName());
        if (!plan.features().isEmpty()) lines.add("Características: " + String.join(", ", plan.features()));
        if (!increases.isEmpty()) lines.add("Atributos: " + String.join(", ", increases.entrySet().stream()
                .map(e -> TextoPt.ability(e.getKey()) + " +" + e.getValue()).toList()));
        if (feat != null) lines.add("Talento: " + feat.getName());
        return String.join("\n", lines);
    }

    // ------------------------------------------------------------------ utilitarios

    private DndClass targetClass(Character character, UUID classId) {
        if (classId != null) {
            return dndClassRepository.findById(classId).orElseThrow(() -> notFound("classe"));
        }
        List<CharacterClass> classes = character.getSheet().getClasses();
        if (classes.size() == 1) return classes.getFirst().getDndClass();
        throw badRequest(classes.isEmpty() ? "Escolha a primeira classe do personagem" : "Escolha qual classe ganha o nível");
    }

    private static Map<AbilityScore, Integer> cleanIncreases(Map<AbilityScore, Integer> raw) {
        Map<AbilityScore, Integer> out = new EnumMap<>(AbilityScore.class);
        if (raw == null) return out;
        raw.forEach((ability, amount) -> {
            if (ability == null || amount == null || amount == 0) return;
            if (amount < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aumento de atributo não pode ser negativo");
            out.put(ability, amount);
        });
        return out;
    }

    private static int conModifier(Sheet sheet) {
        return sheet.getAttributes() == null ? 0 : sheet.getAttributes().getModifier("constitution");
    }

    private <T> T valid(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw badRequest(ex.getMessage());
        }
    }

    private Character manageable(UUID characterId, String username) {
        Character character = characterRepository.findById(characterId).orElseThrow(() -> notFound("personagem"));
        if (!character.getOwner().getName().equals(username)
                && !character.getCampaign().getMaster().getName().equals(username)) {
            throw new AccessDeniedException("Você não pode mexer neste personagem");
        }
        return character;
    }

    private static ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " não encontrado");
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
