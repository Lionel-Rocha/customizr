package com.vttcaseiro.net;

import com.vttcaseiro.model.MapPin;
import com.vttcaseiro.model.SceneModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Pins: o mestre cria, todos recebem; pin oculto nunca sai do servidor pra jogador. */
class PinsNetTest {

    static class PinRec extends GameServerSecurityTest.Recorder {
        final List<List<MapPin>> pins = new CopyOnWriteArrayList<>();
        @Override public void onPinsSync(List<MapPin> p) { pins.add(p); }
    }

    private static void waitFor(BooleanSupplier c, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("Timeout: " + what);
            Thread.sleep(20);
        }
    }

    @Test
    void pinsSincronizamEOcultoNaoVaiProJogador() throws Exception {
        SceneModel scene = new SceneModel();
        GameServer server = new GameServer(0, scene);
        server.start();
        PinRec gm = new PinRec();
        PinRec ana = new PinRec();
        PinRec late = new PinRec();
        GameClient gmC = new GameClient(gm, null, "gm");
        GameClient anaC = new GameClient(ana, null, "ana");
        GameClient lateC = new GameClient(late, null, "late");
        try {
            gmC.connect("localhost", server.getPort(), "Mestre", true);
            anaC.connect("localhost", server.getPort(), "Ana", false);
            waitFor(() -> gm.scene != null && ana.scene != null, "entraram");

            SceneModel mine = new SceneModel();
            MapPin city = new MapPin(100, 200, "Alta Torre", "capital");
            city.setLinkedScene("Alta Torre (cidade)");
            MapPin secret = new MapPin(300, 50, "Covil do dragao", "perigo");
            secret.setHidden(true);
            mine.getPins().add(city);
            mine.getPins().add(secret);
            gmC.sendPins(mine);

            waitFor(() -> !ana.pins.isEmpty(), "jogadora recebeu os pins");
            List<MapPin> got = ana.pins.get(ana.pins.size() - 1);
            assertEquals(1, got.size(), "o pin oculto nao chega");
            assertEquals("Alta Torre", got.get(0).getTitle());
            assertEquals("Alta Torre (cidade)", got.get(0).getLinkedScene());

            // Quem entra depois recebe a cena ja sem o pin oculto.
            lateC.connect("localhost", server.getPort(), "Beto", false);
            waitFor(() -> late.scene != null, "Beto entrou");
            assertEquals(1, late.scene.getPins().size());
            assertFalse(late.scene.getPins().get(0).isHidden());

            // Jogador nao mexe nos pins.
            anaC.sendPins(new SceneModel());
            waitFor(() -> ana.chats.stream().anyMatch(c -> c.contains("Só o mestre mexe nos pins")), "recusou");
            // ...e o mestre continua vendo os dois (inclusive o oculto) ao reentrar.
            PinRec again = new PinRec();
            GameClient againC = new GameClient(again, null, "gm2");
            againC.connect("localhost", server.getPort(), "Mestre2", true);
            waitFor(() -> again.scene != null, "mestre reentrou");
            assertEquals(2, again.scene.getPins().size(), "mestre recebe o oculto; a recusa nao apagou nada");
            againC.disconnect();
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            lateC.disconnect();
            server.stop();
        }
    }

    @Test
    void pinVindoDaRedeELimpo() {
        SceneModel s = new SceneModel();
        MapPin bad = new MapPin(Double.NaN, 5, "x".repeat(500), "inexistente");
        bad.setGeneratorUrl("javascript:alert(1)");
        bad.setText("y".repeat(10000));
        s.setPins(List.of(bad));
        MapPin p = s.getPins().get(0);
        assertEquals(MapPin.MAX_TITLE, p.getTitle().length());
        assertEquals(MapPin.MAX_TEXT, p.getText().length());
        assertEquals("info", p.getKind());
        assertNull(p.getGeneratorUrl(), "so link https");
        assertEquals(0, p.getX());
    }
}
