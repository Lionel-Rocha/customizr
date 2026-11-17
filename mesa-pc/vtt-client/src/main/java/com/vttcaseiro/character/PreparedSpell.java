package com.vttcaseiro.character;

/**
 * Uma linha da tabela "Cantrips & Prepared Spells" da ficha oficial de
 * 2024 (pagina 2): nivel, nome, tempo de conjuracao, alcance, as tres
 * marcacoes C/R/M (concentracao, ritual, componente material exigido) e
 * notas. Texto livre nos campos descritivos, pelo mesmo motivo de
 * {@link Attack}: e o que a ficha impressa mostra, e o texto completo da
 * magia continua no compendio (anexavel na ficha), nao duplicado aqui.
 */
public class PreparedSpell {
    /** 0 = truque (cantrip), 1 a 9 = nivel da magia. */
    private int level;
    private String name = "";
    private String castingTime = "";
    private String range = "";
    private boolean concentration;
    private boolean ritual;
    private boolean materialRequired;
    private String notes = "";
    /** Dados rolados ao conjurar (ex.: "8d6"). Vazio = so manda ao chat. */
    private String dice = "";
    /** Dados a mais por nivel de espaco acima do nivel da magia (ex.: "1d6"). */
    private String dicePerLevel = "";
    /** Tipo de dano (chave de DamageTypes) ou "healing" pra magia de cura. */
    private String damageType = "";
    /** Nome do enum Ability da salvaguarda ("DEXTERITY"), vazio = sem salvaguarda. */
    private String saveAbility = "";
    private boolean halfOnSave;
    /** Magia que pede jogada de ataque (Raio de Fogo): o dano vai em quem foi acertado. */
    private boolean spellAttack;

    public PreparedSpell() {
    }

    public PreparedSpell(int level, String name) {
        setLevel(level);
        this.name = name;
    }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = Math.max(0, Math.min(9, level)); }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCastingTime() { return castingTime; }
    public void setCastingTime(String castingTime) { this.castingTime = castingTime; }

    public String getRange() { return range; }
    public void setRange(String range) { this.range = range; }

    public boolean isConcentration() { return concentration; }
    public void setConcentration(boolean concentration) { this.concentration = concentration; }

    public boolean isRitual() { return ritual; }
    public void setRitual(boolean ritual) { this.ritual = ritual; }

    public boolean isMaterialRequired() { return materialRequired; }
    public void setMaterialRequired(boolean materialRequired) { this.materialRequired = materialRequired; }

    public String getDice() { return dice == null ? "" : dice; }
    public void setDice(String dice) { this.dice = dice == null ? "" : dice.trim(); }

    public String getDicePerLevel() { return dicePerLevel == null ? "" : dicePerLevel; }
    public void setDicePerLevel(String v) { this.dicePerLevel = v == null ? "" : v.trim(); }

    /**
     * Expressao a rolar conjurando no espaco {@code slotLevel}: "8d6" com
     * "1d6" por nivel acima, num espaco 2 niveis acima, vira "10d6". Se os
     * dados por nivel forem de outro tipo, soma como termo separado.
     */
    public String diceForSlot(int slotLevel) {
        String base = getDice();
        if (base.isBlank()) return "";
        int extra = Math.max(0, slotLevel - level);
        String per = getDicePerLevel();
        if (extra == 0 || per.isBlank()) return base;
        java.util.regex.Matcher pm = java.util.regex.Pattern.compile("^(\\d*)d(\\d+)$").matcher(per);
        java.util.regex.Matcher bm = java.util.regex.Pattern.compile("^(\\d*)d(\\d+)(.*)$").matcher(base);
        if (pm.matches() && bm.matches() && pm.group(2).equals(bm.group(2))) {
            int perCount = pm.group(1).isEmpty() ? 1 : Integer.parseInt(pm.group(1));
            int baseCount = bm.group(1).isEmpty() ? 1 : Integer.parseInt(bm.group(1));
            return (baseCount + perCount * extra) + "d" + bm.group(2) + bm.group(3);
        }
        if (pm.matches()) {
            int perCount = pm.group(1).isEmpty() ? 1 : Integer.parseInt(pm.group(1));
            return base + "+" + (perCount * extra) + "d" + pm.group(2);
        }
        return base;
    }

    public String getDamageType() { return damageType == null ? "" : damageType; }
    public void setDamageType(String v) { this.damageType = v == null ? "" : v.trim(); }

    public boolean isHealing() { return "healing".equals(getDamageType()); }

    public String getSaveAbility() { return saveAbility == null ? "" : saveAbility; }
    public void setSaveAbility(String v) { this.saveAbility = v == null ? "" : v.trim(); }

    public Ability saveAbilityOrNull() {
        try {
            return getSaveAbility().isBlank() ? null : Ability.valueOf(getSaveAbility());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean isHalfOnSave() { return halfOnSave; }
    public void setHalfOnSave(boolean halfOnSave) { this.halfOnSave = halfOnSave; }

    public boolean isSpellAttack() { return spellAttack; }
    public void setSpellAttack(boolean spellAttack) { this.spellAttack = spellAttack; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    /** "C R M" com so as marcacoes ligadas, pra coluna compacta da tabela. */
    public String flagsText() {
        StringBuilder sb = new StringBuilder();
        if (concentration) sb.append("C ");
        if (ritual) sb.append("R ");
        if (materialRequired) sb.append("M");
        return sb.toString().trim();
    }

    public String levelText() {
        return level == 0 ? "Truque" : level + "º";
    }

    public String toChatText() {
        StringBuilder sb = new StringBuilder(name).append(" (").append(level == 0 ? "truque" : "nivel " + level).append(")");
        if (castingTime != null && !castingTime.isBlank()) sb.append(" - ").append(castingTime);
        if (range != null && !range.isBlank()) sb.append(" - alcance ").append(range);
        String flags = flagsText();
        if (!flags.isEmpty()) sb.append(" [").append(flags).append("]");
        if (notes != null && !notes.isBlank()) sb.append(": ").append(notes);
        return sb.toString();
    }
}
