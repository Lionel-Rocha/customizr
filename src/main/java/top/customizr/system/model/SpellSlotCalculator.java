package top.customizr.system.model;

import java.util.ArrayList;
import java.util.List;

public final class SpellSlotCalculator {

    private static final int[][] STANDARD_SLOTS = {
            {},
            {2},
            {3},
            {4, 2},
            {4, 3},
            {4, 3, 2},
            {4, 3, 3},
            {4, 3, 3, 1},
            {4, 3, 3, 2},
            {4, 3, 3, 3, 1},
            {4, 3, 3, 3, 2},
            {4, 3, 3, 3, 2, 1},
            {4, 3, 3, 3, 2, 1},
            {4, 3, 3, 3, 2, 1, 1},
            {4, 3, 3, 3, 2, 1, 1},
            {4, 3, 3, 3, 2, 1, 1, 1},
            {4, 3, 3, 3, 2, 1, 1, 1},
            {4, 3, 3, 3, 2, 1, 1, 1, 1},
            {4, 3, 3, 3, 3, 1, 1, 1, 1},
            {4, 3, 3, 3, 3, 2, 1, 1, 1},
            {4, 3, 3, 3, 3, 2, 2, 1, 1}
    };

    private SpellSlotCalculator() {}

    public static List<SpellSlotCount> calculate(List<CharacterClass> characterClasses) {
        if (characterClasses == null) {
            throw new IllegalArgumentException("Character classes are required");
        }

        int sharedCasterLevel = 0;
        List<CharacterClass> pactClasses = new ArrayList<>();
        for (CharacterClass characterClass : characterClasses) {
            if (characterClass == null) {
                throw new IllegalArgumentException("Character class is required");
            }
            int classLevel = characterClass.getLevel();
            SpellcastingProgression progression = characterClass.getDndClass().getSpellcastingProgression();
            switch (progression) {
                case FULL -> sharedCasterLevel += classLevel;
                case HALF -> sharedCasterLevel += classLevel / 2;
                case THIRD -> sharedCasterLevel += classLevel / 3;
                case PACT -> pactClasses.add(characterClass);
                case NONE -> {
                }
            }
        }

        List<SpellSlotCount> slots = new ArrayList<>();
        int[] standardSlots = STANDARD_SLOTS[Math.min(sharedCasterLevel, STANDARD_SLOTS.length - 1)];
        for (int i = 0; i < standardSlots.length; i++) {
            if (standardSlots[i] > 0) {
                slots.add(new SpellSlotCount(SpellSlotPool.STANDARD, null, i + 1, standardSlots[i]));
            }
        }

        for (CharacterClass pactClass : pactClasses) {
            int classLevel = pactClass.getLevel();
            int slotCount = pactSlotCount(classLevel);
            int spellLevel = Math.min(5, (classLevel + 1) / 2);
            slots.add(new SpellSlotCount(
                    SpellSlotPool.PACT,
                    pactClass.getDndClass().getClassName(),
                    spellLevel,
                    slotCount
            ));
        }

        return List.copyOf(slots);
    }

    private static int pactSlotCount(int classLevel) {
        if (classLevel == 1) {
            return 1;
        }
        if (classLevel <= 10) {
            return 2;
        }
        if (classLevel <= 16) {
            return 3;
        }
        return 4;
    }
}
