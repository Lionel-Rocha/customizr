package com.vttcaseiro.net;

import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import com.vttcaseiro.model.Wall;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** O servidor so manda pro jogador os tokens que os tokens dele enxergam. */
class ServerVisionNetTest {

    private static void waitFor(BooleanSupplier c, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("Timeout: " + what);
            Thread.sleep(20);
        }
    }

    @Test
    void monstroAtrasDaParedeNaoChegaAteAparecer() throws Exception {
        SceneModel scene = new SceneModel();
        // Sala dividida por uma parede em x=560, com uma porta no meio.
        scene.getWalls().add(new Wall(560, 0, 560, 420, false));
        Wall door = new Wall(560, 420, 560, 560, true);
        scene.getWalls().add(door);
        scene.getWalls().add(new Wall(560, 560, 560, 1400, false));
        scene.setVisionEnabled(true);
        TokenModel hero = new TokenModel("Elora", null);
        hero.setOwnerId("ana");
        hero.setX(140);
        hero.setY(420);
        TokenModel goblin = new TokenModel("Goblin", null);
        goblin.setX(840);
        goblin.setY(420);
        scene.addToken(hero);
        scene.addToken(goblin);

        GameServer server = new GameServer(0, scene);
        server.start();
        Priority3NetTest.Rec gm = new Priority3NetTest.Rec();
        Priority3NetTest.Rec ana = new Priority3NetTest.Rec();
        Priority3NetTest.Rec beto = new Priority3NetTest.Rec();
        GameClient gmC = new GameClient(gm, null, "gm");
        GameClient anaC = new GameClient(ana, null, "ana");
        GameClient betoC = new GameClient(beto, null, "beto");
        try {
            gmC.connect("localhost", server.getPort(), "Mestre", true);
            anaC.connect("localhost", server.getPort(), "Ana", false);
            betoC.connect("localhost", server.getPort(), "Beto", false);
            waitFor(() -> gm.scene != null && ana.scene != null && beto.scene != null, "conectados");
            assertEquals(2, gm.scene.getTokens().size(), "mestre recebe tudo");
            assertEquals(List.of("Elora"), ana.scene.getTokens().stream().map(TokenModel::getName).toList(),
                    "goblin atras da parede nem chega no computador da Ana");
            assertTrue(beto.scene.getTokens().isEmpty(), "sem token proprio, com visao ligada, nao ve nada");

            // Mestre abre a porta: o goblin entra na linha de visao da Ana.
            SceneModel walls = new SceneModel();
            walls.getWalls().addAll(scene.getWalls());
            walls.getWalls().get(1).setOpen(true);
            walls.setVisionEnabled(true);
            gmC.sendWalls(walls);
            waitFor(() -> ana.tokensAdded.stream().anyMatch(t -> t.getName().equals("Goblin")), "porta aberta: goblin aparece");

            // Goblin anda pra tras da parede: some da Ana.
            gmC.sendMoveToken(goblin.getId(), 840, 140);
            waitFor(() -> ana.removed.contains(goblin.getId()), "goblin saiu da visao");

            // Movimento de quem a Ana nao ve nao chega (nem a posicao).
            int before = ana.tokensAdded.size();
            gmC.sendMoveToken(goblin.getId(), 910, 140);
            Thread.sleep(300);
            assertEquals(before, ana.tokensAdded.size());

            // Visao desligada: todos veem tudo de novo.
            walls.setVisionEnabled(false);
            gmC.sendWalls(walls);
            waitFor(() -> beto.tokensAdded.size() >= 2, "visao desligada: Beto recebe os tokens");
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            betoC.disconnect();
            server.stop();
        }
    }
}
