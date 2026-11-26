package com.vttcaseiro.net;

import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.model.Wall;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Espectador: entra so pra assistir -- ve o que o grupo ve, conversa e aponta no mapa, nao mexe em nada. */
class SpectatorNetTest {

    private static void waitFor(BooleanSupplier c, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("Timeout: " + what);
            Thread.sleep(20);
        }
    }

    @Test
    void watchesWhatThePartySeesTalksButCannotTouch() throws Exception {
        SceneModel scene = new SceneModel();
        scene.getWalls().add(new Wall(560, 0, 560, 1400, false)); // parede: o goblin fica do outro lado
        scene.setVisionEnabled(true);
        TokenModel hero = new TokenModel("Elora", null);
        hero.setOwnerId("ana");
        hero.setX(140);
        hero.setY(420);
        TokenModel wolf = new TokenModel("Lobo", null); // do mesmo lado da Elora
        wolf.setX(280);
        wolf.setY(420);
        TokenModel goblin = new TokenModel("Goblin", null);
        goblin.setX(840);
        goblin.setY(420);
        scene.addToken(hero);
        scene.addToken(wolf);
        scene.addToken(goblin);

        GameServer server = new GameServer(0, scene, null, "senha", "mestre");
        server.start();
        Priority3NetTest.Rec gm = new Priority3NetTest.Rec();
        Priority3NetTest.Rec ana = new Priority3NetTest.Rec();
        Priority3NetTest.Rec ze = new Priority3NetTest.Rec();
        GameClient gmC = new GameClient(gm, null, "gm");
        GameClient anaC = new GameClient(ana, null, "ana");
        GameClient zeC = new GameClient(ze, null, "ze");
        zeC.setSpectator(true);
        try {
            gmC.connect("localhost", server.getPort(), "Mestre", true, "senha", "mestre");
            anaC.connect("localhost", server.getPort(), "Ana", false, "senha", null);
            // pedir mestre junto nao vale: espectador e espectador
            zeC.connect("localhost", server.getPort(), "Zé", true, "senha", "mestre");
            waitFor(() -> gm.scene != null && ana.scene != null && ze.scene != null, "conectados");

            assertTrue(zeC.getSession().spectator());
            assertFalse(zeC.getSession().gm());
            assertTrue(server.getConnectedSessions().stream().anyMatch(s -> s.playerName().equals("Zé") && s.spectator() && !s.gm()));
            assertEquals(List.of("Elora", "Lobo"), ze.scene.getTokens().stream().map(TokenModel::getName).sorted().toList(),
                    "ve pelos olhos do grupo (a Elora ve o lobo), nao o goblin atras da parede");

            // conversa (com o nome marcado), mas nao mexe
            zeC.sendChat("Zé", "que mapa bonito");
            waitFor(() -> gm.chats.stream().anyMatch(c -> c.contains("que mapa bonito")), "chat do espectador");
            assertTrue(gm.chats.stream().anyMatch(c -> c.startsWith("Zé (espectador)")), gm.chats.toString());

            zeC.sendMoveToken(hero.getId(), 200, 200);
            zeC.sendAddToken(new TokenModel("Intruso", null));
            waitFor(() -> ze.chats.stream().filter(c -> c.contains("Você está assistindo")).count() >= 2, "recusas");
            Thread.sleep(200);
            assertEquals(140, scene.findToken(hero.getId()).orElseThrow().getX(), 0.01,
                    "a Elora nao saiu do lugar");
            assertEquals(3, scene.getTokens().size(), "o intruso nao entrou");
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            zeC.disconnect();
            server.stop();
        }
    }

    @Test
    void sessionLabelsAndControl() {
        PlayerSession s = new PlayerSession("ze", "Zé", false, true);
        assertEquals("Zé (espectador)", s.displayName());
        assertFalse(s.owns("ze"), "nem o que tivesse o id dele");
        assertEquals("Ana", new PlayerSession("ana", "Ana", false).displayName());
    }
}
