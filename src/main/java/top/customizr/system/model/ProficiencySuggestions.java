package top.customizr.system.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Le, da entrada original do 5etools (o {@code metadata} de especie, antecedente e classe), os idiomas, ferramentas,
 * armas e armaduras que o personagem ganha. O que e escolha ("qualquer idioma padrao", "um instrumento") vira um
 * aviso em {@code choices}, pra o jogador decidir.
 */
public final class ProficiencySuggestions {

    public record Suggested(List<String> languages, List<String> tools, List<String> weapons, List<String> armor,
                            List<String> choices) {}

    /** {@code {@item shield|phb|shields}} -> "shields"; {@code {@item thieves' tools|xphb}} -> "thieves' tools". */
    private static final Pattern TAG = Pattern.compile("\\{@\\w+ ([^}|]+)(?:\\|[^}|]*)?(?:\\|([^}]+))?}");

    private ProficiencySuggestions() {}

    public static Suggested of(Sheet sheet) {
        Set<String> languages = new LinkedHashSet<>();
        Set<String> tools = new LinkedHashSet<>();
        Set<String> weapons = new LinkedHashSet<>();
        Set<String> armor = new LinkedHashSet<>();
        Set<String> choices = new LinkedHashSet<>();
        Character character = sheet.getCharacter();

        if (character != null && character.getSpecies() != null) {
            Map<String, Object> m = character.getSpecies().getMetadata();
            String from = character.getSpecies().getName();
            readFlags(get(m, "languageProficiencies"), languages, choices, from, "language");
            readFlags(get(m, "toolProficiencies"), tools, choices, from, "tool");
            readFlags(get(m, "weaponProficiencies"), weapons, choices, from, "weapon");
            readFlags(get(m, "armorProficiencies"), armor, choices, from, "armor");
        }
        if (character != null && character.getBackground() != null) {
            Map<String, Object> m = character.getBackground().getMetadata();
            String from = character.getBackground().getName();
            readFlags(get(m, "languageProficiencies"), languages, choices, from, "language");
            readFlags(get(m, "toolProficiencies"), tools, choices, from, "tool");
        }
        // primeira classe: proficiencias iniciais; as outras (multiclasse): so o que a multiclasse da
        boolean first = true;
        for (CharacterClass cc : sheet.getClasses()) {
            Object cls = get(cc.getDndClass().getMetadata(), "class");
            Object block = first ? get(cls, "startingProficiencies") : get(get(cls, "multiclassing"), "proficienciesGained");
            String from = cc.getDndClass().getClassName();
            readList(get(block, "armor"), armor, ProficiencySuggestions::armorName);
            readList(get(block, "weapons"), weapons, ProficiencySuggestions::weaponName);
            readList(get(block, "tools"), tools, ProficiencySuggestions::title);
            readFlags(get(block, "toolProficiencies"), tools, choices, from, "tool");
            first = false;
        }
        return new Suggested(List.copyOf(languages), List.copyOf(tools), List.copyOf(weapons), List.copyOf(armor),
                List.copyOf(choices));
    }

    /** Lista de objetos {@code {"common": true, "anyStandard": 1}}: true = ganhou; numero = escolha. */
    private static void readFlags(Object value, Set<String> into, Set<String> choices, String from, String what) {
        if (!(value instanceof List<?> list)) return;
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> map)) continue;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = String.valueOf(e.getKey());
                if (Boolean.TRUE.equals(e.getValue())) {
                    into.add(title(untag(key)));
                } else if (e.getValue() instanceof Number n) {
                    choices.add(from + ": choose " + n.intValue() + " " + what + " (" + readable(key) + ")");
                } else if (key.equals("choose") && e.getValue() instanceof Map<?, ?> choose) {
                    Object options = choose.get("from");
                    Object count = choose.get("count");
                    choices.add(from + ": choose " + (count instanceof Number c ? c.intValue() : 1) + " " + what
                            + (options instanceof List<?> l ? " (" + String.join(", ", l.stream().map(o -> title(untag(String.valueOf(o)))).toList()) + ")" : ""));
                }
            }
        }
    }

    /** Lista de textos: {@code ["light", "{@item shield|phb|shields}"]}; o que for objeto (escolha) e ignorado. */
    private static void readList(Object value, Set<String> into, java.util.function.UnaryOperator<String> name) {
        if (!(value instanceof List<?> list)) return;
        for (Object o : list) {
            if (o instanceof String s && !s.isBlank()) into.add(name.apply(untag(s)));
        }
    }

    private static String armorName(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "light" -> "Light armor";
            case "medium" -> "Medium armor";
            case "heavy" -> "Heavy armor";
            case "shield", "shields" -> "Shields";
            default -> title(raw);
        };
    }

    private static String weaponName(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "simple" -> "Simple weapons";
            case "martial" -> "Martial weapons";
            default -> title(raw);
        };
    }

    public static String untag(String text) {
        Matcher m = TAG.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) m.appendReplacement(out, Matcher.quoteReplacement(m.group(2) != null ? m.group(2) : m.group(1)));
        m.appendTail(out);
        return out.toString().trim();
    }

    /** "thieves' tools" -> "Thieves' Tools". */
    static String title(String text) {
        StringBuilder out = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(java.lang.Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    /** "anyStandard" -> "any standard". */
    private static String readable(String key) {
        return key.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
    }

    private static Object get(Object map, String key) {
        return map instanceof Map<?, ?> m ? m.get(key) : null;
    }
}
