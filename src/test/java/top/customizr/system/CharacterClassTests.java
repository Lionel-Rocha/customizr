package top.customizr.system;

import org.junit.jupiter.api.Test;
import top.customizr.system.model.Campaign;
import top.customizr.system.model.Character;
import top.customizr.system.model.CharacterClass;
import top.customizr.system.model.Feature;
import top.customizr.system.model.FeatureType;
import top.customizr.system.model.DndClass;
import top.customizr.system.model.Subclass;
import top.customizr.system.model.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CharacterClassTests {

    @Test
    void characterChoosesOneAvailableSubclassAndSelectsGrantedFeatures() {
        DndClass fighter = new DndClass("Fighter", 10, 3, null);
        Feature fightingStyle = new Feature("Fighting Style", "", "Combat", FeatureType.CHOICE, null);
        Feature archery = new Feature("Archery", "", "Combat", FeatureType.CHOICE, null);
        Feature championFeature = new Feature("Improved Critical", "", "Combat", FeatureType.AUTOMATIC, null);
        fighter.addFeature(fightingStyle, 1, "fighting-style");
        fighter.addFeature(archery, 1, "fighting-style");
        Subclass champion = fighter.addSubclass("Champion", 3, null);
        champion.addFeature(championFeature, 3);

        User owner = new User("owner", "password");
        Character character = new Character("Aria", new Campaign("Campaign", owner), owner);
        CharacterClass characterClass = character.getSheet().addClass(fighter, 5);

        characterClass.chooseSubclass(champion, 3);
        characterClass.selectFeature(fightingStyle, 1);

        assertEquals(champion, characterClass.getSubclass());
        assertEquals(3, characterClass.getSubclassChosenAtLevel());
        assertEquals(1, characterClass.getFeatureSelections().getFirst().getLevel());
        assertEquals(championFeature, champion.getFeatures().getFirst().getFeature());
        assertThrows(IllegalArgumentException.class, () -> characterClass.selectFeature(archery, 1));
    }

    @Test
    void subclassCannotBeChosenBeforeUnlockOrChangedAfterSelection() {
        DndClass fighter = new DndClass("Fighter", 10, 3, null);
        Subclass champion = fighter.addSubclass("Champion", 3, null);
        Subclass battleMaster = fighter.addSubclass("Battle Master", 3, null);

        User owner = new User("owner", "password");
        Character character = new Character("Aria", new Campaign("Campaign", owner), owner);
        CharacterClass characterClass = character.getSheet().addClass(fighter, 5);

        assertThrows(IllegalArgumentException.class, () -> characterClass.chooseSubclass(champion, 2));
        characterClass.chooseSubclass(champion, 3);
        assertThrows(IllegalStateException.class, () -> characterClass.chooseSubclass(battleMaster, 4));
    }
}
