package top.customizr.system.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.ToIntBiFunction;

/**
 * Recursos de classe com usos (Furia, Pontos de Foco, Canalizar Divindade...). So numeros das regras:
 * o maximo sai do nivel da classe (ou de um atributo) e a recarga da regra da edicao da classe
 * (2024 = {@code edition "one"}; 2014 = {@code edition "classic"} ou fonte PHB).
 */
public final class ClassResources {

    /** Um recurso calculado pra esta ficha. {@code unit} = "HP" quando e uma reserva de pontos (Cura pelas Maos). */
    public record ClassResource(String key, String name, String className, int max, ResourceRecharge recharge, String unit) {}

    private record Rule(String className, boolean classic, String key, String name, int minLevel,
                        ToIntBiFunction<Integer, Sheet> max, IntFunction<ResourceRecharge> recharge, String unit) {}

    private static final List<Rule> RULES = new ArrayList<>();

    static {
        // ---------------------------------------------------------------- 2024 (XPHB)
        modern("Barbarian", "rage", "Rage", 1, (l, s) -> step(l, 1, 2, 3, 3, 6, 4, 12, 5, 17, 6), ResourceRecharge.SHORT_REST_ONE);
        modern("Bard", "bardic-inspiration", "Bardic Inspiration", 1, (l, s) -> Math.max(1, s.abilityModifier(AbilityScore.CHARISMA)),
                l -> l >= 5 ? ResourceRecharge.SHORT_REST : ResourceRecharge.LONG_REST, null);
        modern("Cleric", "channel-divinity", "Channel Divinity", 2, (l, s) -> step(l, 2, 2, 6, 3, 18, 4), ResourceRecharge.SHORT_REST_ONE);
        modern("Cleric", "divine-intervention", "Divine Intervention", 10, (l, s) -> 1, ResourceRecharge.LONG_REST);
        modern("Druid", "wild-shape", "Wild Shape", 2, (l, s) -> step(l, 2, 2, 6, 3, 17, 4), ResourceRecharge.SHORT_REST_ONE);
        modern("Fighter", "second-wind", "Second Wind", 1, (l, s) -> step(l, 1, 2, 4, 3, 10, 4), ResourceRecharge.SHORT_REST_ONE);
        modern("Fighter", "action-surge", "Action Surge", 2, (l, s) -> step(l, 2, 1, 17, 2), ResourceRecharge.SHORT_REST);
        modern("Fighter", "indomitable", "Indomitable", 9, (l, s) -> step(l, 9, 1, 13, 2, 17, 3), ResourceRecharge.LONG_REST);
        modern("Monk", "focus-points", "Focus Points", 2, (l, s) -> l, ResourceRecharge.SHORT_REST);
        modern("Monk", "uncanny-metabolism", "Uncanny Metabolism", 2, (l, s) -> 1, ResourceRecharge.LONG_REST);
        modern("Paladin", "lay-on-hands", "Lay On Hands", 1, (l, s) -> 5 * l, l -> ResourceRecharge.LONG_REST, "HP");
        modern("Paladin", "channel-divinity", "Channel Divinity", 3, (l, s) -> step(l, 3, 2, 11, 3), ResourceRecharge.SHORT_REST_ONE);
        modern("Ranger", "favored-enemy", "Favored Enemy", 1, (l, s) -> step(l, 1, 2, 5, 3, 9, 4, 13, 5, 17, 6), ResourceRecharge.LONG_REST);
        modern("Rogue", "stroke-of-luck", "Stroke of Luck", 20, (l, s) -> 1, ResourceRecharge.SHORT_REST);
        modern("Sorcerer", "innate-sorcery", "Innate Sorcery", 1, (l, s) -> 2, ResourceRecharge.LONG_REST);
        modern("Sorcerer", "sorcery-points", "Sorcery Points", 2, (l, s) -> l, ResourceRecharge.LONG_REST);
        modern("Warlock", "magical-cunning", "Magical Cunning", 2, (l, s) -> 1, ResourceRecharge.LONG_REST);
        modern("Wizard", "arcane-recovery", "Arcane Recovery", 1, (l, s) -> 1, ResourceRecharge.LONG_REST);

        // ---------------------------------------------------------------- 2014 (PHB)
        // Furia e Forma Selvagem viram ilimitadas no 20: o recurso some da lista
        classic("Barbarian", "rage", "Rage", 1, (l, s) -> l >= 20 ? 0 : step(l, 1, 2, 3, 3, 6, 4, 12, 5, 17, 6), ResourceRecharge.LONG_REST);
        rule("Bard", true, "bardic-inspiration", "Bardic Inspiration", 1, (l, s) -> Math.max(1, s.abilityModifier(AbilityScore.CHARISMA)),
                l -> l >= 5 ? ResourceRecharge.SHORT_REST : ResourceRecharge.LONG_REST, null);
        classic("Cleric", "channel-divinity", "Channel Divinity", 2, (l, s) -> step(l, 2, 1, 6, 2, 18, 3), ResourceRecharge.SHORT_REST);
        classic("Cleric", "divine-intervention", "Divine Intervention", 10, (l, s) -> 1, ResourceRecharge.LONG_REST);
        classic("Druid", "wild-shape", "Wild Shape", 2, (l, s) -> l >= 20 ? 0 : 2, ResourceRecharge.SHORT_REST);
        classic("Fighter", "second-wind", "Second Wind", 1, (l, s) -> 1, ResourceRecharge.SHORT_REST);
        classic("Fighter", "action-surge", "Action Surge", 2, (l, s) -> step(l, 2, 1, 17, 2), ResourceRecharge.SHORT_REST);
        classic("Fighter", "indomitable", "Indomitable", 9, (l, s) -> step(l, 9, 1, 13, 2, 17, 3), ResourceRecharge.LONG_REST);
        classic("Monk", "ki", "Ki Points", 2, (l, s) -> l, ResourceRecharge.SHORT_REST);
        rule("Paladin", true, "lay-on-hands", "Lay on Hands", 1, (l, s) -> 5 * l, l -> ResourceRecharge.LONG_REST, "HP");
        classic("Paladin", "channel-divinity", "Channel Divinity", 3, (l, s) -> 1, ResourceRecharge.SHORT_REST);
        classic("Rogue", "stroke-of-luck", "Stroke of Luck", 20, (l, s) -> 1, ResourceRecharge.SHORT_REST);
        classic("Sorcerer", "sorcery-points", "Sorcery Points", 2, (l, s) -> l, ResourceRecharge.LONG_REST);
        classic("Wizard", "arcane-recovery", "Arcane Recovery", 1, (l, s) -> 1, ResourceRecharge.LONG_REST);
    }

    private ClassResources() {}

    /** Recursos que as classes desta ficha dao, no nivel atual de cada uma. */
    public static List<ClassResource> of(Sheet sheet) {
        List<ClassResource> out = new ArrayList<>();
        for (CharacterClass cc : sheet.getClasses()) {
            DndClass dnd = cc.getDndClass();
            boolean classic = isClassic(dnd);
            for (Rule rule : RULES) {
                if (rule.classic() != classic || !rule.className().equalsIgnoreCase(dnd.getClassName())) continue;
                if (cc.getLevel() < rule.minLevel()) continue;
                int max = rule.max().applyAsInt(cc.getLevel(), sheet);
                if (max <= 0) continue;
                out.add(new ClassResource(keyOf(dnd, rule.key()), rule.name(), dnd.getClassName(), max,
                        rule.recharge().apply(cc.getLevel()), rule.unit()));
            }
        }
        return out;
    }

    /** Chave guardada na ficha, ex.: "barbarian.rage" (classe + recurso; Clerigo e Paladino tem cada um o seu). */
    static String keyOf(DndClass dnd, String key) {
        return dnd.getClassName().toLowerCase().replace(' ', '-') + "." + key;
    }

    static boolean isClassic(DndClass dnd) {
        if (dnd.getEdition() != null) return "classic".equalsIgnoreCase(dnd.getEdition());
        return "PHB".equalsIgnoreCase(dnd.getSourceCode());
    }

    /** Tabela por degraus: (nivel, valor) em ordem; vale o ultimo degrau alcancado. */
    private static int step(int level, int... pairs) {
        int value = 0;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (level >= pairs[i]) value = pairs[i + 1];
        }
        return value;
    }

    private static void modern(String cls, String key, String name, int minLevel, ToIntBiFunction<Integer, Sheet> max,
                               ResourceRecharge recharge) {
        rule(cls, false, key, name, minLevel, max, l -> recharge, null);
    }

    private static void modern(String cls, String key, String name, int minLevel, ToIntBiFunction<Integer, Sheet> max,
                               IntFunction<ResourceRecharge> recharge, String unit) {
        rule(cls, false, key, name, minLevel, max, recharge, unit);
    }

    private static void classic(String cls, String key, String name, int minLevel, ToIntBiFunction<Integer, Sheet> max,
                                ResourceRecharge recharge) {
        rule(cls, true, key, name, minLevel, max, l -> recharge, null);
    }

    private static void rule(String cls, boolean classic, String key, String name, int minLevel,
                             ToIntBiFunction<Integer, Sheet> max, IntFunction<ResourceRecharge> recharge, String unit) {
        RULES.add(new Rule(cls, classic, key, name, minLevel, max, recharge, unit));
    }
}
