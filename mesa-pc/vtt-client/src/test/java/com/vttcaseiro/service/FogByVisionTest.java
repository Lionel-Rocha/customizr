package com.vttcaseiro.service;

import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Neblina do mestre aberta pela visao dos tokens dos jogadores (regra do 5.5e), e altura/direcao pelo editToken. */
class FogByVisionTest {

    @Test
    void visionOpensTheFogAndItStaysOpen(@TempDir Path dir) throws Exception {
        SceneModel scene = new SceneModel();
        scene.getGrid().setCellSize(70);
        scene.getGrid().setFeetPerCell(5);
        SceneService scenes = new SceneService(scene);
        TableAndEncounterTest.FakePort port = new TableAndEncounterTest.FakePort();
        TableService table = new TableService(scenes, new CharacterService(new LibraryStorage(dir)), port);

        TokenModel elf = new TokenModel("Elfa", null);
        elf.setOwnerId("jogador");
        elf.setX(10 * 70);
        elf.setY(10 * 70);
        elf.setVisionFeet(30);
        scene.addToken(elf);
        scene.getFog().setEnabled(true);

        assertEquals(0, table.revealFogByVision(), "opcao desligada: nada");
        table.setVisionSettings(true, true, false, true);   // visao, escuro, sem penumbra, neblina pela visao
        assertTrue(scene.getFog().isRevealed(10, 10), "a casa dela");
        assertTrue(scene.getFog().isRevealed(15, 10), "25 pes: dentro dos 30 da visao no escuro");
        assertFalse(scene.getFog().isRevealed(17, 10), "35 pes: fora");
        int opened = scene.getFog().revealedCount();
        assertTrue(opened > 50 && opened < 200, "um circulo de ~6 casas de raio: " + opened);

        // andou: abre mais, e o que ja abriu continua aberto
        elf.setX(20 * 70);
        assertTrue(table.revealFogByVision() > 0);
        assertTrue(scene.getFog().isRevealed(10, 10), "continua aberto");
        assertTrue(scene.getFog().isRevealed(24, 10));

        // jogador nao manda na neblina
        port.gm = false;
        elf.setX(40 * 70);
        assertEquals(0, table.revealFogByVision());
    }

    @Test
    void editTokenChangesElevationAndFacingForWhoControls(@TempDir Path dir) throws Exception {
        SceneModel scene = new SceneModel();
        SceneService scenes = new SceneService(scene);
        TableAndEncounterTest.FakePort port = new TableAndEncounterTest.FakePort();
        TableService table = new TableService(scenes, new CharacterService(new LibraryStorage(dir)), port);
        TokenModel t = new TokenModel("Grifo", null);
        scene.addToken(t);
        table.editToken(t, x -> {
            x.setElevationFeet(40);
            x.setFacingDegrees(90.0);
        });
        assertEquals(40, t.getElevationFeet());
        assertEquals(90.0, t.getFacingDegrees());
    }
}
