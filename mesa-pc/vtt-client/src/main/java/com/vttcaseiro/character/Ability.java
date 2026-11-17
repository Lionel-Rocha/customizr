package com.vttcaseiro.character;

/**
 * As seis habilidades de D&D 5e. Antes isso vivia como um enum privado
 * dentro de AbilityScorePrerequisite (so servia pra "FOR 13 ou mais" em
 * feats) -- virou classe de topo porque agora CharacterSheet tambem
 * precisa dela pra saving throws e pericias (ver Skill.java, que associa
 * cada pericia a uma destas seis).
 */
public enum Ability {
    STRENGTH,
    DEXTERITY,
    CONSTITUTION,
    INTELLIGENCE,
    WISDOM,
    CHARISMA;

    /** Abreviacao de 3 letras em portugues, usada na tela da ficha. */
    public String abbreviationPt() {
        return switch (this) {
            case STRENGTH -> "FOR";
            case DEXTERITY -> "DES";
            case CONSTITUTION -> "CON";
            case INTELLIGENCE -> "INT";
            case WISDOM -> "SAB";
            case CHARISMA -> "CAR";
        };
    }
}
