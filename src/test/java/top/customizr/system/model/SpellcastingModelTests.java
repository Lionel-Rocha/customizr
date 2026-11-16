package top.customizr.system.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpellcastingModelTests {

    @Test
    void calculatesSharedSlotsFromMulticlassProgressions() {
        CharacterClass fullCaster = characterClass("full", 3);
        CharacterClass halfCaster = characterClass("half", 4);

        List<SpellSlotCount> slots = SpellSlotCalculator.calculate(List.of(fullCaster, halfCaster));

        assertEquals(List.of(
                new SpellSlotCount(SpellSlotPool.STANDARD, null, 1, 4),
                new SpellSlotCount(SpellSlotPool.STANDARD, null, 2, 3),
                new SpellSlotCount(SpellSlotPool.STANDARD, null, 3, 2)
        ), slots);
    }

    @Test
    void calculatesPactSlotsSeparatelyFromSharedSlots() {
        CharacterClass pactCaster = characterClass("pact", 5);

        List<SpellSlotCount> slots = SpellSlotCalculator.calculate(List.of(pactCaster));

        assertEquals(List.of(
                new SpellSlotCount(SpellSlotPool.PACT, "Test pact", 3, 2)
        ), slots);
    }

    @Test
    void preparedSpellsMustBeOnTheClassListAndCanBeRemoved() {
        Source source = new Source("Test Source", "TST");
        Duration duration = new Duration(1, DurationUnit.MINUTE, true);
        Spell available = new Spell(
                "Test Spell", SpellSchool.Evocation, SpellLevel.FIRST, Time.Action,
                60, Set.of(Components.Verbal), duration, source, "description"
        );
        assertEquals(duration, available.getDuration());
        Spell unavailable = new Spell(
                "Unavailable Spell", SpellSchool.Illusion, SpellLevel.FIRST, Time.Action,
                30, Set.of(), new Duration(0, DurationUnit.INSTANTANEOUS, false), source, "unavailable"
        );
        DndClass dndClass = dndClass("full", "Test wizard");
        dndClass.addAvailableSpell(available);
        CharacterClass characterClass = new CharacterClass(null, dndClass, 1);

        characterClass.prepareSpell(available);
        assertEquals(Set.of(available), characterClass.getPreparedSpells());
        assertThrows(IllegalArgumentException.class, () -> characterClass.prepareSpell(unavailable));

        characterClass.unprepareSpell(available.getSpellId());
        assertEquals(Set.of(), characterClass.getPreparedSpells());
    }

    @Test
    void durationSupportsInstantTimedAndConcentrationForms() {
        assertEquals(DurationUnit.INSTANTANEOUS,
                new Duration(0, DurationUnit.INSTANTANEOUS, false).getUnit());
        assertEquals(2, new Duration(2, DurationUnit.ROUND, false).getAmount());
        assertEquals(DurationUnit.CONCENTRATION,
                new Duration(0, DurationUnit.CONCENTRATION, true).getUnit());
        assertEquals(3, new Duration(3, DurationUnit.HOUR, true).getAmount());
        assertEquals(2, new Duration(2, DurationUnit.DAY, false).getAmount());
        assertThrows(IllegalArgumentException.class,
                () -> new Duration(0, DurationUnit.MINUTE, false));
    }

    private CharacterClass characterClass(String progression, int level) {
        return new CharacterClass(null, dndClass(progression, "Test " + progression), level);
    }

    private DndClass dndClass(String progression, String name) {
        return new DndClass(
                name, "TST", 1, 8, 2, null, null, null, null,
                null, progression, null, null, null
        );
    }
}
