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
    void oneMarkCyclesProficiencyExpertiseAndJackOfAllTrades() {
        CharacterSheet s = new CharacterSheet("Bardo");
        s.setLevel(5);           // proficiencia +3
        s.setDexterity(14);      // +2
        assertEquals(2, s.skillBonus(Skill.STEALTH));
        assertEquals(1, s.cycleSkillLevel(Skill.STEALTH));
        assertEquals(5, s.skillBonus(Skill.STEALTH), "proficiente");
        assertEquals(2, s.cycleSkillLevel(Skill.STEALTH));
        assertEquals(8, s.skillBonus(Skill.STEALTH), "especialista");
        assertEquals(3, s.cycleSkillLevel(Skill.STEALTH));
        assertEquals(3, s.skillBonus(Skill.STEALTH), "pau pra toda obra: metade de 3 = 1");
        assertFalse(s.isSkillProficient(Skill.STEALTH));
        assertEquals(0, s.cycleSkillLevel(Skill.STEALTH));
        assertEquals(2, s.skillBonus(Skill.STEALTH));

        // marcar proficiente por fora tira o pau pra toda obra
        s.setSkillLevel(Skill.ARCANA, 3);
        s.setSkillProficient(Skill.ARCANA, true);
        assertEquals(1, s.skillLevel(Skill.ARCANA));
    }

    @Test
    void extraBonusesPerAbilityAndForAllSkillsAndSaves() {
        CharacterSheet s = new CharacterSheet("Extra");
        s.setLevel(1);          // +2
        s.setDexterity(12);     // +1
        s.setWisdom(10);
        s.setSkillBonusFor(Ability.DEXTERITY, 1);
        s.setSkillBonusAll(2);
        s.setSaveBonusAll(1);
        assertEquals(1 + 1 + 2, s.skillBonus(Skill.ACROBATICS), "mod + extra de DES + extra geral");
        assertEquals(0 + 2, s.skillBonus(Skill.PERCEPTION), "extra de DES nao vale pra SAB");
        assertEquals(10 + 2, s.passivePerception(), "a passiva acompanha");
        assertEquals(1 + 1, s.savingThrowBonus(Ability.DEXTERITY), "salvaguarda: mod + extra geral (sem o das pericias)");
        s.setSavingThrowProficient(Ability.DEXTERITY, true);
        assertEquals(1 + 1 + 2, s.savingThrowBonus(Ability.DEXTERITY));
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
