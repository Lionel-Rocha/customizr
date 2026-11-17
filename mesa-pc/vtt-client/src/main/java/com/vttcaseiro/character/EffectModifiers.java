package com.vttcaseiro.character;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Efeitos com numeros: "Bencao" soma 1d4 nos ataques e salvaguardas,
 * "Escudo" da +5 de CA, "Furia" da +2 no dano e resistencia a dano fisico.
 * <p>
 * Um efeito e so um texto (o mesmo que ja ia pra ficha e pro token). Os
 * conhecidos tem os numeros no {@link #CATALOG}; qualquer outro pode trazer
 * os proprios entre colchetes:
 * <pre>
 *   Anel de Protecao [ca+1, salv+1]
 *   Furia [dano+3, resist:contundente/perfurante/cortante, vant:teste-for, vant:salv-for]
 * </pre>
 * Termos: {@code ca}, {@code ataque}, {@code salv}, {@code teste}, {@code dano}
 * seguidos de +N, -N, +XdY ou -XdY; {@code resist:tipo/tipo};
 * {@code vant:} ou {@code desv:} com ataque, salv, teste ou iniciativa,
 * opcionalmente "-for", "-des"... pra um atributo so.
 */
public final class EffectModifiers {

    private EffectModifiers() {
    }

    /** Nome (sem acento, minusculo) -> termos. */
    public static final Map<String, String> CATALOG = new LinkedHashMap<>();

    static {
        CATALOG.put("bencao", "ataque+1d4, salv+1d4");
        CATALOG.put("bless", "ataque+1d4, salv+1d4");
        CATALOG.put("abencoado", "ataque+1d4, salv+1d4");
        CATALOG.put("perdicao", "ataque-1d4, salv-1d4");
        CATALOG.put("bane", "ataque-1d4, salv-1d4");
        CATALOG.put("escudo", "ca+5");
        CATALOG.put("shield", "ca+5");
        CATALOG.put("escudo da fe", "ca+2");
        CATALOG.put("shield of faith", "ca+2");
        CATALOG.put("orientacao", "teste+1d4");
        CATALOG.put("guidance", "teste+1d4");
        CATALOG.put("furia", "dano+2, resist:contundente/perfurante/cortante, vant:teste-for, vant:salv-for");
        CATALOG.put("rage", "dano+2, resist:contundente/perfurante/cortante, vant:teste-for, vant:salv-for");
        CATALOG.put("velocidade", "ca+2, vant:salv-des");
        CATALOG.put("haste", "ca+2, vant:salv-des");
        CATALOG.put("acelerado", "ca+2, vant:salv-des");
        CATALOG.put("esquiva", "vant:salv-des");
        CATALOG.put("dodge", "vant:salv-des");
    }

    private static final Pattern TERM = Pattern.compile("^(ca|ac|ataque|atk|attack|salv|salvaguarda|save|teste|check|pericia|dano|damage)\\s*([+-])\\s*(\\d*d\\d+|\\d+)$");

    /** Um bonus numerico ("+1d4", "-2") e de qual efeito veio. */
    public record Term(String source, String sign, String value) {
        public boolean isDice() {
            return value.contains("d");
        }

        public String expression() {
            return sign + value;
        }
    }

    /** Tudo que os efeitos ativos fazem, ja somado. */
    public static final class Mods {
        public int ac;
        public final List<Term> attack = new ArrayList<>();
        public final List<Term> save = new ArrayList<>();
        public final List<Term> check = new ArrayList<>();
        public final List<Term> damage = new ArrayList<>();
        public final Set<String> resist = new LinkedHashSet<>();
        /** "attack", "save", "check", "initiative", com ":ATRIBUTO" opcional, e de qual efeito. */
        public final Map<String, String> advantage = new LinkedHashMap<>();
        public final Map<String, String> disadvantage = new LinkedHashMap<>();

        public boolean isEmpty() {
            return ac == 0 && attack.isEmpty() && save.isEmpty() && check.isEmpty() && damage.isEmpty()
                    && resist.isEmpty() && advantage.isEmpty() && disadvantage.isEmpty();
        }
    }

    static String plain(String text) {
        String n = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).trim();
    }

    /**
     * "Bencao, Anel [ca+1, salv+1], Furia" -> 3 efeitos: separa nas virgulas
     * que estao FORA dos colchetes (as de dentro sao termos do efeito).
     */
    public static List<String> splitList(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        for (char c : text.toCharArray()) {
            if (c == '[') depth++;
            if (c == ']') depth = Math.max(0, depth - 1);
            if (c == ',' && depth == 0) {
                if (!cur.toString().isBlank()) out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (!cur.toString().isBlank()) out.add(cur.toString().trim());
        return out;
    }

    /** Nome do efeito sem os colchetes ("Anel [ca+1]" -> "Anel"). */
    public static String nameOf(String effect) {
        if (effect == null) return "";
        int b = effect.indexOf('[');
        return (b >= 0 ? effect.substring(0, b) : effect).trim();
    }

    /** Os termos do efeito: os colchetes, ou o catalogo pelo nome. null = efeito so de texto. */
    public static String termsOf(String effect) {
        if (effect == null) return null;
        int b = effect.indexOf('[');
        int e = effect.lastIndexOf(']');
        if (b >= 0 && e > b) return effect.substring(b + 1, e);
        String key = plain(nameOf(effect)).replaceAll("\\s*\\(.*\\)$", "");
        return CATALOG.get(key);
    }

    public static Mods parse(List<String> effects) {
        Mods mods = new Mods();
        if (effects == null) return mods;
        for (String effect : effects) {
            String terms = termsOf(effect);
            if (terms == null) continue;
            String source = nameOf(effect);
            for (String raw : terms.split(",")) {
                String t = plain(raw).replace(" ", "");
                if (t.isEmpty()) continue;
                if (t.startsWith("resist:")) {
                    for (String part : t.substring(7).split("[/;]")) {
                        String type = DamageTypes.find(part);
                        if (type != null) mods.resist.add(type);
                    }
                    continue;
                }
                if (t.startsWith("vant:") || t.startsWith("desv:")) {
                    String key = rollKey(t.substring(5));
                    if (key != null) (t.startsWith("vant:") ? mods.advantage : mods.disadvantage).put(key, source);
                    continue;
                }
                Matcher m = TERM.matcher(t);
                if (!m.matches()) continue;
                Term term = new Term(source, m.group(2), m.group(3));
                switch (m.group(1)) {
                    case "ca", "ac" -> {
                        if (!term.isDice()) mods.ac += ("-".equals(term.sign()) ? -1 : 1) * Integer.parseInt(term.value());
                    }
                    case "ataque", "atk", "attack" -> mods.attack.add(term);
                    case "salv", "salvaguarda", "save" -> mods.save.add(term);
                    case "teste", "check", "pericia" -> mods.check.add(term);
                    default -> mods.damage.add(term);
                }
            }
        }
        return mods;
    }

    /** "salv-des" -> "save:DEXTERITY"; "ataque" -> "attack". */
    private static String rollKey(String text) {
        String[] parts = text.split("-", 2);
        String kind = switch (parts[0]) {
            case "ataque", "atk", "attack" -> "attack";
            case "salv", "salvaguarda", "save" -> "save";
            case "teste", "check", "pericia" -> "check";
            case "iniciativa", "initiative" -> "initiative";
            default -> null;
        };
        if (kind == null) return null;
        if (parts.length == 1) return kind;
        Ability a = abilityFromPt(parts[1]);
        return a == null ? kind : kind + ":" + a.name();
    }

    private static Ability abilityFromPt(String s) {
        return switch (s) {
            case "for", "str" -> Ability.STRENGTH;
            case "des", "dex" -> Ability.DEXTERITY;
            case "con" -> Ability.CONSTITUTION;
            case "int" -> Ability.INTELLIGENCE;
            case "sab", "wis" -> Ability.WISDOM;
            case "car", "cha" -> Ability.CHARISMA;
            default -> null;
        };
    }

    /** O que os efeitos fazem com UMA rolagem de d20 (dados de bonus ja rolados). */
    public record RollEffect(boolean advantage, boolean disadvantage, int delta, List<String> notes) {
        public static final RollEffect NONE = new RollEffect(false, false, 0, List.of());
    }

    public static RollEffect forRoll(List<String> effects, RollKind kind, Ability ability,
                                     ToIntFunction<String> roller) {
        if (effects == null || effects.isEmpty() || kind == null) return RollEffect.NONE;
        Mods mods = parse(effects);
        if (mods.isEmpty()) return RollEffect.NONE;
        List<Term> terms = switch (kind) {
            case ATTACK -> mods.attack;
            case SAVING_THROW, DEATH_SAVE -> mods.save;
            case ABILITY_CHECK, INITIATIVE -> mods.check;
        };
        int delta = 0;
        List<String> notes = new ArrayList<>();
        for (Term t : terms) {
            int value = t.isDice() ? roller.applyAsInt(t.value()) : Integer.parseInt(t.value());
            int signed = "-".equals(t.sign()) ? -value : value;
            delta += signed;
            notes.add(t.source() + " " + (signed >= 0 ? "+" : "") + signed + (t.isDice() ? " (" + t.value() + ")" : ""));
        }
        String base = switch (kind) {
            case ATTACK -> "attack";
            case SAVING_THROW, DEATH_SAVE -> "save";
            case ABILITY_CHECK -> "check";
            case INITIATIVE -> "initiative";
        };
        boolean adv = false;
        boolean dis = false;
        for (String key : keysFor(base, ability, kind)) {
            if (mods.advantage.containsKey(key)) {
                adv = true;
                notes.add(mods.advantage.get(key) + ": vantagem");
            }
            if (mods.disadvantage.containsKey(key)) {
                dis = true;
                notes.add(mods.disadvantage.get(key) + ": desvantagem");
            }
        }
        return new RollEffect(adv, dis, delta, notes);
    }

    private static List<String> keysFor(String base, Ability ability, RollKind kind) {
        List<String> keys = new ArrayList<>();
        keys.add(base);
        if (ability != null) keys.add(base + ":" + ability.name());
        // Iniciativa e um teste de DES: vantagem em "teste" tambem vale.
        if (kind == RollKind.INITIATIVE) {
            keys.add("check");
            keys.add("check:" + Ability.DEXTERITY.name());
        }
        return keys;
    }

    public static int acBonus(List<String> effects) {
        return parse(effects).ac;
    }

    /** Termos de dano somados, prontos pra colar no fim da expressao ("+2+1d6"), ou "". */
    public static String damageSuffix(List<String> effects) {
        StringBuilder sb = new StringBuilder();
        for (Term t : parse(effects).damage) sb.append(t.expression());
        return sb.toString();
    }

    public static Set<String> resistances(List<String> effects) {
        return parse(effects).resist;
    }

    /** Resumo pra tela: "CA +5 · ataque +1d4 · resistencia: fogo". "" sem nada. */
    public static String describe(List<String> effects) {
        Mods m = parse(effects);
        List<String> parts = new ArrayList<>();
        if (m.ac != 0) parts.add("CA " + (m.ac > 0 ? "+" : "") + m.ac);
        addTerms(parts, "ataque", m.attack);
        addTerms(parts, "salvaguarda", m.save);
        addTerms(parts, "teste", m.check);
        addTerms(parts, "dano", m.damage);
        if (!m.resist.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (String r : m.resist) names.add(DamageTypes.namePt(r));
            parts.add("resistencia: " + String.join(", ", names));
        }
        for (String k : m.advantage.keySet()) parts.add("vantagem em " + describeKey(k));
        for (String k : m.disadvantage.keySet()) parts.add("desvantagem em " + describeKey(k));
        return String.join("  ·  ", parts);
    }

    private static void addTerms(List<String> parts, String label, List<Term> terms) {
        if (terms.isEmpty()) return;
        StringBuilder sb = new StringBuilder(label + " ");
        for (Term t : terms) sb.append(t.expression());
        parts.add(sb.toString());
    }

    private static String describeKey(String key) {
        String[] p = key.split(":");
        String kind = switch (p[0]) {
            case "attack" -> "ataque";
            case "save" -> "salvaguarda";
            case "check" -> "teste";
            default -> "iniciativa";
        };
        return p.length > 1 ? kind + " de " + Ability.valueOf(p[1]).abbreviationPt() : kind;
    }
}
