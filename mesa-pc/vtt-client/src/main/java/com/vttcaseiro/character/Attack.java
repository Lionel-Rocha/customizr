package com.vttcaseiro.character;

/**
 * Uma linha de ataque na ficha ("Espada longa", "+5 pra acertar",
 * "1d8+3 cortante"). De proposito tudo e texto livre em vez de campos
 * numericos separados (bonus de ataque, dado de dano, tipo de dano) --
 * isso casa com o que a ficha oficial de 2024 mostra (uma linha de texto
 * por ataque) e evita ter que modelar toda combinacao possivel de arma,
 * magia e bonus condicional antes de ter uma tela pra editar isso.
 */
public class Attack {
    private String name = "";
    private String attackBonusOrDc = "";
    private String damageAndType = "";
    private String notes = "";

    public Attack() {
    }

    public Attack(String name, String attackBonusOrDc, String damageAndType, String notes) {
        this.name = name;
        this.attackBonusOrDc = attackBonusOrDc;
        this.damageAndType = damageAndType;
        this.notes = notes;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAttackBonusOrDc() { return attackBonusOrDc; }
    public void setAttackBonusOrDc(String attackBonusOrDc) { this.attackBonusOrDc = attackBonusOrDc; }

    public String getDamageAndType() { return damageAndType; }
    public void setDamageAndType(String damageAndType) { this.damageAndType = damageAndType; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    private static final java.util.regex.Pattern BONUS_PATTERN =
            java.util.regex.Pattern.compile("^\\s*([+-]\\s*\\d+)\\s*$");
    private static final java.util.regex.Pattern DICE_EXPR_PATTERN =
            java.util.regex.Pattern.compile("\\d*d\\d+(\\s*[+-]\\s*(\\d*d\\d+|\\d+))*");

    /**
     * O bonus de ataque, se o campo for um numero tipo "+5" ou "-1" (o
     * caso comum). Vazio se o campo for uma CD ("CD 13") ou texto livre --
     * ai a ficha nao oferece rolar o d20, so o dano.
     */
    public java.util.OptionalInt attackBonus() {
        if (attackBonusOrDc == null) return java.util.OptionalInt.empty();
        java.util.regex.Matcher m = BONUS_PATTERN.matcher(attackBonusOrDc);
        if (!m.matches()) return java.util.OptionalInt.empty();
        return java.util.OptionalInt.of(Integer.parseInt(m.group(1).replaceAll("\\s+", "")));
    }

    /**
     * A primeira expressao de dados dentro do texto de dano ("1d8+3
     * cortante" vira "1d8+3"), pronta pro DiceRoller. Vazio se nao tiver
     * nenhum dado no texto.
     */
    public java.util.Optional<String> damageExpression() {
        if (damageAndType == null) return java.util.Optional.empty();
        java.util.regex.Matcher m = DICE_EXPR_PATTERN.matcher(damageAndType);
        return m.find() ? java.util.Optional.of(m.group().replaceAll("\\s+", "")) : java.util.Optional.empty();
    }
}
