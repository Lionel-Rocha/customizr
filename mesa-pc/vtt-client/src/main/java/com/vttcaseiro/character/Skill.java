package com.vttcaseiro.character;

/**
 * As 18 pericias oficiais de D&D 5e, cada uma ligada a habilidade que ela
 * usa pra rolar (isso e fixo nas regras -- Furtividade e sempre DES,
 * Intimidacao e sempre CAR, etc). CharacterSheet guarda SO quais dessas o
 * personagem e proficiente/tem expertise (ver getSkillProficiencies()),
 * nao duplica a habilidade aqui.
 */
public enum Skill {
    ATHLETICS(Ability.STRENGTH, "Atletismo"),
    ACROBATICS(Ability.DEXTERITY, "Acrobacia"),
    SLEIGHT_OF_HAND(Ability.DEXTERITY, "Prestidigitação"),
    STEALTH(Ability.DEXTERITY, "Furtividade"),
    ARCANA(Ability.INTELLIGENCE, "Arcanismo"),
    HISTORY(Ability.INTELLIGENCE, "História"),
    INVESTIGATION(Ability.INTELLIGENCE, "Investigação"),
    NATURE(Ability.INTELLIGENCE, "Natureza"),
    RELIGION(Ability.INTELLIGENCE, "Religião"),
    ANIMAL_HANDLING(Ability.WISDOM, "Lidar com Animais"),
    INSIGHT(Ability.WISDOM, "Intuição"),
    MEDICINE(Ability.WISDOM, "Medicina"),
    PERCEPTION(Ability.WISDOM, "Percepção"),
    SURVIVAL(Ability.WISDOM, "Sobrevivência"),
    DECEPTION(Ability.CHARISMA, "Enganação"),
    INTIMIDATION(Ability.CHARISMA, "Intimidação"),
    PERFORMANCE(Ability.CHARISMA, "Atuação"),
    PERSUASION(Ability.CHARISMA, "Persuasão");

    private final Ability ability;
    private final String displayNamePt;

    Skill(Ability ability, String displayNamePt) {
        this.ability = ability;
        this.displayNamePt = displayNamePt;
    }

    public Ability getAbility() { return ability; }

    public String getDisplayNamePt() { return displayNamePt; }
}
