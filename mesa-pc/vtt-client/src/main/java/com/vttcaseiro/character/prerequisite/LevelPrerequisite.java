package com.vttcaseiro.character.prerequisite;

import com.vttcaseiro.character.CharacterSheet;

public record LevelPrerequisite(int minimumLevel) implements Prerequisite {
    @Override
    public boolean isMetBy(CharacterSheet sheet) {
        return sheet.getLevel() >= minimumLevel;
    }

    @Override
    public String describe() {
        return "Nivel " + minimumLevel + " ou mais";
    }
}
