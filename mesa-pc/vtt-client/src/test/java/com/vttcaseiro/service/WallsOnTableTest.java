package com.vttcaseiro.service;

import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.mob.MobSheet;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.model.Wall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WallsOnTableTest {

    @Test
    void paredesSoDoMestrePortaQualquerUm(@TempDir Path dir) throws Exception {
        SceneService scenes = new SceneService(new SceneModel());
        TableAndEncounterTest.FakePort port = new TableAndEncounterTest.FakePort();
        TableService table = new TableService(scenes, new CharacterService(new LibraryStorage(dir)), port);
        int[] changes = {0};
        scenes.addListener(new SceneService.SceneChangeListener() {
            @Override public void onWallsChanged() { changes[0]++; }
        });

        Wall wall = new Wall(0, 0, 100, 0, false);
        Wall door = new Wall(100, 0, 140, 0, true);
        table.addWall(wall);
        table.addWall(door);
        table.addWall(new Wall(0, 0, 1, 0, false));
        assertEquals(2, scenes.getScene().getWalls().size(), "parede minuscula nao entra");
        table.setVisionSettings(true, true);
        assertTrue(scenes.getScene().isVisionEnabled());
        assertTrue(scenes.getScene().isDark());
        table.toggleDoor(door);
        assertTrue(door.isOpen(), "offline/mestre abre direto");
        table.toggleDoor(wall);
        assertFalse(wall.isOpen(), "parede nao e porta");

        port.gm = false;
        port.connected = true;
        table.addWall(new Wall(0, 50, 100, 50, false));
        assertEquals(2, scenes.getScene().getWalls().size(), "jogador nao desenha parede");
        table.removeWall(wall);
        assertEquals(2, scenes.getScene().getWalls().size());
        table.setVisionSettings(false, false);
        assertTrue(scenes.getScene().isVisionEnabled(), "jogador nao desliga a visao");
        table.toggleDoor(door);
        assertEquals(List.of(door.getId()), port.doorToggles, "jogador pede pro servidor");

        table.applyRemoteWalls(List.of(wall), false, null);
        assertEquals(List.of(wall), scenes.getScene().getWalls());
        assertFalse(scenes.getScene().isVisionEnabled());
        assertTrue(scenes.getScene().isDark(), "null mantem");
        assertTrue(changes[0] >= 5);
    }

    @Test
    void visaoDoTokenDonoOuMestreEDoBestiario(@TempDir Path dir) throws Exception {
        SceneService scenes = new SceneService(new SceneModel());
        TableAndEncounterTest.FakePort port = new TableAndEncounterTest.FakePort();
        TableService table = new TableService(scenes, new CharacterService(new LibraryStorage(dir)), port);
        MobSheet orc = new MobSheet("Orc");
        orc.getSenses().add("Darkvision 60 ft.");
        TokenModel orcTok = TokenLink.tokenFromMob(orc, "Orc");
        assertEquals(60, orcTok.getVisionFeet(), "visao no escuro vem do bestiario");
        scenes.addToken(orcTok);
        table.setTokenVision(orcTok, 120, 40);
        assertEquals(120, orcTok.getVisionFeet());
        assertEquals(40, orcTok.getLightFeet());
        TokenModel copy = new TokenModel("x", null);
        copy.copyStateFrom(orcTok);
        assertEquals(40, copy.getLightFeet(), "vai junto na rede");

        port.gm = false;
        port.connected = true;
        port.me = "ana";
        table.setTokenVision(orcTok, 0, 0);
        assertEquals(120, orcTok.getVisionFeet(), "jogador nao mexe no token alheio");
    }
}
