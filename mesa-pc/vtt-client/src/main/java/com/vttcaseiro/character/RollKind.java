package com.vttcaseiro.character;

/** Tipo de teste de d20 -- decide quais condicoes/efeitos se aplicam. */
public enum RollKind {
    /** Teste de atributo ou de pericia. */
    ABILITY_CHECK,
    SAVING_THROW,
    ATTACK,
    /** Em 2024 iniciativa e um teste de Destreza (com regras proprias de
     * Invisivel/Incapacitado). */
    INITIATIVE,
    DEATH_SAVE
}
