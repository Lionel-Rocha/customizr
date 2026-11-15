package top.customizr.system.model;

public enum SpellLevel {
    CANTRIP(0),
    FIRST(1),
    SECOND(2),
    THIRD(3),
    FOURTH(4),
    FIFTH(5),
    SIXTH(6),
    SEVENTH(7),
    EIGHTH(8),
    NINTH(9);

    private final int value;

    SpellLevel(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
