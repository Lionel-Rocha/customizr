package com.vttcaseiro.character;

import com.vttcaseiro.model.Condition;

import java.util.ArrayList;
import java.util.List;

/**
 * Regras de condicoes/exaustao nos testes de d20 (D&amp;D 2024), num lugar so
 * e sem depender de ficha: a CharacterSheet usa, e o token de monstro (que
 * guarda as condicoes nele mesmo) usa igual -- antes so personagem tinha
 * essas regras automaticas.
 */
public final class CombatRules {

    private CombatRules() {
    }

    /**
     * @param conditions nomes de {@link Condition} ativos
     * @param exhaustionLevel 0 a 6 (cada nivel = -2 no d20)
     * @param chosen o modo que o jogador/mestre escolheu (conta como mais
     *               uma fonte de vantagem/desvantagem)
     */
    public static RollAdjustment rollAdjustment(List<String> conditions, int exhaustionLevel,
                                                RollKind kind, Ability ability, RollMode chosen) {
        return rollAdjustment(conditions, exhaustionLevel, kind, ability, chosen, List.of(), CombatRules::rollDice);
    }

    /** Rola os dados de bonus dos efeitos (o 1d4 da Bencao). */
    public static int rollDice(String expression) {
        try {
            return com.vttcaseiro.dice.DiceRoller.roll(expression).total();
        } catch (com.vttcaseiro.dice.DiceException ex) {
            return 0;
        }
    }

    /**
     * Igual, mais os efeitos ativos (Bencao, Furia, "Anel [salv+1]"...):
     * os dados de bonus sao rolados AGORA por {@code roller} e somados no
     * modificador; vantagem/desvantagem dos efeitos entram na mesma conta
     * das condicoes (qualquer vantagem + qualquer desvantagem = normal).
     */
    public static RollAdjustment rollAdjustment(List<String> conditions, int exhaustionLevel,
                                                RollKind kind, Ability ability, RollMode chosen,
                                                List<String> effects, java.util.function.ToIntFunction<String> roller) {
        List<String> active = conditions == null ? List.of() : conditions;
        boolean adv = chosen == RollMode.ADVANTAGE;
        boolean dis = chosen == RollMode.DISADVANTAGE;
        boolean autoFail = false;
        List<String> notes = new ArrayList<>();
        boolean check = kind == RollKind.ABILITY_CHECK || kind == RollKind.INITIATIVE;
        boolean attack = kind == RollKind.ATTACK;
        boolean save = kind == RollKind.SAVING_THROW;
        boolean strOrDexSave = save && (ability == Ability.STRENGTH || ability == Ability.DEXTERITY);

        if (has(active, Condition.POISONED) && (check || attack)) {
            dis = true;
            notes.add("Envenenado: desvantagem");
        }
        if (has(active, Condition.FRIGHTENED) && (check || attack)) {
            dis = true;
            notes.add("Assustado: desvantagem (com a fonte a vista)");
        }
        if (attack && has(active, Condition.BLINDED)) {
            dis = true;
            notes.add("Cego: desvantagem");
        }
        if (attack && has(active, Condition.PRONE)) {
            dis = true;
            notes.add("Caido: desvantagem");
        }
        if (has(active, Condition.RESTRAINED) && (attack || (save && ability == Ability.DEXTERITY))) {
            dis = true;
            notes.add("Restringido: desvantagem");
        }
        if (has(active, Condition.INVISIBLE) && (attack || kind == RollKind.INITIATIVE)) {
            adv = true;
            notes.add("Invisivel: vantagem");
        }
        if (kind == RollKind.INITIATIVE && has(active, Condition.INCAPACITATED)) {
            dis = true;
            notes.add("Incapacitado: desvantagem");
        }
        if (attack && has(active, Condition.GRAPPLED)) {
            notes.add("Agarrado: desvantagem se o alvo nao for quem agarra");
        }
        if (strOrDexSave) {
            for (Condition c : List.of(Condition.PARALYZED, Condition.STUNNED, Condition.UNCONSCIOUS, Condition.PETRIFIED)) {
                if (has(active, c)) {
                    autoFail = true;
                    notes.add(c.label() + ": falha automatica");
                    break;
                }
            }
        }
        int delta = 0;
        if (exhaustionLevel > 0) {
            delta = -2 * exhaustionLevel;
            notes.add("Exaustao " + exhaustionLevel + ": " + delta);
        }
        EffectModifiers.RollEffect fx = EffectModifiers.forRoll(effects, kind, ability, roller);
        adv |= fx.advantage();
        dis |= fx.disadvantage();
        delta += fx.delta();
        notes.addAll(fx.notes());
        return new RollAdjustment(RollMode.combine(adv, dis), delta, notes, autoFail);
    }

    private static boolean has(List<String> conditions, Condition c) {
        return conditions.contains(c.name());
    }
}
