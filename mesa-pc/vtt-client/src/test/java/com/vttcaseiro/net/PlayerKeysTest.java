package com.vttcaseiro.net;

import com.vttcaseiro.model.SceneModel;
import com.vttcaseiro.model.TokenModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Chave secreta por jogador: ninguem entra com o id de outro (o id e publico; a chave nao). */
class PlayerKeysTest {

    static final String ANA_KEY = "a".repeat(64);
    static final String BETO_KEY = "b".repeat(64);

    @Test
    void firstKeyWinsUntilTheGmForgetsIt(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("jogadores-chaves.json");
        PlayerKeys keys = new PlayerKeys(file);
        assertEquals(PlayerKeys.Result.REGISTERED, keys.check("ana-id", ANA_KEY, "Ana", false));
        assertEquals(PlayerKeys.Result.OK, keys.check("ana-id", ANA_KEY, "Ana", false));
        assertEquals(PlayerKeys.Result.MISMATCH, keys.check("ana-id", BETO_KEY, "Beto", false));
        assertEquals(PlayerKeys.Result.MISMATCH, keys.check("ana-id", null, "Beto", false), "sem chave nao passa por id protegido");
        assertEquals(PlayerKeys.Result.LEGACY, keys.check("velho-id", null, "Velho", false), "programa antigo: entra sem protecao");
        assertFalse(Files.readString(file).contains(ANA_KEY), "o arquivo guarda so a impressao, nunca a chave");

        PlayerKeys reloaded = new PlayerKeys(file);
        assertEquals(PlayerKeys.Result.MISMATCH, reloaded.check("ana-id", BETO_KEY, "Beto", false), "vale depois de reabrir");
        assertEquals("Ana", reloaded.list().get(0).name);
        assertNull(reloaded.list().get(0).hash, "a lista nao mostra a impressao");

        assertTrue(reloaded.forget("ana-id"));
        assertEquals(PlayerKeys.Result.REGISTERED, reloaded.check("ana-id", BETO_KEY, "Ana no PC novo", false));
        assertEquals(PlayerKeys.Result.OK, reloaded.check("ana-id", ANA_KEY, "Ana", true), "mestre (senha de mestre) troca a propria chave");
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
    void someoneWithAnotherPlayersIdIsTurnedAway() throws Exception {
        GameServer server = new GameServer(0, new SceneModel(), null, "senha", "mestre");
        server.start();
        GameServerSecurityTest.Recorder gm = new GameServerSecurityTest.Recorder(), ana = new GameServerSecurityTest.Recorder(),
                beto = new GameServerSecurityTest.Recorder();
        GameClient gmC = new GameClient(gm, null, "gm-id");
        GameClient anaC = new GameClient(ana, null, "ana-id");
        anaC.setPlayerSecret(ANA_KEY);
        GameClient betoC = new GameClient(beto, null, "ana-id"); // Beto copiou o id da Ana (aparece no dono dos tokens)
        betoC.setPlayerSecret(BETO_KEY);
        try {
            gmC.connect("localhost", server.getPort(), "Mestre", true, "senha", "mestre");
            anaC.connect("localhost", server.getPort(), "Ana", false, "senha", null);
            waitFor(() -> gm.scene != null && ana.scene != null, "mestre e Ana");
            TokenModel lyra = new TokenModel("Lyra", null);
            lyra.setOwnerId("ana-id");
            gmC.sendAddToken(lyra);
            waitFor(() -> ana.tokensAdded.size() == 1, "token da Ana");

            betoC.connect("localhost", server.getPort(), "Beto", false, "senha", null);
            waitFor(() -> beto.chats.stream().anyMatch(c -> c.contains("já entrou na mesa de outro computador")), "recusa do Beto");
            assertNull(beto.scene, "o Beto nem recebe a cena");
            assertEquals(2, server.getConnectedSessions().size(), "so o mestre e a Ana");

            // a Ana de verdade volta (mesma chave): entra normal
            anaC.disconnect();
            GameClient anaAgain = new GameClient(new GameServerSecurityTest.Recorder(), null, "ana-id");
            anaAgain.setPlayerSecret(ANA_KEY);
            anaAgain.connect("localhost", server.getPort(), "Ana", false, "senha", null);
            waitFor(() -> server.getConnectedSessions().size() == 2, "Ana de volta");
            anaAgain.disconnect();
        } finally {
            gmC.disconnect();
            betoC.disconnect();
            server.stop();
        }
    }
}
