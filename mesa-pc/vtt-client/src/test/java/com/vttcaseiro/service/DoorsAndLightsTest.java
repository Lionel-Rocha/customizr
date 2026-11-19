package com.vttcaseiro.service;

import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.model.MapLight;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.model.Wall;
import com.vttcaseiro.vision.Perception;
import com.vttcaseiro.vision.Visibility;
import com.vttcaseiro.vision.VisionCheck;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Portas trancadas e secretas, janelas e luzes fixas no mapa. */
class DoorsAndLightsTest {

    private static TableService table(SceneModel scene, TableAndEncounterTest.FakePort port, Path dir) throws Exception {
        return new TableService(new SceneService(scene), new CharacterService(new LibraryStorage(dir)), port);
    }

    @Test
    void windowLetsSeeButNotPass() {
        Wall window = new Wall(100, -500, 100, 500, false);
        window.setKind(false, true);
        assertTrue(window.isWindow());
        assertFalse(window.blocksSight());
        assertTrue(window.blocks(), "nao passa");
        List<Wall> walls = List.of(window);
        assertTrue(Visibility.blocked(50, 0, 150, 0, walls), "movimento bloqueado");
        double[] poly = Visibility.polygon(50, 0, walls, 1000, 1000);
        assertTrue(Visibility.inside(poly, 150, 0), "mas ve do outro lado");

        window.setKind(true, false);
        assertTrue(window.isDoor());
        assertFalse(window.isWindow(), "virou porta");
    }

    @Test
    void lockedAndSecretDoorsOnlyTheMasterOpens(@TempDir Path dir) throws Exception {
        SceneModel scene = new SceneModel();
        TableAndEncounterTest.FakePort port = new TableAndEncounterTest.FakePort();
        TableService table = table(scene, port, dir);
        Wall door = new Wall(0, 0, 0, 100, true);
        scene.getWalls().add(door);

        table.editWall(door, w -> w.setLocked(true));
        assertTrue(door.isLocked());
        port.gm = false;
        table.toggleDoor(door);
        assertFalse(door.isOpen(), "jogador nao abre a trancada");
        assertTrue(port.errors.stream().anyMatch(e -> e.contains("trancada")));
        table.editWall(door, w -> w.setLocked(false));
        assertTrue(door.isLocked(), "jogador nao destranca");

        port.gm = true;
        table.toggleDoor(door);
        assertTrue(door.isOpen(), "mestre abre");

        Wall secret = new Wall(200, 0, 200, 100, true);
        scene.getWalls().add(secret);
        table.editWall(secret, w -> w.setSecret(true));
        port.gm = false;
        table.toggleDoor(secret);
        assertFalse(secret.isOpen(), "secreta: jogador nem sabe que e porta");
        port.gm = true;
        table.toggleDoor(secret);
        assertTrue(secret.isOpen());
        assertFalse(secret.gmOnly(), "aberta, qualquer um fecha");

        table.editWall(secret, w -> w.setKind(false, false));
        assertFalse(secret.isDoor());
        assertFalse(secret.isSecret(), "virou parede: perde a tranca e o segredo");
    }

    @Test
    void mapLightsLightTheDarkAndCanBeSwitchedOff(@TempDir Path dir) throws Exception {
        SceneModel scene = new SceneModel();
        scene.getGrid().setCellSize(70);
        scene.getGrid().setFeetPerCell(5);
        scene.setVisionEnabled(true);
        scene.setDark(true);
        TableAndEncounterTest.FakePort port = new TableAndEncounterTest.FakePort();
        TableService table = table(scene, port, dir);
        TokenModel human = new TokenModel("Humano", null);
        human.setOwnerId("p");
        scene.addToken(human);

        // fogueira a 6 casas (30 pes): 20 plena + 20 penumbra
        MapLight fire = new MapLight("Fogueira", 6 * 70 + 35, 35, 20, 20);
        assertTrue(table.addLight(fire));
        VisionCheck eyes = VisionCheck.of(scene, List.of(human));
        assertEquals(Perception.Light.BRIGHT, eyes.seenAt(6 * 70 + 35, 35), "do lado da fogueira: luz plena");
        assertEquals(Perception.Light.DIM, eyes.seenAt(12 * 70 + 35, 35), "30 pes dela: penumbra");
        assertFalse(eyes.canSee(16 * 70 + 35, 35), "longe: escuro");

        table.editLight(fire, l -> l.setOn(false));
        assertFalse(VisionCheck.of(scene, List.of(human)).canSee(6 * 70 + 35, 35), "apagada nao ilumina");

        port.gm = false;
        assertFalse(table.addLight(new MapLight("Hack", 0, 0, 60, 60)), "jogador nao poe luz");
        port.gm = true;
        table.removeLight(fire);
        assertTrue(scene.getLights().isEmpty());
    }
}
