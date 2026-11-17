package top.customizr.system.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Confere os pre-requisitos de um talento do catalogo usando a entrada original do 5etools
 * ({@code metadata.prerequisite}): cada item da lista e uma alternativa ("ou"); dentro dela tudo
 * precisa valer. Entende nivel, atributo minimo, especie, outro talento e conjuracao; o que nao
 * da pra conferir automaticamente (ex.: "other") e ignorado.
 */
public final class FeatPrerequisites {

    private FeatPrerequisites() {}

    /** Lista vazia = pode pegar; senao, o motivo de cada alternativa que falhou. */
    @SuppressWarnings("unchecked")
    public static List<String> unmet(FeatDefinition feat, Character character) {
        Object raw = feat.getMetadata() == null ? null : feat.getMetadata().get("prerequisite");
        if (!(raw instanceof List<?> options) || options.isEmpty()) {
            return List.of();
        }
        List<String> failures = new ArrayList<>();
        for (Object option : options) {
            if (!(option instanceof Map<?, ?> map)) continue;
            List<String> missing = check((Map<String, Object>) map, character);
            if (missing.isEmpty()) return List.of(); // uma alternativa basta
            failures.add(String.join(", ", missing));
        }
        return failures;
    }

    @SuppressWarnings("unchecked")
    private static List<String> check(Map<String, Object> option, Character character) {
        Sheet sheet = character.getSheet();
        List<String> missing = new ArrayList<>();
        Object level = option.get("level");
        Integer needed = level instanceof Number n ? Integer.valueOf(n.intValue())
                : level instanceof Map<?, ?> m && m.get("level") instanceof Number n2 ? Integer.valueOf(n2.intValue()) : null;
        if (needed != null && Math.max(1, sheet.getTotalLevel()) < needed) {
            missing.add("level " + needed);
        }
        if (option.get("ability") instanceof List<?> abilities && !abilities.isEmpty()) {
            boolean any = false;
            List<String> wanted = new ArrayList<>();
            for (Object a : abilities) {
                if (!(a instanceof Map<?, ?> m)) continue;
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (!(e.getValue() instanceof Number min)) continue;
                    String key = String.valueOf(e.getKey()).toUpperCase(Locale.ROOT);
                    wanted.add(key + " " + min.intValue());
                    if (score(sheet, key) >= min.intValue()) any = true;
                }
            }
            if (!any && !wanted.isEmpty()) missing.add(String.join(" or ", wanted));
        }
        if (option.get("race") instanceof List<?> races && !races.isEmpty()) {
            String species = character.getSpecies() == null ? "" : character.getSpecies().getName().toLowerCase(Locale.ROOT);
            boolean ok = races.stream().anyMatch(r -> r instanceof Map<?, ?> m && m.get("name") != null
                    && species.contains(String.valueOf(m.get("name")).toLowerCase(Locale.ROOT)));
            if (!ok) missing.add("species " + races.stream().map(r -> r instanceof Map<?, ?> m ? String.valueOf(m.get("name")) : "?").toList());
        }
        if (option.get("feat") instanceof List<?> feats && !feats.isEmpty()) {
            for (Object f : feats) {
                String name = String.valueOf(f);
                String plain = (name.contains("|") ? name.substring(0, name.indexOf('|')) : name).toLowerCase(Locale.ROOT);
                boolean has = sheet.getCatalogFeats().stream()
                        .anyMatch(cf -> cf.getFeat().getName().toLowerCase(Locale.ROOT).equals(plain));
                if (!has) missing.add("feat " + plain);
            }
        }
        if (option.containsKey("spellcasting") || option.containsKey("spellcasting2020") || option.containsKey("spellcastingFeature")) {
            boolean caster;
            try {
                caster = !sheet.getSpellSlots().isEmpty();
            } catch (IllegalArgumentException ex) {
                caster = false;
            }
            if (!caster) missing.add("spellcasting");
        }
        return missing;
    }

    private static int score(Sheet sheet, String key) {
        Attributes a = sheet.getAttributes();
        if (a == null) return 10;
        return switch (key) {
            case "STR" -> a.getStrength();
            case "DEX" -> a.getDexterity();
            case "CON" -> a.getConstitution();
            case "INT" -> a.getIntelligence();
            case "WIS" -> a.getWisdom();
            case "CHA" -> a.getCharisma();
            default -> 0;
        };
    }
}
