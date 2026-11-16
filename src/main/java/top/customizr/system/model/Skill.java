package top.customizr.system.model;

/** Pericias de 5e e o atributo de cada uma. */
public enum Skill {
    ACROBATICS("Acrobatics", AbilityScore.DEXTERITY),
    ANIMAL_HANDLING("Animal Handling", AbilityScore.WISDOM),
    ARCANA("Arcana", AbilityScore.INTELLIGENCE),
    ATHLETICS("Athletics", AbilityScore.STRENGTH),
    DECEPTION("Deception", AbilityScore.CHARISMA),
    HISTORY("History", AbilityScore.INTELLIGENCE),
    INSIGHT("Insight", AbilityScore.WISDOM),
    INTIMIDATION("Intimidation", AbilityScore.CHARISMA),
    INVESTIGATION("Investigation", AbilityScore.INTELLIGENCE),
    MEDICINE("Medicine", AbilityScore.WISDOM),
    NATURE("Nature", AbilityScore.INTELLIGENCE),
    PERCEPTION("Perception", AbilityScore.WISDOM),
    PERFORMANCE("Performance", AbilityScore.CHARISMA),
    PERSUASION("Persuasion", AbilityScore.CHARISMA),
    RELIGION("Religion", AbilityScore.INTELLIGENCE),
    SLEIGHT_OF_HAND("Sleight of Hand", AbilityScore.DEXTERITY),
    STEALTH("Stealth", AbilityScore.DEXTERITY),
    SURVIVAL("Survival", AbilityScore.WISDOM);

    private final String label;
    private final AbilityScore ability;

    Skill(String label, AbilityScore ability) {
        this.label = label;
        this.ability = ability;
    }

    public String getLabel() {
        return label;
    }

    public AbilityScore getAbility() {
        return ability;
    }
}
