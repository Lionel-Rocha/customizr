package com.vttcaseiro.net;

import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.Wall;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Paredes so do mestre; porta qualquer um abre e todos recebem. */
class WallsNetTest {

    static class Rec extends GameServerSecurityTest.Recorder {
        final List<List<Wall>> walls = new CopyOnWriteArrayList<>();
        final List<Boolean> vision = new CopyOnWriteArrayList<>();

        @Override public void onWallsSync(List<Wall> w, Boolean visionOn, Boolean dark) {
            walls.add(w);
            vision.add(visionOn);
        }
    }

    private static void waitFor(BooleanSupplier c, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("Timeout: " + what);
            Thread.sleep(20);
        }
    }

    @Test
    void paredesDoMestrePortaDeQualquerUm() throws Exception {
        GameServer server = new GameServer(0, new SceneModel());
        server.start();
        Rec gm = new Rec();
        Rec ana = new Rec();
        Rec late = new Rec();
        GameClient gmC = new GameClient(gm, null, "gm");
        GameClient anaC = new GameClient(ana, null, "ana");
        GameClient lateC = new GameClient(late, null, "late");
        try {
            gmC.connect("localhost", server.getPort(), "Mestre", true);
            anaC.connect("localhost", server.getPort(), "Ana", false);
            waitFor(() -> gm.scene != null && ana.scene != null, "conectados");

            SceneModel hack = new SceneModel();
            anaC.sendWalls(hack); // jogador tentando apagar as paredes
            waitFor(() -> ana.chats.stream().anyMatch(c -> c.contains("Só o mestre mexe nas paredes")), "recusa");

            SceneModel s = new SceneModel();
            Wall door = new Wall(100, 0, 100, 100, true);
            s.getWalls().add(new Wall(0, 0, 100, 0, false));
            s.getWalls().add(door);
            s.setVisionEnabled(true);
            gmC.sendWalls(s);
            waitFor(() -> ana.walls.size() == 1, "paredes chegaram");
            assertEquals(2, ana.walls.get(0).size());
            assertTrue(ana.vision.get(0));

            anaC.sendDoorToggle(door.getId());
            waitFor(() -> gm.walls.size() == 1 && ana.walls.size() == 2, "porta alternada pra todos");
            assertTrue(gm.walls.get(0).stream().filter(Wall::isDoor).findFirst().orElseThrow().isOpen());

            lateC.connect("localhost", server.getPort(), "Atrasado", false);
            waitFor(() -> late.scene != null, "atrasado entrou");
            assertEquals(2, late.scene.getWalls().size(), "quem entra depois recebe as paredes");
            assertTrue(late.scene.isVisionEnabled());
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            lateC.disconnect();
            server.stop();
        }
    }
}
