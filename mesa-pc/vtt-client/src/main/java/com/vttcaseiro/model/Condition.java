package com.vttcaseiro.model;

/**
 * As condicoes (status effects) do D&D 5e que o VTT sabe marcar num token
 * (ver TokenModel.conditions e view.TokenNode -- os selinhos coloridos em
 * cima do token). Nao e um texto livre de proposito: cada uma tem uma
 * abreviacao de 2 letras fixa (pro selinho no token, que precisa ser
 * pequeno) e uma cor, igual Roll20/Foundry fazem. O nome do enum (ex.:
 * "POISONED") e o que fica salvo em TokenModel.conditions -- o texto em
 * portugues (label()) pode mudar sem quebrar saves antigos.
 * <p>
 * Exaustao nao tem nivel aqui (so "tem ou nao tem") -- o 5e 2024 tem 6
 * niveis com efeitos acumulativos, mas isso e complexo o bastante pra
 * merecer sua propria tela depois (ver HANDOFF.md).
 */
public enum Condition {
    BLINDED("Cego", "CG", "#5B6B7A"),
    CHARMED("Enfeiticado", "EF", "#C77DD4"),
    DEAFENED("Surdo", "SD", "#8C8C8C"),
    EXHAUSTION("Exausto", "EX", "#7A5230"),
    FRIGHTENED("Assustado", "AS", "#6E4FA8"),
    GRAPPLED("Agarrado", "AG", "#B5651D"),
    INCAPACITATED("Incapacitado", "IN", "#9A4F4F"),
    INVISIBLE("Invisivel", "IV", "#4F8FA8"),
    PARALYZED("Paralisado", "PR", "#D9544C"),
    PETRIFIED("Petrificado", "PT", "#707070"),
    POISONED("Envenenado", "PO", "#4E9A4E"),
    PRONE("Caido", "CD", "#C9802A"),
    RESTRAINED("Restringido", "RS", "#A85C32"),
    STUNNED("Atordoado", "AT", "#E0963C"),
    UNCONSCIOUS("Inconsciente", "UC", "#2E2E2E");

    private final String label;
    private final String abbreviation;
    private final String colorHex;

    Condition(String label, String abbreviation, String colorHex) {
        this.label = label;
        this.abbreviation = abbreviation;
        this.colorHex = colorHex;
    }

    public String label() { return label; }
    public String abbreviation() { return abbreviation; }
    public String colorHex() { return colorHex; }
}
