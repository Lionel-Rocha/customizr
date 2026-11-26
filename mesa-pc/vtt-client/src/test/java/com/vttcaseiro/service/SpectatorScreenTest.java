package com.vttcaseiro.service;

import com.vttcaseiro.character.CharacterSheet;
import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.model.Drawing;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Espectador: a propria tela nao deixa mexer (nada de token "fantasma" que o servidor recusaria). */
class SpectatorScreenTest {

    static class SpectatorPort extends TableAndEncounterTest.FakePort {
        @Override public boolean isSpectator() { return true; }
    }

    @Test
    void spectatorControlsNothing(@TempDir Path dir) throws Exception {
        SceneService scenes = new SceneService(new SceneModel());
        TokenModel old = new TokenModel("Antigo token do Zé", null);
        old.setOwnerId("ze"); // ele ja jogou com esse id
        scenes.addToken(old);
        SpectatorPort port = new SpectatorPort();
        port.connected = true;
        port.gm = false;
        port.me = "ze";
        TableService table = new TableService(scenes, new CharacterService(new LibraryStorage(dir)), port);
        assertFalse(table.canControl(old), "assistindo: nem o token que era dele");
        CharacterSheet sheet = new CharacterSheet("Ficha do Zé");
        sheet.setOwnerPlayerId("ze");
        assertFalse(table.canEditSheet(sheet));
        table.addDrawing(Drawing.text(10, 10, "oi", "#E2483F"));
        assertTrue(scenes.getScene().getDrawings().isEmpty(), "o desenho nem aparece na tela dele");
        assertFalse(port.errors.isEmpty());

        port.connected = false; // offline, a mesa e dele
        assertTrue(table.canControl(old));
    }
}
