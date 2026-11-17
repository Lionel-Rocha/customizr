package com.vttcaseiro.character.prerequisite;

import com.vttcaseiro.character.Ability;
import com.vttcaseiro.character.CharacterSheet;

public record AbilityScorePrerequisite(Ability ability, int minimumScore) implements Prerequisite {

    @Override
    public boolean isMetBy(CharacterSheet sheet) {
        return sheet.abilityScore(ability) >= minimumScore;
    }

    @Override
    public String describe() {
        return ability.abbreviationPt() + " " + minimumScore + " ou mais";
    }
}
