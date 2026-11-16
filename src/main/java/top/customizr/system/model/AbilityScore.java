package top.customizr.system.model;

/** Os seis atributos, com a chave usada em {@link Attributes#getModifier(String)}. */
public enum AbilityScore {
    STRENGTH("strength"),
    DEXTERITY("dexterity"),
    CONSTITUTION("constitution"),
    INTELLIGENCE("intelligence"),
    WISDOM("wisdom"),
    CHARISMA("charisma");

    private final String key;

    AbilityScore(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
