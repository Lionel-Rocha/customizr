package com.vttcaseiro.net;

import com.vttcaseiro.model.SceneModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** Ping no mapa chega nos outros com o nome REAL de quem pingou. */
class MapPingNetTest {

    static class Rec extends GameServerSecurityTest.Recorder {
        final List<String> pings = new CopyOnWriteArrayList<>();

        @Override public void onMapPing(String player, double x, double y) {
            pings.add(player + "@" + (int) x + "," + (int) y);
        }
    }

    @Test
    void pingChegaNosOutrosComONomeDaSessao() throws Exception {
        GameServer server = new GameServer(0, new SceneModel());
        server.start();
        Rec gm = new Rec();
        Rec ana = new Rec();
        GameClient gmC = new GameClient(gm, null, "gm");
        GameClient anaC = new GameClient(ana, null, "ana");
        try {
            gmC.connect("localhost", server.getPort(), "Mestre", true);
            anaC.connect("localhost", server.getPort(), "Ana", false);
            long end = System.currentTimeMillis() + 5000;
            while ((gm.scene == null || ana.scene == null) && System.currentTimeMillis() < end) Thread.sleep(20);

            // O cliente nao manda nome nenhum: quem carimba e o servidor.
            anaC.sendMapPing(140, 210);
            end = System.currentTimeMillis() + 5000;
            while (gm.pings.isEmpty() && System.currentTimeMillis() < end) Thread.sleep(20);
            assertEquals(List.of("Ana@140,210"), gm.pings);
            Thread.sleep(200);
            assertTrue(ana.pings.isEmpty(), "quem pingou ja desenhou o proprio ping");
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            server.stop();
        }
    }
}
