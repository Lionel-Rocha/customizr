package com.vttcaseiro.service;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.character.RollMode;
import com.vttcaseiro.dice.RollResult;
import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Rastreador de combate: modificador por combatente, cor da linha e iniciativa puxada da ficha. */
class TrackerModifiersTest {

    @Test
    void modifierAddsToRollAndShiftsRolledInitiative(@TempDir Path dir) throws Exception {
        SceneService scenes = new SceneService(new SceneModel());
        TableAndEncounterTest.FakePort port = new TableAndEncounterTest.FakePort();
        TableService table = new TableService(scenes, new CharacterService(new LibraryStorage(dir)), port);
        TokenModel goblin = new TokenModel("Goblin", null);
        goblin.setInitiativeBonus(2);
        scenes.addToken(goblin);

        table.setInitiativeModifier(goblin, 5);
        assertNull(goblin.getInitiative(), "sem rolagem, nada pra deslocar");
        for (int i = 0; i < 30; i++) {
            RollResult r = table.rollInitiative(goblin, false);
            assertTrue(r.total() >= 1 + 7 && r.total() <= 20 + 7, "d20 + 2 + 5: " + r.total());
            assertEquals(r.total(), goblin.getInitiative());
        }
        goblin.setInitiative(15);
        table.setInitiativeModifier(goblin, 2);
        assertEquals(12, goblin.getInitiative(), "o modificador caiu 3, a iniciativa anda junto");
        table.setInitiativeModifier(goblin, 999);
        assertEquals(50, goblin.getInitiativeModifier(), "limite");
    }

    @Test
    void colorOnlyKnownValuesAndOnlyWhoControls(@TempDir Path dir) throws Exception {
        SceneService scenes = new SceneService(new SceneModel());
        TableAndEncounterTest.FakePort port = new TableAndEncounterTest.FakePort();
        TableService table = new TableService(scenes, new CharacterService(new LibraryStorage(dir)), port);
        TokenModel t = new TokenModel("Orc", null);
        t.setOwnerId("mestre");
        scenes.addToken(t);
        assertNull(t.getTrackerColor(), "cinza e o padrao");
        table.setTrackerColor(t, "red");
        assertEquals("red", t.getTrackerColor());
        table.setTrackerColor(t, "azul-piscante");
        assertNull(t.getTrackerColor(), "cor desconhecida volta pro cinza");

        port.connected = true;
        port.gm = false;
        table.setTrackerColor(t, "green");
        assertNull(t.getTrackerColor(), "jogador nao pinta o token dos outros");
        table.setInitiativeModifier(t, 4);
        assertEquals(0, t.getInitiativeModifier());
        assertEquals(2, port.errors.size());

        TokenModel copy = new TokenModel("Orc", null);
        t.setTrackerColor("yellow");
        t.setInitiativeModifier(3);
        copy.copyStateFrom(t);
        assertEquals("yellow", copy.getTrackerColor(), "cor viaja pela rede");
        assertEquals(3, copy.getInitiativeModifier());
    }

    @Test
    void sheetInitiativeGoesToTheTracker(@TempDir Path dir) throws Exception {
        CharacterService chars = new CharacterService(new LibraryStorage(dir));
        CharacterSheet sheet = chars.create("Elora");
        sheet.setDexterity(16);
        SceneService scenes = new SceneService(new SceneModel());
        TableAndEncounterTest.FakePort port = new TableAndEncounterTest.FakePort();
        TableService table = new TableService(scenes, chars, port);

        // rolou na ficha antes do token existir
        RollResult r = table.rollSheetInitiative(sheet, RollMode.NORMAL, false);
        assertEquals(r.total(), sheet.getLastInitiative());
        assertTrue(port.announced.get(port.announced.size() - 1).startsWith("Iniciativa de Elora"));

        TokenModel t = TokenLink.tokenFromSheet(sheet);
        scenes.addToken(t);
        t.setInitiativeModifier(2);
        assertEquals(1, table.pullInitiativesFromSheets());
        assertEquals(r.total() + 2, t.getInitiative(), "ficha + modificador do rastreador");

        // rolando pelo token, a ficha guarda o resultado sem o modificador do rastreador
        RollResult again = table.rollInitiative(t, false, RollMode.ADVANTAGE);
        assertEquals(again.total() - 2, chars.findById(sheet.getId()).orElseThrow().getLastInitiative());

        TokenModel loose = new TokenModel("Sem ficha", null);
        scenes.addToken(loose);
        assertFalse(table.pullInitiativeFromSheet(loose));
    }
}
