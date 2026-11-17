package com.vttcaseiro.service;

import com.vttcaseiro.library.HandoutStore;
import com.vttcaseiro.library.LibraryStorage;
import com.vttcaseiro.model.Drawing;
import com.vttcaseiro.model.Handout;
import com.vttcaseiro.model.SceneModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DrawingsAndJournalTest {

    @Test
    void desenhoTemLimitesEDistanciaPraBorracha() {
        Drawing line = Drawing.path(List.of(0.0, 0.0, 100.0, 0.0, 7.0), "#00ff00", 3);
        assertEquals(4, line.getPoints().size(), "ponto sem par e descartado");
        assertEquals(5, line.distanceTo(50, 5), 0.001);
        assertEquals(0, line.distanceTo(100, 0), 0.001);
        assertEquals("#00ff00", line.getColor());
        line.setColor("vermelho; -fx-hack");
        assertEquals("#E2483F", line.getColor(), "cor invalida vira o padrao");

        List<Double> huge = new ArrayList<>();
        for (int i = 0; i < Drawing.MAX_POINTS * 3; i++) huge.add((double) i);
        assertEquals(Drawing.MAX_POINTS * 2, Drawing.path(huge, "#000000", 2).getPoints().size());

        Drawing text = Drawing.text(10, 40, "x".repeat(500), "#ffffff");
        assertEquals(Drawing.MAX_TEXT, text.getText().length());
        assertEquals(0, text.distanceTo(15, 30), 0.001, "clique dentro da caixa do texto");
    }

    @Test
    void cenaGuardaEApagaPorDono() {
        SceneModel scene = new SceneModel();
        Drawing a = Drawing.path(List.of(0.0, 0.0, 1.0, 1.0), "#000000", 2);
        a.setOwnerId("ana");
        Drawing b = Drawing.path(List.of(0.0, 0.0, 1.0, 1.0), "#000000", 2);
        b.setOwnerId("beto");
        scene.addDrawing(a);
        scene.addDrawing(b);
        scene.addDrawing(a); // mesmo id: troca, nao duplica
        assertEquals(2, scene.getDrawings().size());
        assertEquals(1, scene.clearDrawings("ana"));
        assertEquals(List.of(b), scene.getDrawings());
        assertTrue(scene.removeDrawing(b.getId()));
        assertEquals(0, scene.clearDrawings(null));
    }

    @Test
    void mesaDesenhaComDonoEJogadorSoApagaOsSeus(@TempDir Path dir) throws Exception {
        SceneService scenes = new SceneService(new SceneModel());
        TableAndEncounterTest.FakePort port = new TableAndEncounterTest.FakePort();
        TableService table = new TableService(scenes, new CharacterService(new LibraryStorage(dir)), port);
        int[] changes = {0};
        scenes.addListener(new SceneService.SceneChangeListener() {
            @Override public void onDrawingsChanged() { changes[0]++; }
        });
        port.me = "mestre";
        Drawing gmLine = Drawing.path(List.of(0.0, 0.0, 10.0, 10.0), "#ff0000", 3);
        table.addDrawing(gmLine);
        assertEquals("mestre", gmLine.getOwnerId());
        table.addDrawing(Drawing.text(5, 5, "   ", "#ffffff"));
        assertEquals(1, scenes.getScene().getDrawings().size(), "texto vazio nao entra");

        port.gm = false;
        port.connected = true;
        port.me = "ana";
        Drawing mine = Drawing.text(20, 20, "Armadilha!", "#ffffff");
        table.addDrawing(mine);
        assertFalse(table.canErase(gmLine));
        table.removeDrawing(gmLine);
        assertEquals(2, scenes.getScene().getDrawings().size(), "jogador nao apaga o do mestre");
        assertEquals(1, table.clearDrawings(), "jogador limpa so os seus");
        assertEquals(List.of(gmLine), scenes.getScene().getDrawings());

        table.applyRemoteDrawingAdd(mine);
        table.applyRemoteDrawingRemove(mine.getId());
        table.applyRemoteDrawingClear(null);
        assertTrue(scenes.getScene().getDrawings().isEmpty());
        assertTrue(changes[0] >= 5);
    }

    @Test
    void diarioSalvaEVolta(@TempDir Path dir) {
        HandoutStore store = new HandoutStore(dir);
        Handout h = new Handout("Carta do Barao", "Venham sozinhos.");
        h.setRevealed(true);
        h.setImageAssetId("abc");
        store.save(List.of(h, new Handout("", "nota secreta")));
        List<Handout> back = new HandoutStore(dir).load();
        assertEquals(2, back.size());
        assertEquals("Carta do Barao", back.get(0).getTitle());
        assertTrue(back.get(0).isRevealed());
        assertEquals("abc", back.get(0).getImageAssetId());
        assertEquals("(sem titulo)", back.get(1).getTitle());
        assertFalse(back.get(1).isRevealed());
    }
}
