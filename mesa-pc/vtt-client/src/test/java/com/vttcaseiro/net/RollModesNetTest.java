package com.vttcaseiro.net;

import com.vttcaseiro.model.SceneModel;
import org.junit.jupiter.api.Test;

import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Rolagem as cegas (so o mestre ve o resultado) e mensagem "falando como" um personagem. */
class RollModesNetTest {

    private static void waitFor(BooleanSupplier c, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("Timeout: " + what);
            Thread.sleep(20);
        }
    }

    @Test
    void blindRollGoesOnlyToTheMaster() throws Exception {
        GameServer server = new GameServer(0, new SceneModel());
        server.start();
        GameServerSecurityTest.Recorder gm = new GameServerSecurityTest.Recorder();
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

            anaC.sendRoll("Ana", "Percepção", "1d20+3", "normal", 0, false, true);
            waitFor(() -> gm.rolls.stream().anyMatch(r -> r.contains("Percepção")), "mestre recebeu");
            waitFor(() -> ana.chats.stream().anyMatch(c -> c.contains("às cegas")), "Ana recebeu só o aviso");
            assertTrue(ana.rolls.isEmpty(), "Ana não vê o resultado");

            // falar como: so personagem dela, e todo mundo ve quem fala
            com.vttcaseiro.character.CharacterSheet lyra = new com.vttcaseiro.character.CharacterSheet("Lyra");
            lyra.setOwnerPlayerId("ana");
            anaC.sendCharacterSync(lyra);
            waitFor(() -> !beto.sheets.isEmpty(), "ficha da Lyra chegou");
            anaC.sendChatAs("Ana", "Lyra", "Eu vou na frente.");
            waitFor(() -> beto.chats.contains("Lyra (Ana): Eu vou na frente."), "fala da Lyra com o nome da Ana junto");
            anaC.sendChatAs("Ana", "Rei Goblin", "Ajoelhem!");
            waitFor(() -> beto.chats.contains("Ana: Ajoelhem!"), "nome que nao e dela: sai como Ana");
            gmC.sendChatAs("Mestre", "Bento, o taverneiro", "Mais uma rodada?");
            waitFor(() -> beto.chats.contains("Bento, o taverneiro: Mais uma rodada?"), "mestre fala como PdM");
            assertTrue(beto.rolls.isEmpty(), "Beto também não viu a rolagem às cegas");
        } finally {
            gmC.disconnect();
            anaC.disconnect();
            betoC.disconnect();
            server.stop();
        }
    }
}
