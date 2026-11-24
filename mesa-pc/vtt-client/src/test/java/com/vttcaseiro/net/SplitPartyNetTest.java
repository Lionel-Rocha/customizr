package com.vttcaseiro.net;

import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Grupo dividido: um jogador vai sozinho pra outra cena; o resto do grupo nao ve nada do que acontece la. */
class SplitPartyNetTest {

    static class GmRecorder extends GameServerSecurityTest.Recorder {
        volatile String viewKey;
        volatile boolean side;
        volatile SceneModel closed;
        volatile String closedKey;

        @Override public void onSceneView(String key, boolean s) { viewKey = key; side = s; }
        @Override public void onSideSceneClosed(String key, SceneModel scene) { closedKey = key; closed = scene; }
        final java.util.List<String> zoneEvents = new java.util.concurrent.CopyOnWriteArrayList<>();
        @Override public void onZoneEvent(com.vttcaseiro.model.MapZone z, TokenModel t, String key, String owner) {
            zoneEvents.add(z.label() + "|" + t.getName() + "|" + owner + "|" + key);
        }
    }

    private static void waitFor(BooleanSupplier c, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("Timeout: " + what);
            Thread.sleep(20);
        }
    }

    private static SceneModel scene(String name, String tokenName) {
        SceneModel s = new SceneModel();
        s.setName(name);
        s.addToken(new TokenModel(tokenName, null));
        return s;
    }

    @Test
    void playerGoesAloneAndComesBack() throws Exception {
        GameServer server = new GameServer(0, new SceneModel());
        server.start();
        GmRecorder gm = new GmRecorder();
        GameServerSecurityTest.Recorder ana = new GameServerSecurityTest.Recorder();
        GameServerSecurityTest.Recorder beto = new GameServerSecurityTest.Recorder();
        GameClient gmC = new GameClient(gm, null, "gm");
        GameClient anaC = new GameClient(ana, null, "ana");
        GameClient betoC = new GameClient(beto, null, "beto");
        try {
            gmC.connect("localhost", server.getPort(), "Mestre", true);
            anaC.connect("localhost", server.getPort(), "Ana", false);
            betoC.connect("localhost", server.getPort(), "Beto", false);
            waitFor(() -> gm.scene != null && ana.scene != null && beto.scene != null, "entraram");

            gmC.sendSceneOpen(scene("Taverna", "Taverneiro"), "Taverna");
            waitFor(() -> "Taverna".equals(ana.scene.getName()) && "Taverna".equals(beto.scene.getName()), "grupo na taverna");

            // Ana vai sozinha pro porao
            gmC.sendSplitScene("ana", "Porão", scene("Porão", "Rato gigante"));
            waitFor(() -> "Porão".equals(ana.scene.getName()), "Ana no porao");
            assertEquals("Rato gigante", ana.scene.getTokens().get(0).getName());
            Thread.sleep(200);
            assertEquals("Taverna", beto.scene.getName(), "Beto continua na taverna");
            assertEquals(List.of("Ana"), server.splitState().get("Porão"));

            // o que a Ana faz la nao chega no Beto nem no mestre (que esta olhando a taverna)
            int betoBefore = beto.tokensAdded.size();
            int gmBefore = gm.tokensAdded.size();
            TokenModel lyra = new TokenModel("Lyra", null);
            anaC.sendAddToken(lyra);
            Thread.sleep(300);
            assertEquals(betoBefore, beto.tokensAdded.size(), "Beto não vê o token do porão");
            assertEquals(gmBefore, gm.tokensAdded.size(), "mestre olhando a taverna também não");

            // mestre olha o porao e volta
            gmC.sendGmView("Porão");
            waitFor(() -> "Porão".equals(gm.scene.getName()) && gm.side, "mestre vendo o porao");
            assertTrue(gm.scene.getTokens().stream().anyMatch(t -> t.getName().equals("Lyra")), "mestre vê a Lyra la");
            assertEquals("Porão", gmC.getSideViewKey());
            gmC.sendGmView(null);
            waitFor(() -> "Taverna".equals(gm.scene.getName()) && !gm.side, "mestre de volta");

            // Ana volta pro grupo: a cena do porao (com a Lyra) volta pro mestre gravar
            gmC.sendSplitScene("ana", null, null);
            waitFor(() -> "Taverna".equals(ana.scene.getName()), "Ana de volta");
            waitFor(() -> gm.closed != null, "porao devolvido pro mestre");
            assertEquals("Porão", gm.closedKey);
            assertTrue(gm.closed.getTokens().stream().anyMatch(t -> t.getName().equals("Lyra")));
            assertTrue(server.splitState().isEmpty());

            // jogador nao separa ninguem
            betoC.sendSplitScene("ana", "Porão", scene("Porão", "x"));
            waitFor(() -> beto.chats.stream().anyMatch(c -> c.contains("Só o mestre separa")), "recusado");
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            betoC.disconnect();
            server.stop();
        }
    }

    @Test
    void secretZonesStayWithTheMasterAndPortalsCarryTheToken() throws Exception {
        GameServer server = new GameServer(0, new SceneModel());
        server.start();
        GmRecorder gm = new GmRecorder();
        GameServerSecurityTest.Recorder ana = new GameServerSecurityTest.Recorder();
        GameServerSecurityTest.Recorder beto = new GameServerSecurityTest.Recorder();
        GameClient gmC = new GameClient(gm, null, "gm");
        GameClient anaC = new GameClient(ana, null, "ana");
        GameClient betoC = new GameClient(beto, null, "beto");
        try {
            gmC.connect("localhost", server.getPort(), "Mestre", true);
            anaC.connect("localhost", server.getPort(), "Ana", false);
            betoC.connect("localhost", server.getPort(), "Beto", false);
            waitFor(() -> gm.scene != null && ana.scene != null && beto.scene != null, "entraram");

            SceneModel tavern = scene("Taverna", "Taverneiro");
            tavern.getGrid().setCellSize(50);
            com.vttcaseiro.model.MapZone pit = new com.vttcaseiro.model.MapZone(500, 0, 100, 100, com.vttcaseiro.model.MapZone.TRAP);
            pit.setName("Fosso");
            tavern.getZones().add(pit);
            tavern.getZones().add(new com.vttcaseiro.model.MapZone(0, 500, 100, 100, com.vttcaseiro.model.MapZone.TERRAIN));
            TokenModel lyra = new TokenModel("Lyra", null);
            lyra.setOwnerId("ana");
            tavern.addToken(lyra);
            gmC.sendSceneOpen(tavern, "Taverna");
            waitFor(() -> "Taverna".equals(ana.scene.getName()), "Ana na taverna");
            assertEquals(1, ana.scene.getZones().size(), "jogador recebe so o terreno, nao a armadilha");
            assertEquals(com.vttcaseiro.model.MapZone.TERRAIN, ana.scene.getZones().get(0).getKind());

            anaC.sendMoveToken(lyra.getId(), 525, 25);
            waitFor(() -> !gm.zoneEvents.isEmpty(), "mestre soube da armadilha");
            assertEquals("Fosso|Lyra|Ana|null", gm.zoneEvents.get(0));
            anaC.sendMoveToken(lyra.getId(), 0, 0);
            anaC.sendMoveToken(lyra.getId(), 525, 25);
            Thread.sleep(300);
            assertEquals(1, gm.zoneEvents.size(), "desarmou depois da primeira vez");

            // portal: Lyra vai pro porao com a Ana; sai da taverna do Beto
            SceneModel cellar = scene("Porão", "Rato gigante");
            TokenModel moving = NetCodec.deepCopy(lyra, TokenModel.class);
            moving.setX(100);
            moving.setY(100);
            gmC.sendSplitScene("ana", "Porão", cellar, moving);
            waitFor(() -> "Porão".equals(ana.scene.getName()), "Ana no porao");
            assertTrue(ana.scene.getTokens().stream().anyMatch(t -> t.getName().equals("Lyra") && t.getX() == 100), "Lyra chegou junto");
            waitFor(() -> server.splitState().containsKey("Porão"), "porao separado");
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            betoC.disconnect();
            server.stop();
        }
    }

    @Test
    void openingTheSideSceneForEveryoneJoinsTheGroup() throws Exception {
        GameServer server = new GameServer(0, new SceneModel());
        server.start();
        GmRecorder gm = new GmRecorder();
        GameServerSecurityTest.Recorder ana = new GameServerSecurityTest.Recorder();
        GameServerSecurityTest.Recorder beto = new GameServerSecurityTest.Recorder();
        GameClient gmC = new GameClient(gm, null, "gm");
        GameClient anaC = new GameClient(ana, null, "ana");
        GameClient betoC = new GameClient(beto, null, "beto");
        try {
            gmC.connect("localhost", server.getPort(), "Mestre", true);
            anaC.connect("localhost", server.getPort(), "Ana", false);
            betoC.connect("localhost", server.getPort(), "Beto", false);
            waitFor(() -> gm.scene != null && ana.scene != null && beto.scene != null, "entraram");
            gmC.sendSceneOpen(scene("Taverna", "Taverneiro"), "Taverna");
            gmC.sendSplitScene("ana", "Porão", scene("Porão", "Rato gigante"));
            waitFor(() -> "Porão".equals(ana.scene.getName()), "Ana no porao");
            anaC.sendAddToken(new TokenModel("Lyra", null));
            Thread.sleep(200);

            // mestre abre o porao pra todos: o grupo desce, com a Lyra que ja estava la
            gmC.sendSceneOpen(scene("Porão", "Rato gigante"), "Porão");
            waitFor(() -> "Porão".equals(beto.scene.getName()), "Beto desceu");
            assertTrue(beto.scene.getTokens().stream().anyMatch(t -> t.getName().equals("Lyra")), "a Lyra continua la");
            assertTrue(server.splitState().isEmpty(), "ninguém separado");
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            betoC.disconnect();
            server.stop();
        }
    }
}
