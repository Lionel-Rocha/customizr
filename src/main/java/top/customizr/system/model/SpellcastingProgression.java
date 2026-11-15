package top.customizr.system.model;

import java.util.Locale;

public enum SpellcastingProgression {
    NONE,
    FULL,
    HALF,
    THIRD,
    PACT;

    public static SpellcastingProgression from(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }

        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/]", "");
        return switch (normalized) {
            case "none", "0" -> NONE;
            case "full", "fullcaster", "1" -> FULL;
            case "half", "halfcaster", "1/2" -> HALF;
            case "third", "thirdcaster", "1/3" -> THIRD;
            case "pact", "pactmagic" -> PACT;
            default -> throw new IllegalArgumentException("Unsupported spellcasting progression: " + value);
        };
    }
}
