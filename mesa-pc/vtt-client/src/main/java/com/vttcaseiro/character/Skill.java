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
    SLEIGHT_OF_HAND(Ability.DEXTERITY, "Prestidigitacao"),
    STEALTH(Ability.DEXTERITY, "Furtividade"),
    ARCANA(Ability.INTELLIGENCE, "Arcanismo"),
    HISTORY(Ability.INTELLIGENCE, "Historia"),
    INVESTIGATION(Ability.INTELLIGENCE, "Investigacao"),
    NATURE(Ability.INTELLIGENCE, "Natureza"),
    RELIGION(Ability.INTELLIGENCE, "Religiao"),
    ANIMAL_HANDLING(Ability.WISDOM, "Lidar com Animais"),
    INSIGHT(Ability.WISDOM, "Intuicao"),
    MEDICINE(Ability.WISDOM, "Medicina"),
    PERCEPTION(Ability.WISDOM, "Percepcao"),
    SURVIVAL(Ability.WISDOM, "Sobrevivencia"),
    DECEPTION(Ability.CHARISMA, "Enganacao"),
    INTIMIDATION(Ability.CHARISMA, "Intimidacao"),
    PERFORMANCE(Ability.CHARISMA, "Atuacao"),
    PERSUASION(Ability.CHARISMA, "Persuasao");

    private final Ability ability;
    private final String displayNamePt;

    Skill(Ability ability, String displayNamePt) {
        this.ability = ability;
        this.displayNamePt = displayNamePt;
    }

    public Ability getAbility() { return ability; }

    public String getDisplayNamePt() { return displayNamePt; }
}
