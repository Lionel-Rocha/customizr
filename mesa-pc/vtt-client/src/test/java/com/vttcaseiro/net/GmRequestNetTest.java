package com.vttcaseiro.net;

import com.vttcaseiro.model.GmRequest;
import com.vttcaseiro.model.SceneModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** Pedido de dano de um jogador chega em UM mestre so, com o nome real. */
class GmRequestNetTest {

    static class Rec extends GameServerSecurityTest.Recorder {
        final List<String> requests = new CopyOnWriteArrayList<>();

        @Override public void onGmRequest(String player, GmRequest r) {
            requests.add(player + ":" + r.getKind() + ":" + r.getAmount() + ":" + r.getDamageType());
        }
    }

    private static void waitFor(java.util.function.BooleanSupplier c) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean() && System.currentTimeMillis() < end) Thread.sleep(20);
    }

    @Test
    void pedidoVaiPraUmMestreSo() throws Exception {
        GameServer server = new GameServer(0, new SceneModel());
        server.start();
        Rec gm1 = new Rec();
        Rec gm2 = new Rec();
        Rec ana = new Rec();
        GameClient gm1C = new GameClient(gm1, null, "gm1");
        GameClient gm2C = new GameClient(gm2, null, "gm2");
        GameClient anaC = new GameClient(ana, null, "ana");
        try {
            anaC.connect("localhost", server.getPort(), "Ana", false);
            waitFor(() -> ana.scene != null);
            // Sem mestre: o jogador fica sabendo.
            anaC.sendGmRequest(GmRequest.damage(List.of("t1"), 7, "fire", "Raio"));
            waitFor(() -> ana.chats.stream().anyMatch(c -> c.contains("Nenhum mestre")));
            assertTrue(ana.chats.stream().anyMatch(c -> c.contains("Nenhum mestre")), ana.chats.toString());

            gm1C.connect("localhost", server.getPort(), "Mestre1", true);
            gm2C.connect("localhost", server.getPort(), "Mestre2", true);
            waitFor(() -> gm1.scene != null && gm2.scene != null);
            anaC.sendGmRequest(GmRequest.damage(List.of("t1"), 7, "fire", "Raio"));
            waitFor(() -> gm1.requests.size() + gm2.requests.size() >= 1);
            Thread.sleep(200);
            assertEquals(1, gm1.requests.size() + gm2.requests.size(), "dois mestres nao aplicam duas vezes");
            String got = gm1.requests.isEmpty() ? gm2.requests.get(0) : gm1.requests.get(0);
            assertEquals("Ana:damage:7:fire", got);
            assertTrue(ana.requests.isEmpty());
        } finally {
            gm1C.disconnect();
            gm2C.disconnect();
            anaC.disconnect();
            server.stop();
        }
    }
}
