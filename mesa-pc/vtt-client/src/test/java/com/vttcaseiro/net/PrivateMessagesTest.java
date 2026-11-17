package com.vttcaseiro.net;

import com.vttcaseiro.model.SceneModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Sussurros e rolagens escondidas: so quem deve ver recebe. */
class PrivateMessagesTest {

    static class Rec extends GameServerSecurityTest.Recorder {
        final List<String> whispers = new CopyOnWriteArrayList<>();
        final List<String> hiddenRolls = new CopyOnWriteArrayList<>();
        final List<String> publicRolls = new CopyOnWriteArrayList<>();

        @Override
        public void onWhisper(String player, String to, String text) {
            whispers.add(player + "->" + to + ": " + text);
        }

        @Override
        public void onRollResultFull(String player, String label, String detail, int total, Integer nat, boolean hidden) {
            (hidden ? hiddenRolls : publicRolls).add(player + "|" + label);
        }
    }

    private static void waitFor(BooleanSupplier cond, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < end) {
            if (cond.getAsBoolean()) return;
            Thread.sleep(20);
        }
        fail("Timeout esperando: " + what);
    }

    @Test
    void sussurrosERolagensEscondidas() throws Exception {
        GameServer server = new GameServer(0, new SceneModel());
        server.start();
        Rec gm = new Rec();
        Rec ana = new Rec();
        Rec beto = new Rec();
        GameClient gmC = new GameClient(gm, null, "gm");
        GameClient anaC = new GameClient(ana, null, "ana");
        GameClient betoC = new GameClient(beto, null, "beto");
        try {
            gmC.connect("localhost", server.getPort(), "Mestre", true);
            anaC.connect("localhost", server.getPort(), "Ana", false);
            betoC.connect("localhost", server.getPort(), "Beto", false);
            waitFor(() -> gm.scene != null && ana.scene != null && beto.scene != null, "conectados");

            // Ana -> Beto: Ana e Beto recebem; o mestre NAO.
            anaC.sendWhisper("Ana", "beto", "segredo");
            waitFor(() -> beto.whispers.size() == 1 && ana.whispers.size() == 1, "sussurro entregue");
            Thread.sleep(200);
            assertTrue(gm.whispers.isEmpty(), "sussurro entre jogadores nao vaza pro mestre");
            assertEquals("Ana->beto: segredo", beto.whispers.get(0));

            // Beto -> mestre: so os mestres (e Beto).
            betoC.sendWhisper("Beto", "mestre", "vou trair o grupo");
            waitFor(() -> gm.whispers.size() == 1, "sussurro pro mestre");
            Thread.sleep(200);
            assertEquals(1, ana.whispers.size(), "Ana nao ve o sussurro do Beto pro mestre");

            // Nome que nao existe: so quem mandou fica sabendo.
            anaC.sendWhisper("Ana", "Carlos", "oi?");
            waitFor(() -> ana.chats.stream().anyMatch(c -> c.contains("Ninguém conectado chamado")), "aviso");
            assertTrue(beto.chats.stream().noneMatch(c -> c.contains("Carlos")));

            // Rolagem escondida do mestre: so o mestre ve. Da Ana: Ana + mestre.
            gmC.sendRoll("Mestre", "Percepcao do goblin", "1d20+2", "normal", 0, true);
            anaC.sendRoll("Ana", "Furtividade", "1d20+5", "normal", 0, true);
            waitFor(() -> gm.hiddenRolls.size() == 2 && ana.hiddenRolls.size() == 1, "rolagens escondidas");
            Thread.sleep(200);
            assertTrue(beto.hiddenRolls.isEmpty() && beto.publicRolls.isEmpty(), "Beto nao ve nenhuma");
            assertEquals("Ana|Furtividade", ana.hiddenRolls.get(0));

            // Rolagem normal continua chegando pra todos.
            betoC.sendRoll("Beto", "Atletismo", "1d20", "normal", 0, false);
            waitFor(() -> gm.publicRolls.size() == 1 && ana.publicRolls.size() == 1 && beto.publicRolls.size() == 1, "publica");
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            betoC.disconnect();
            server.stop();
        }
    }
}
