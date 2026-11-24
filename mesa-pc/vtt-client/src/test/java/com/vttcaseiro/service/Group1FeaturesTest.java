package com.vttcaseiro.service;

import com.vttcaseiro.character.Ability;
import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.PreparedSpell;
import com.vttcaseiro.content.EntriesRenderer;
import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.library.MacroStore;
import com.vttcaseiro.mob.MobSheet;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tamanho do token, salvaguarda em grupo, descansos, magias com dados e macros. */
class Group1FeaturesTest {

    private record Table(TableService table, SceneService scenes, CharacterService chars,
                         TableAndEncounterTest.FakePort port) {
    }

    private static Table table(Path dir) throws Exception {
        CharacterService chars = new CharacterService(new LibraryStorage(dir));
        SceneService scenes = new SceneService(new SceneModel());
        TableAndEncounterTest.FakePort port = new TableAndEncounterTest.FakePort();
        return new Table(new TableService(scenes, chars, port), scenes, chars, port);
    }

    private static CharacterSheet hero(CharacterService chars, String name, int hp) throws Exception {
        CharacterSheet s = chars.create(name);
        s.setHpMax(hp);
        s.setHpCurrent(hp);
        return s;
    }

    @Test
    void tamanhoDoTokenSoQuemControlaETokenDaFichaUsaOTamanhoDela(@TempDir Path dir) throws Exception {
        Table t = table(dir);
        TokenModel ogre = new TokenModel("Ogro", null);
        t.scenes().addToken(ogre);
        t.table().setSize(ogre, 2);
        assertEquals(2, ogre.getWidthCells());
        assertEquals(2, ogre.getHeightCells());
        t.table().setSize(ogre, 99);
        assertEquals(com.vttcaseiro.model.TokenModel.MAX_CELLS, ogre.getWidthCells(), "limite de 20x20");

        t.port().gm = false;
        t.port().connected = true;
        t.table().setSize(ogre, 1);
        assertEquals(com.vttcaseiro.model.TokenModel.MAX_CELLS, ogre.getWidthCells(), "jogador nao mexe no token dos outros");
        assertFalse(t.port().errors.isEmpty());

        CharacterSheet big = hero(t.chars(), "Golias", 30);
        big.setSize("Grande");
        assertEquals(2, TokenLink.tokenFromSheet(big).getWidthCells());
        assertEquals("Enorme", TableService.sizeName(3));
    }

    @Test
    void salvaguardaEmGrupoAplicaDanoCheioOuMetade(@TempDir Path dir) throws Exception {
        Table t = table(dir);
        CharacterSheet elora = hero(t.chars(), "Elora", 40);
        TokenModel eloraTok = TokenLink.tokenFromSheet(elora);
        TokenModel goblin = new TokenModel("Goblin", null);
        goblin.setMaxHp(30);
        goblin.setCurrentHp(30);
        TokenModel spy = new TokenModel("Espiao", null);
        spy.setMaxHp(30);
        spy.setCurrentHp(30);
        spy.setHiddenFromPlayers(true);
        for (TokenModel tk : List.of(eloraTok, goblin, spy)) t.scenes().addToken(tk);
        List<TokenModel> all = List.copyOf(t.scenes().getScene().getTokens());

        // CD impossivel: todos falham e levam o dano cheio.
        var fail = t.table().groupSave(Ability.DEXTERITY, 100, all, "10", true);
        assertEquals(3, fail.size());
        assertTrue(fail.stream().noneMatch(TableService.SaveResult::success));
        assertEquals(30, elora.getHpCurrent(), "dano passa pela ficha");
        assertEquals(20, goblin.getCurrentHp());
        String pub = t.port().announced.get(0);
        assertTrue(pub.contains("Salvaguarda de DES CD 100"), pub);
        assertFalse(pub.contains("Espiao"), "oculto nao aparece no chat publico: " + pub);
        assertTrue(t.port().announcedGm.stream().anyMatch(m -> m.contains("Espiao")));

        // CD baixa: todos passam e levam metade.
        t.table().groupSave(Ability.DEXTERITY, -100, all, "10", true);
        assertEquals(25, elora.getHpCurrent());
        assertEquals(15, goblin.getCurrentHp());

        // Passou sem "metade": nada.
        t.table().groupSave(Ability.DEXTERITY, -100, all, "10", false);
        assertEquals(15, goblin.getCurrentHp());

        // Jogador nao pede.
        t.port().gm = false;
        t.port().connected = true;
        assertTrue(t.table().groupSave(Ability.DEXTERITY, 10, all, null, true).isEmpty());
    }

    @Test
    void salvaguardaDeMonstroUsaOBonusDoBloco(@TempDir Path dir) throws Exception {
        Table t = table(dir);
        MobSheet mob = new MobSheet("Mago");
        mob.setDexterity(10);
        mob.getSavingThrowBonuses().put("dex", 50);
        assertEquals(50, mob.saveBonus(Ability.DEXTERITY));
        assertEquals(0, mob.saveBonus(Ability.STRENGTH));
        t.table().setMobLookup(id -> id.equals(mob.getId()) ? mob : null);
        TokenModel tok = TokenLink.tokenFromMob(mob, "Mago");
        t.scenes().addToken(tok);
        var r = t.table().groupSave(Ability.DEXTERITY, 50, List.of(tok), null, true);
        assertTrue(r.get(0).success(), "1d20+50 sempre passa CD 50");
        assertTrue(r.get(0).total() >= 51);
    }

    @Test
    void descansosDoGrupo(@TempDir Path dir) throws Exception {
        Table t = table(dir);
        CharacterSheet elora = hero(t.chars(), "Elora", 40);
        elora.setLevel(5);
        elora.setHitDie("d10");
        elora.setConstitution(14);
        elora.getSpellSlots().setMax(1, 3);
        elora.getSpellSlots().setCurrent(1, 0);
        elora.setHpCurrent(5);
        t.scenes().addToken(TokenLink.tokenFromSheet(elora));
        t.scenes().addToken(new TokenModel("Goblin", null)); // sem ficha: ignorado

        t.table().groupShortRest();
        assertTrue(elora.getHpCurrent() > 5, "gastou dado de vida");
        assertTrue(elora.getHpCurrent() <= 40);
        assertTrue(elora.hitDiceRemaining() < 5);
        assertTrue(t.port().announced.get(0).startsWith("Descanso curto"));

        t.table().groupLongRest();
        assertEquals(40, elora.getHpCurrent());
        assertEquals(5, elora.hitDiceRemaining());
        assertEquals(3, elora.getSpellSlots().getCurrent(1));
        assertEquals(10, TableService.dieSides("d10"));
        assertEquals(8, TableService.dieSides("?"));

        t.port().gm = false;
        t.port().connected = true;
        assertTrue(t.table().groupLongRest().isEmpty());
    }

    @Test
    void magiaRolaMaisDadosNoEspacoMaisAlto() {
        PreparedSpell fireball = new PreparedSpell(3, "Fireball");
        fireball.setDice("8d6");
        fireball.setDicePerLevel("1d6");
        assertEquals("8d6", fireball.diceForSlot(3));
        assertEquals("10d6", fireball.diceForSlot(5));
        PreparedSpell mixed = new PreparedSpell(1, "Teste");
        mixed.setDice("2d8+3");
        mixed.setDicePerLevel("1d6");
        assertEquals("2d8+3+2d6", mixed.diceForSlot(3));
        assertEquals("", new PreparedSpell(1, "Escudo").diceForSlot(3));
    }

    @Test
    void dadosDaMagiaVemDoTextoDoCompendio() {
        PreparedSpell shatter = new PreparedSpell(2, "Shatter");
        CompendiumToSheet.fillSpellDice(shatter, "Casting Time: 1 action | Duration: Instantaneous\n\n"
                + "Each creature makes a Constitution saving throw, taking 3d8 Thunder damage."
                + "\n\nUsing a Higher-Level Spell Slot: The damage increases by 1d8 for each spell slot level above 2.");
        assertEquals("3d8", shatter.getDice());
        assertEquals("1d8", shatter.getDicePerLevel());
        // Texto antigo bugado ("+8d6" igual ao base): nao confia.
        PreparedSpell fireball = new PreparedSpell(3, "Fireball");
        CompendiumToSheet.fillSpellDice(fireball, "Duration: Instantaneous\n\ntaking 8d6 Fire damage."
                + "\n\nUsing a Higher-Level Spell Slot: The damage increases by 8d6 for each spell slot level above 3.");
        assertEquals("8d6", fireball.getDice());
        assertEquals("", fireball.getDicePerLevel());
        // E o renderizador agora mostra o aumento certo.
        assertEquals("The damage increases by 1d6.",
                EntriesRenderer.renderInline("The damage increases by {@scaledamage 8d6|3-9|1d6}."));
    }

    @Test
    void macrosSalvamEVoltam(@TempDir Path dir) {
        MacroStore store = new MacroStore(dir);
        assertTrue(store.load().isEmpty());
        store.save(List.of(new MacroStore.Macro("Furtivo", "/r 1d20+7\n\n  /r 1d6+4+3d6  \nAtaque furtivo!")));
        List<MacroStore.Macro> back = new MacroStore(dir).load();
        assertEquals(1, back.size());
        assertEquals("Furtivo", back.get(0).getName());
        assertEquals(List.of("/r 1d20+7", "/r 1d6+4+3d6", "Ataque furtivo!"), back.get(0).lines());
    }
}
