package com.vttcaseiro.net;

import com.vttcaseiro.model.SceneModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** "Olhem aqui" (o mestre puxa a tela de todos) e camera travada pelo mestre. */
class CameraNetTest {

    static class CamRecorder extends GameServerSecurityTest.Recorder {
        final List<String> pulls = new CopyOnWriteArrayList<>();
        final List<String> pings = new CopyOnWriteArrayList<>();
        final List<String> locks = new CopyOnWriteArrayList<>();

        @Override public void onMapPull(String p, double x, double y) { pulls.add(p + "@" + (int) x + "," + (int) y); }
        @Override public void onMapPing(String p, double x, double y) { pings.add(p); }
        @Override public void onCameraLock(boolean l, double x, double y, double z) { locks.add(l + "@" + (int) x + "," + (int) y + "x" + z); }
    }

    private static void waitFor(BooleanSupplier c, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("Timeout: " + what);
            Thread.sleep(20);
        }
    }

    @Test
    void masterPullsAndLocksTheCamera() throws Exception {
        GameServer server = new GameServer(0, new SceneModel());
        server.start();
        CamRecorder gm = new CamRecorder();
        CamRecorder ana = new CamRecorder();
        CamRecorder beto = new CamRecorder();
        GameClient gmC = new GameClient(gm, null, "gm");
        GameClient anaC = new GameClient(ana, null, "ana");
        GameClient betoC = new GameClient(beto, null, "beto");
        GameClient carlaC = null;
        try {
            gmC.connect("localhost", server.getPort(), "Mestre", true);
            anaC.connect("localhost", server.getPort(), "Ana", false);
            betoC.connect("localhost", server.getPort(), "Beto", false);
            waitFor(() -> gm.scene != null && ana.scene != null && beto.scene != null, "entraram");

            gmC.sendMapPull(300, 400);
            waitFor(() -> !ana.pulls.isEmpty() && !beto.pulls.isEmpty(), "todos puxados");
            assertEquals("Mestre@300,400", ana.pulls.get(0));

            anaC.sendMapPull(10, 10); // jogador nao puxa ninguem: vira ping comum
            waitFor(() -> !beto.pings.isEmpty(), "ping da Ana");
            assertEquals(1, beto.pulls.size(), "Beto não foi puxado pela Ana");

            gmC.sendCameraLock(true, 500, 600, 1.5);
            waitFor(() -> !ana.locks.isEmpty(), "camera travada");
            assertEquals("true@500,600x1.5", ana.locks.get(0));

            // quem entra depois ja chega com a camera travada
            CamRecorder carla = new CamRecorder();
            carlaC = new GameClient(carla, null, "carla");
            carlaC.connect("localhost", server.getPort(), "Carla", false);
            waitFor(() -> !carla.locks.isEmpty(), "Carla chegou travada");

            anaC.sendCameraLock(false, 0, 0, 1); // jogador nao solta
            Thread.sleep(200);
            assertEquals(1, beto.locks.size());

            gmC.sendCameraLock(false, 500, 600, 1.5);
            waitFor(() -> ana.locks.size() == 2 && ana.locks.get(1).startsWith("false"), "camera solta");
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            betoC.disconnect();
            if (carlaC != null) carlaC.disconnect();
            server.stop();
        }
    }
}
