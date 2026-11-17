package top.customizr.system.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Le os ataques da ficha de monstro pela entrada original do 5etools ({@code metadata}): cada acao
 * com {@code {@hit N}} vira um ataque (bonus + dano), e cada acao com {@code {@dc N}} vira uma acao
 * de salvaguarda (CD + dano). Assim o token do monstro rola os ataques direto da ficha.
 */
public final class MonsterAttacks {

    /** kind: ATTACK (rola d20 + bonus contra a CA) ou SAVE (o alvo faz salvaguarda contra a CD). */
    public enum Kind { ATTACK, SAVE }

    /**
     * Ataque ou acao de monstro. damage = soma dos dados que valem sempre (sem os "se..."), ex.: "1d10+8+2d4";
     * damageType = tipo do primeiro dano; melee = ataque corpo a corpo (null se nao der pra saber).
     */
    public record Attack(int index, String name, Kind kind, Integer attackBonus, Integer saveDc, String saveAbility,
                         String damage, String damageType, Boolean melee, String text) {}

    private static final Pattern HIT = Pattern.compile("\\{@hit\\s+([+-]?\\d+)}");
    private static final Pattern DC = Pattern.compile("\\{@dc\\s+(\\d+)}");
    private static final Pattern SAVE = Pattern.compile("\\{@actSave\\s+(\\w+)}");
    private static final Pattern ATK = Pattern.compile("\\{@atkr?\\s+([a-z,]+)}");
    private static final Pattern DAMAGE = Pattern.compile("\\{@damage\\s+([^}]+)}\\)?\\s*([A-Za-z]+)?");

    private MonsterAttacks() {}

    /** Acoes, acoes bonus e reacoes do monstro, na ordem da ficha. Sem metadata, lista vazia. */
    public static List<Attack> of(Monster monster) {
        List<Attack> result = new ArrayList<>();
        if (monster == null || monster.getMetadata() == null) return result;
        for (String section : List.of("action", "bonus", "reaction")) {
            if (!(monster.getMetadata().get(section) instanceof List<?> actions)) continue;
            for (Object raw : actions) {
                if (!(raw instanceof Map<?, ?> action) || !(action.get("name") instanceof String name)) continue;
                String text = flatten(action.get("entries"));
                Attack attack = parse(result.size(), name, text);
                if (attack != null) result.add(attack);
            }
        }
        return result;
    }

    static Attack parse(int index, String rawName, String text) {
        Matcher hit = HIT.matcher(text);
        Matcher dc = DC.matcher(text);
        boolean isAttack = hit.find();
        boolean isSave = !isAttack && dc.find();
        if (!isAttack && !isSave) return null;
        StringBuilder damage = new StringBuilder();
        String damageType = null;
        // "plus 2 ({@damage 1d4}) ... if the attack roll had Advantage" nao entra: depende de condicao
        for (String clause : text.split("(?i)\\bplus\\b|\\band\\b")) {
            if (clause.matches("(?is).*\\bif\\b.*") && damage.length() > 0) continue;
            Matcher d = DAMAGE.matcher(clause);
            while (d.find()) {
                String dice = d.group(1).replaceAll("\\s+", "");
                if (!valid(dice)) continue;
                if (damage.length() > 0) damage.append('+');
                damage.append(dice);
                if (damageType == null && d.group(2) != null) damageType = d.group(2).toLowerCase(Locale.ROOT);
            }
        }
        Boolean melee = null;
        Matcher atk = ATK.matcher(text);
        if (atk.find()) melee = atk.group(1).startsWith("m") && !atk.group(1).contains("r");
        String saveAbility = null;
        if (isSave) {
            Matcher save = SAVE.matcher(text);
            if (save.find()) saveAbility = save.group(1).toUpperCase(Locale.ROOT);
        }
        String name = EntriesRenderer.renderText(rawName).trim();
        return new Attack(index, name, isAttack ? Kind.ATTACK : Kind.SAVE,
                isAttack ? Integer.parseInt(hit.group(1)) : null,
                isSave ? Integer.valueOf(dc.group(1)) : null, saveAbility,
                damage.length() == 0 ? null : damage.toString(), damageType, melee,
                EntriesRenderer.renderText(text).trim());
    }

    private static boolean valid(String dice) {
        try {
            DiceRoller.roll(dice, sides -> 1);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static String flatten(Object entries) {
        if (entries instanceof String s) return s;
        StringBuilder out = new StringBuilder();
        if (entries instanceof List<?> list) {
            for (Object e : list) {
                if (out.length() > 0) out.append(' ');
                out.append(e instanceof Map<?, ?> m ? flatten(m.get("entries")) : flatten(e));
            }
        }
        return out.toString();
    }
}
