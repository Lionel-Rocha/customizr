package com.vttcaseiro.character;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Regras novas da aba Combate: dados de vida por tipo, teste contra a morte que se aplica, CA sem a caixa de escudo. */
class SheetCombatRulesTest {

    @Test
    void hitDicePerTypeForMulticlass() {
        CharacterSheet s = new CharacterSheet("Aria");
        s.setLevel(7);
        s.setHitDie("d10");
        assertEquals(Map.of("d10", 7), s.hitDiceTotals(), "ficha antiga: dado da classe x nivel");

        s.setHitDiceCount("d10", 4);
        s.setHitDiceCount("d8", 3);
        assertEquals(java.util.List.of("d10", "d8"), java.util.List.copyOf(s.hitDiceTotals().keySet()), "do maior pro menor");
        assertEquals(7, s.hitDiceMax());

        assertTrue(s.spendHitDie("d8"));
        assertEquals(2, s.hitDiceRemainingOf("d8"));
        assertEquals("d10", s.spendLargestHitDie(), "descanso em grupo gasta o maior primeiro");
        assertEquals(5, s.hitDiceRemaining());
        assertFalse(s.spendHitDie("d12"), "nao tem d12");

        s.longRest();
        assertEquals(7, s.hitDiceRemaining(), "descanso longo devolve tudo");

        s.setHitDiceCount("d8", 0);
        assertEquals(Map.of("d10", 4), s.hitDiceTotals());
    }

    @Test
    void legacySpentHitDiceGoToTheLargestDie() {
        CharacterSheet s = new CharacterSheet("Velho");
        s.setLevel(3);
        s.setHitDie("d8");
        s.setHitDiceSpent(2);
        assertEquals(1, s.hitDiceRemainingOf("d8"));
        assertEquals(2, s.getHitDiceSpent());
    }

    @Test
    void deathSavesMarkAndApplyTheirEffect() {
        CharacterSheet s = new CharacterSheet("Caido");
        s.setHpMax(20);
        s.setHpCurrent(10);
        assertEquals(CharacterSheet.DeathSaveOutcome.NONE, s.applyDeathSave(15), "com PV nao rola");

        s.setHpCurrent(0);
        assertEquals(CharacterSheet.DeathSaveOutcome.SUCCESS, s.applyDeathSave(12));
        assertEquals(CharacterSheet.DeathSaveOutcome.FAILURE, s.applyDeathSave(1));
        assertEquals(2, s.getDeathSaveFailures(), "1 natural = duas falhas");
        assertEquals(CharacterSheet.DeathSaveOutcome.SUCCESS, s.applyDeathSave(10));
        assertEquals(CharacterSheet.DeathSaveOutcome.STABLE, s.applyDeathSave(19), "terceiro sucesso");
        assertEquals(0, s.getDeathSaveSuccesses(), "estavel zera os testes");
        assertEquals(0, (int) s.getHpCurrent(), "estavel continua a 0 PV");

        assertEquals(CharacterSheet.DeathSaveOutcome.REVIVED, s.applyDeathSave(20));
        assertEquals(1, (int) s.getHpCurrent(), "20 natural volta com 1 PV");

        s.setHpCurrent(0);
        s.applyDeathSave(5);
        s.applyDeathSave(4);
        assertEquals(CharacterSheet.DeathSaveOutcome.DEAD, s.applyDeathSave(3));
        assertEquals(CharacterSheet.DeathSaveOutcome.NONE, s.applyDeathSave(20), "morto nao rola mais");
    }

    @Test
    void oldShieldCheckboxMovesIntoTheArmorClass() {
        CharacterSheet s = new CharacterSheet("Escudeiro");
        s.setArmorClass(16);
        s.setShieldEquipped(true);
        assertEquals(18, s.totalArmorClass());
        assertTrue(s.migrateShieldIntoArmorClass());
        assertEquals(18, (int) s.getArmorClass());
        assertFalse(s.isShieldEquipped());
        assertEquals(18, s.totalArmorClass(), "a CA nao muda");
        assertFalse(s.migrateShieldIntoArmorClass());
    }

    @Test
    void initiativeDiceAreKept() {
        CharacterSheet s = new CharacterSheet("Rapida");
        s.setInitiativeDice(" 1d4 ");
        assertEquals("1d4", s.getInitiativeDice());
        s.setInitiativeDice("");
        assertEquals("", s.getInitiativeDice());
    }
}
