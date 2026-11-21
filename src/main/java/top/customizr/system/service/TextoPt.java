package top.customizr.system.service;

import top.customizr.system.model.AbilityScore;
import top.customizr.system.model.Condition;
import top.customizr.system.model.Skill;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Textos em portugues das rolagens que vao pro chat (ficha, combate no mapa, concentracao). Os codigos da API
 * (CRITICAL_HIT, CRITICAL_MISS, AUTOMATIC_FAILURE...) continuam iguais; so o texto que as pessoas leem muda.
 * Os nomes sao os mesmos do programa de PC.
 */
public final class TextoPt {

    private TextoPt() {
    }

    private static final Map<AbilityScore, String> ABILITY = Map.of(
            AbilityScore.STRENGTH, "Força", AbilityScore.DEXTERITY, "Destreza", AbilityScore.CONSTITUTION, "Constituição",
            AbilityScore.INTELLIGENCE, "Inteligência", AbilityScore.WISDOM, "Sabedoria", AbilityScore.CHARISMA, "Carisma");

    private static final Map<Skill, String> SKILL = Map.ofEntries(
            Map.entry(Skill.ACROBATICS, "Acrobacia"), Map.entry(Skill.ANIMAL_HANDLING, "Lidar com Animais"),
            Map.entry(Skill.ARCANA, "Arcanismo"), Map.entry(Skill.ATHLETICS, "Atletismo"), Map.entry(Skill.DECEPTION, "Enganação"),
            Map.entry(Skill.HISTORY, "História"), Map.entry(Skill.INSIGHT, "Intuição"), Map.entry(Skill.INTIMIDATION, "Intimidação"),
            Map.entry(Skill.INVESTIGATION, "Investigação"), Map.entry(Skill.MEDICINE, "Medicina"), Map.entry(Skill.NATURE, "Natureza"),
            Map.entry(Skill.PERCEPTION, "Percepção"), Map.entry(Skill.PERFORMANCE, "Atuação"), Map.entry(Skill.PERSUASION, "Persuasão"),
            Map.entry(Skill.RELIGION, "Religião"), Map.entry(Skill.SLEIGHT_OF_HAND, "Prestidigitação"),
            Map.entry(Skill.STEALTH, "Furtividade"), Map.entry(Skill.SURVIVAL, "Sobrevivência"));

    private static final Map<Condition, String> CONDITION = Map.ofEntries(
            Map.entry(Condition.BLINDED, "cego"), Map.entry(Condition.CHARMED, "enfeitiçado"), Map.entry(Condition.DEAFENED, "surdo"),
            Map.entry(Condition.FRIGHTENED, "assustado"), Map.entry(Condition.GRAPPLED, "agarrado"),
            Map.entry(Condition.INCAPACITATED, "incapacitado"), Map.entry(Condition.INVISIBLE, "invisível"),
            Map.entry(Condition.PARALYZED, "paralisado"), Map.entry(Condition.PETRIFIED, "petrificado"),
            Map.entry(Condition.POISONED, "envenenado"), Map.entry(Condition.PRONE, "caído"),
            Map.entry(Condition.RESTRAINED, "restringido"), Map.entry(Condition.STUNNED, "atordoado"),
            Map.entry(Condition.UNCONSCIOUS, "inconsciente"));

    private static final Map<String, String> DAMAGE = Map.ofEntries(
            Map.entry("acid", "ácido"), Map.entry("bludgeoning", "contundente"), Map.entry("cold", "frio"), Map.entry("fire", "fogo"),
            Map.entry("force", "energia"), Map.entry("lightning", "elétrico"), Map.entry("necrotic", "necrótico"),
            Map.entry("piercing", "perfurante"), Map.entry("poison", "veneno"), Map.entry("psychic", "psíquico"),
            Map.entry("radiant", "radiante"), Map.entry("slashing", "cortante"), Map.entry("thunder", "trovejante"));

    public static String ability(AbilityScore a) {
        return a == null ? "" : ABILITY.get(a);
    }

    /** "STR", "dex", "strength"... (do jeito que vier do bloco do monstro). */
    public static String ability(String key) {
        if (key == null) return "";
        String k = key.trim().toLowerCase(Locale.ROOT);
        for (AbilityScore a : AbilityScore.values()) {
            if (a.getKey().equals(k) || a.getKey().startsWith(k) && k.length() >= 3) return ABILITY.get(a);
        }
        return key;
    }

    public static String skill(Skill s) {
        return s == null ? "" : SKILL.getOrDefault(s, s.getLabel());
    }

    public static String condition(Condition c) {
        return c == null ? "" : CONDITION.getOrDefault(c, c.name().toLowerCase(Locale.ROOT));
    }

    /** Condicoes presentes no filtro, em portugues, separadas por virgula. */
    public static String conditions(Set<Condition> present, Set<Condition> filter) {
        return present.stream().filter(filter::contains).map(TextoPt::condition).sorted().collect(Collectors.joining(", "));
    }

    public static String damageType(String type) {
        return type == null ? null : DAMAGE.getOrDefault(type.trim().toLowerCase(Locale.ROOT), type);
    }

    /** Resultado da rolagem do jeito que aparece no chat (o codigo da API fica igual). */
    public static String outcome(String code) {
        if (code == null) return null;
        return switch (code) {
            case "CRITICAL_HIT" -> "CRÍTICO";
            case "CRITICAL_MISS" -> "FALHA CRÍTICA";
            case "AUTOMATIC_FAILURE" -> "falha automática";
            case "SUCCESS" -> "sucesso";
            case "FAILURE" -> "falha";
            case "STABLE" -> "estável";
            case "DEAD" -> "morto";
            case "REVIVED" -> "voltou com 1 PV";
            default -> code;
        };
    }

    /** Escola de magia (o nome da magia em si nao e traduzido). */
    public static String school(top.customizr.system.model.SpellSchool s) {
        if (s == null) return "";
        return switch (s) {
            case Abjuration -> "abjuração";
            case Conjuration -> "conjuração";
            case Divination -> "adivinhação";
            case Enchantment -> "encantamento";
            case Evocation -> "evocação";
            case Illusion -> "ilusão";
            case Necromancy -> "necromancia";
            case Transmutation -> "transmutação";
        };
    }

    /** Tempo de conjuracao. */
    public static String castingTime(top.customizr.system.model.Time t) {
        if (t == null) return "";
        return switch (t.name()) {
            case "Action" -> "1 ação";
            case "BonusAction" -> "1 ação bônus";
            case "Reaction" -> "1 reação";
            case "Ritual" -> "ritual";
            case "OneMinute" -> "1 minuto";
            case "TenMinutes" -> "10 minutos";
            case "OneHour" -> "1 hora";
            case "EightHours" -> "8 horas";
            case "TwelveHours" -> "12 horas";
            case "TwentyFourHours" -> "24 horas";
            default -> t.name();
        };
    }

    /** Unidade da duracao com o plural certo ("1 minuto", "10 minutos"). */
    public static String timeUnit(String unit, int amount) {
        boolean many = amount > 1;
        return switch (unit == null ? "" : unit) {
            case "ROUND" -> many ? "rodadas" : "rodada";
            case "MINUTE" -> many ? "minutos" : "minuto";
            case "HOUR" -> many ? "horas" : "hora";
            case "DAY" -> many ? "dias" : "dia";
            default -> unit == null ? "" : unit.toLowerCase(Locale.ROOT);
        };
    }

    private static final Map<String, String> WORDS = Map.ofEntries(
            // itens
            Map.entry("mundane", "comum"), Map.entry("magical", "mágico"), Map.entry("consumable", "consumível"),
            Map.entry("armor", "armadura"), Map.entry("weapon", "arma"), Map.entry("adventuring gear", "equipamento de aventura"),
            Map.entry("mounts vehicles", "montarias e veículos"), Map.entry("trinkets", "bugigangas"), Map.entry("poisons", "venenos"),
            Map.entry("tools", "ferramentas"), Map.entry("wondrous item", "item maravilhoso"),
            Map.entry("ammunition explosives", "munição e explosivos"), Map.entry("common", "comum"), Map.entry("uncommon", "incomum"),
            Map.entry("rare", "raro"), Map.entry("veryrare", "muito raro"), Map.entry("very rare", "muito raro"),
            Map.entry("legendary", "lendário"), Map.entry("artifact", "artefato"),
            // tamanho das criaturas
            Map.entry("tiny", "Minúsculo"), Map.entry("small", "Pequeno"), Map.entry("medium", "Médio"), Map.entry("large", "Grande"),
            Map.entry("huge", "Enorme"), Map.entry("gargantuan", "Imenso"),
            // tipo das criaturas
            Map.entry("aberration", "aberração"), Map.entry("beast", "fera"), Map.entry("celestial", "celestial"),
            Map.entry("construct", "constructo"), Map.entry("dragon", "dragão"), Map.entry("elemental", "elemental"),
            Map.entry("fey", "fada"), Map.entry("fiend", "corruptor"), Map.entry("giant", "gigante"), Map.entry("humanoid", "humanoide"),
            Map.entry("monstrosity", "monstruosidade"), Map.entry("ooze", "limo"), Map.entry("plant", "planta"),
            Map.entry("undead", "morto-vivo"));

    /** Palavra de item (tipo, categoria, raridade) ou de criatura (tamanho, tipo); o resto fica como veio. */
    public static String word(String raw) {
        if (raw == null || raw.isBlank()) return raw == null ? "" : raw;
        String key = raw.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
        return WORDS.getOrDefault(key, raw);
    }

    /** "Distância", "dano" etc. num lugar so pros servicos usarem igual. */
    public static String hitText(Boolean hit) {
        return hit == null ? "" : hit ? " | ACERTOU" : " | ERROU";
    }
}
