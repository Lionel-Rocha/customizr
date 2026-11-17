package com.vttcaseiro.character;

import com.vttcaseiro.dice.DiceException;
import com.vttcaseiro.dice.DiceRoller;
import com.vttcaseiro.mob.MobSheet;
import com.vttcaseiro.model.Condition;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.service.TokenLink;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Condicoes/exaustao nas rolagens, dano com concentracao, e token<->ficha. */
class EffectsAndLinkTest {

    @Test
    void envenenadoDaDesvantagemEmAtaqueETesteMasNaoEmSalvaguarda() {
        CharacterSheet s = new CharacterSheet("A");
        s.setCondition(Condition.POISONED, true);
        assertEquals(RollMode.DISADVANTAGE, s.rollAdjustment(RollKind.ATTACK, null, RollMode.NORMAL).mode());
        assertEquals(RollMode.DISADVANTAGE, s.rollAdjustment(RollKind.ABILITY_CHECK, Ability.WISDOM, RollMode.NORMAL).mode());
        assertEquals(RollMode.NORMAL, s.rollAdjustment(RollKind.SAVING_THROW, Ability.CONSTITUTION, RollMode.NORMAL).mode());
    }

    @Test
    void vantagemEDesvantagemSeCancelam() {
        CharacterSheet s = new CharacterSheet("A");
        s.setCondition(Condition.POISONED, true);
        // Jogador marcou vantagem, Envenenado da desvantagem: rolagem normal.
        assertEquals(RollMode.NORMAL, s.rollAdjustment(RollKind.ATTACK, null, RollMode.ADVANTAGE).mode());
        s.setCondition(Condition.INVISIBLE, true);
        assertEquals(RollMode.NORMAL, s.rollAdjustment(RollKind.ATTACK, null, RollMode.NORMAL).mode());
    }

    @Test
    void paralisadoFalhaSalvaguardaDeDesEExaustaoTiraDoisPorNivel() {
        CharacterSheet s = new CharacterSheet("A");
        s.setCondition(Condition.PARALYZED, true);
        s.setExhaustionLevel(2);
        RollAdjustment dex = s.rollAdjustment(RollKind.SAVING_THROW, Ability.DEXTERITY, RollMode.NORMAL);
        assertTrue(dex.autoFail());
        assertEquals(-4, dex.modifierDelta());
        assertFalse(s.rollAdjustment(RollKind.SAVING_THROW, Ability.WISDOM, RollMode.NORMAL).autoFail());
    }

    @Test
    void danoComConcentracaoPedeCdEZeraConcentracaoAoCair() {
        CharacterSheet s = new CharacterSheet("A");
        s.setHpMax(30);
        s.setHpCurrent(30);
        s.setConcentratingOn("Bencao");
        CharacterSheet.DamageOutcome small = s.takeDamage(8);
        assertEquals(10, small.concentrationDc(), "CD minima 10");
        CharacterSheet.DamageOutcome big = s.takeDamage(22);
        assertTrue(big.droppedToZero());
        assertNull(s.getConcentratingOn(), "inconsciente perde a concentracao");
        assertTrue(s.hasCondition(Condition.UNCONSCIOUS));
        s.takeDamage(3);
        assertEquals(1, s.getDeathSaveFailures(), "dano caido = 1 falha");
        s.heal(5);
        assertFalse(s.hasCondition(Condition.UNCONSCIOUS));
        assertEquals(0, s.getDeathSaveFailures());
    }

    @Test
    void danoMassivoMataNaHora() {
        CharacterSheet s = new CharacterSheet("A");
        s.setHpMax(10);
        s.setHpCurrent(5);
        assertTrue(s.takeDamage(15).instantDeath()); // sobram 10 >= max 10
    }

    @Test
    void fichaEmpurraPvCondicoesEEfeitosProToken() {
        CharacterSheet s = new CharacterSheet("Elora");
        s.setHpMax(20);
        s.setHpCurrent(12);
        s.setHpTemp(3);
        s.setCondition(Condition.PRONE, true);
        s.setConcentratingOn("Bola de Fogo");
        s.setExhaustionLevel(1);
        TokenModel token = TokenLink.tokenFromSheet(s);
        assertEquals(s.getId(), token.getCharacterSheetId());
        assertEquals(12, token.getCurrentHp());
        assertEquals(3, token.getTempHp());
        assertTrue(token.hasCondition(Condition.PRONE));
        assertTrue(token.getEffects().contains("Concentrando: Bola de Fogo"));
        assertTrue(token.getEffects().contains("Exaustao 1"));
        assertFalse(TokenLink.pushSheetToToken(s, token), "nada mudou = nao manda pela rede");
        s.takeDamage(5);
        assertTrue(TokenLink.pushSheetToToken(s, token));
        assertEquals(10, token.getCurrentHp());
    }

    @Test
    void tokenDeMonstroNasceComPvMedioTamanhoEPvEscondido() {
        MobSheet mob = new com.google.gson.Gson().fromJson(
                "{\"id\":\"m1\",\"name\":\"Ogro\",\"size\":\"Grande\",\"hitPointsAverage\":68,\"dexterity\":8}", MobSheet.class);
        TokenModel token = TokenLink.tokenFromMob(mob, "Ogro");
        assertEquals(68, token.getCurrentHp());
        assertEquals(2, token.getWidthCells());
        assertEquals(-1, token.getInitiativeBonus());
        assertFalse(token.isHpVisibleToAll());
        assertTrue(TokenLink.damageUnlinkedToken(token, 70).contains("cai"));
        assertTrue(token.hasCondition(Condition.UNCONSCIOUS));
    }

    @Test
    void cenaNaoDuplicaTokenComMesmoId() {
        SceneModel scene = new SceneModel();
        TokenModel t = new TokenModel("X", null);
        scene.addToken(t);
        TokenModel copy = new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(t), TokenModel.class);
        scene.addToken(copy);
        assertEquals(1, scene.getTokens().size());
    }

    @Test
    void criticoDobraDadosEMarcaD20Natural() throws DiceException {
        assertEquals("2d8+3", DiceRoller.doubleDice("1d8+3"));
        assertEquals("4d6+2d4+2", DiceRoller.doubleDice("2d6 + 1d4 + 2"));
        assertNotNull(DiceRoller.roll("1d20+5").naturalD20());
        assertNull(DiceRoller.roll("2d6+1").naturalD20());
        assertNotNull(DiceRoller.rollWithAdvantage(3).naturalD20());
    }
}
