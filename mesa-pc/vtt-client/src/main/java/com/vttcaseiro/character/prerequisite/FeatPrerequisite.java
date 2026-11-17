package com.vttcaseiro.character.prerequisite;

import com.vttcaseiro.character.CharacterSheet;

/** Exige que o personagem ja tenha outro feat (comparado pelo nome --
 * sem um compendio de feats com id estavel ainda, e a unica referencia
 * que da pra usar; quando o compendio existir, isto pode trocar pra usar
 * um id em vez do nome, sem mudar a interface Prerequisite). */
public record FeatPrerequisite(String requiredFeatName) implements Prerequisite {
    @Override
    public boolean isMetBy(CharacterSheet sheet) {
        return sheet.getFeats().stream()
                .anyMatch(f -> requiredFeatName.equalsIgnoreCase(f.getName()));
    }

    @Override
    public String describe() {
        return "Ter o feat \"" + requiredFeatName + "\"";
    }
}
