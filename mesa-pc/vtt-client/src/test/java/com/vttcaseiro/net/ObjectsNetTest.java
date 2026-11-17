package com.vttcaseiro.net;

import com.vttcaseiro.model.MapObject;
import com.vttcaseiro.model.SceneModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Objetos do mapa: o mestre coloca, todos recebem; objeto oculto nao vai pro jogador. */
class ObjectsNetTest {

    static class ObjRec extends GameServerSecurityTest.Recorder {
        final List<List<MapObject>> objects = new CopyOnWriteArrayList<>();
        @Override public void onObjectsSync(List<MapObject> o) { objects.add(o); }
    }

    private static void waitFor(BooleanSupplier c, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("Timeout: " + what);
            Thread.sleep(20);
        }
    }

    @Test
    void objetosSincronizamEOcultoNaoVaiProJogador() throws Exception {
        GameServer server = new GameServer(0, new SceneModel());
        server.start();
        ObjRec gm = new ObjRec();
        ObjRec ana = new ObjRec();
        ObjRec late = new ObjRec();
        GameClient gmC = new GameClient(gm, null, "gm");
        GameClient anaC = new GameClient(ana, null, "ana");
        GameClient lateC = new GameClient(late, null, "late");
        try {
            gmC.connect("localhost", server.getPort(), "Mestre", true);
            anaC.connect("localhost", server.getPort(), "Ana", false);
            waitFor(() -> gm.scene != null && ana.scene != null, "entraram");
            String asset = "b".repeat(64) + ".png";
            SceneModel mine = new SceneModel();
            MapObject chest = new MapObject(asset, 70, 70, 70, 70);
            MapObject trap = new MapObject(asset, 140, 70, 70, 70);
            trap.setHidden(true);
            mine.getObjects().add(chest);
            mine.getObjects().add(trap);
            gmC.sendObjects(mine);
            waitFor(() -> !ana.objects.isEmpty(), "jogadora recebeu");
            List<MapObject> got = ana.objects.get(ana.objects.size() - 1);
            assertEquals(1, got.size(), "o oculto nao chega");
            assertEquals(chest.getId(), got.get(0).getId());

            lateC.connect("localhost", server.getPort(), "Beto", false);
            waitFor(() -> late.scene != null, "Beto entrou");
            assertEquals(1, late.scene.getObjects().size());

            anaC.sendObjects(new SceneModel());
            waitFor(() -> ana.chats.stream().anyMatch(c -> c.contains("Só o mestre mexe nos objetos")), "recusou");
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            lateC.disconnect();
            server.stop();
        }
    }
}
